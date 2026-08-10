/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.malbec.parts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.input.InputSettings
import android.provider.Settings
import android.util.Log
import org.lineageos.malbec.parts.display.PenModeController

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // MY_PACKAGE_REPLACED as well as BOOT_COMPLETED: an OTA that swaps this
        // APK drops the private gesture handler registrations with the process,
        // and re-registering here beats waiting for the next reboot.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        Log.i(Constants.TAG, "starting up: ${intent.action}")

        // Restate the mode. init.malbec.rc applies the persisted property on its
        // own, but the panel side lives in Settings.System and the two must agree
        // — if a previous boot was interrupted between the two writes, or if
        // something left peak_refresh_rate above 120, this is where it is fixed.
        //
        // reassert() and NOT setMode(): setMode always toasts, because it means
        // "the user just chose this". Greeting the owner with a mode toast on
        // every boot would be wrong, and it is the kind of wrong that only shows
        // up on real hardware.
        PenModeController.reassert(context)

        materialiseTouchpadScrollDefault(context)

        // The service reconciles the gesture table itself in onCreate.
        MalbecPartsService.sync(context)

        // ⚠️ seedFetchedPif() used to be called here and is GONE.
        //
        // It worked around an upstream NPE in PixelOS's AttestationService,
        // which re-threw every 300 s forever: Settings.Secure.FETCHED_PIF
        // started out null, and the catch swallowed the throw before the
        // putString that would have repaired it could run.
        //
        // None of that exists on LineageOS. Settings.Secure.FETCHED_PIF is a
        // PixelOS-only framework addition — a tree-wide search finds the
        // identifier in exactly one place, this file — and neither
        // AttestationService nor PropImitationHooks is in this tree at all.
        // The build says so plainly:
        //
        //     BootCompletedReceiver.kt:39:58: error: unresolved reference 'FETCHED_PIF'
        //
        // Kept as a note rather than deleted silently: it was the ONLY place
        // this port reached into a framework API LineageOS does not have, so if
        // Play Integrity behaviour is ever compared between the two ROMs, this
        // is the difference.
    }

    /**
     * ★ Write the touchpad's scroll direction down, because two readers disagree
     * about what it is when nobody has written it.
     *
     * `Settings.System.touchpad_natural_scrolling` ships unset. Read it through
     * the framework and the answer is ON — `InputSettings.useTouchpadNaturalScrolling`
     * (`InputSettings.java:335-338`) passes a default of **1**, and that reaches
     * the gestures library as `Invert Scrolling`. Measured on this tablet with
     * the folio attached: `dumpsys input` -> `Invert Scrolling (boolean): [true]`
     * while `settings get system touchpad_natural_scrolling` -> `null`.
     *
     * Read the same key through Launcher3 and the answer is OFF.
     * `SettingsCache.updateValue` (`SettingsCache.java:165-181`) defaults a key to
     * **0** unless it is in the `SETTINGS_ENABLED_BY_DEFAULT` multibinding, and
     * this one is not — `SettingsModule.kt` contributes three URIs (taskbar,
     * nav-bar hint, notification badging) and quickstep's `Modules.kt` one more,
     * none of them this. So `Launcher.isNaturalScrollingEnabled()` returns false,
     * `AbstractStateChangeTouchController:99` sets `mIsTrackpadReverseScroll`,
     * and `:264-268` inverts the drag — but only `mStartState == NORMAL`, i.e.
     * only the gesture that OPENS the app list.
     *
     * The symptom is exactly that asymmetry, and it is what the owner reported:
     * two fingers DOWN opens the app list, and two fingers DOWN closes it again.
     * One direction, both ways, on a device where the touchscreen equivalent is
     * swipe up to open and swipe down to close.
     *
     * ⚠️ This is not a value being changed — the input stack already behaves as
     * `1`. It is an implicit default being made explicit, which is the only
     * thing the two readers actually disagree about. Written once, and only when
     * the key is absent, so Settings > Touchpad > "Reverse scrolling" keeps
     * ownership the moment the owner touches it. A `--wipe` clears it and the
     * next boot puts it back.
     *
     * Reading with [InputSettings.useTouchpadNaturalScrolling] rather than a
     * literal is the point: the framework's own accessor owns the default, so
     * this cannot drift away from it.
     */
    private fun materialiseTouchpadScrollDefault(context: Context) {
        if (Settings.System.getString(
                context.contentResolver, Settings.System.TOUCHPAD_NATURAL_SCROLLING
            ) != null
        ) {
            return
        }
        val effective = InputSettings.useTouchpadNaturalScrolling(context)
        Log.i(Constants.TAG, "seeding touchpad_natural_scrolling=$effective (was unset)")
        InputSettings.setTouchpadNaturalScrolling(context, effective)
    }
}
