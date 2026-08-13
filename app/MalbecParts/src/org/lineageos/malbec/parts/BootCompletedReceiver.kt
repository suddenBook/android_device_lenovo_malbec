/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.malbec.parts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.input.InputSettings
import android.os.Vibrator
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

        materialiseVolumeHushDefault(context)

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

    /**
     * ★ Point the power-button hush shortcut at Mute, because this tablet has no
     * vibrator and upstream never checks.
     *
     * Settings > Sound reads "Shortcut to prevent ringing — **Vibrate**" out of
     * the box, and `dumpsys vibrator_manager` on this device reads
     * **"No vibrator found"**. So the one gesture whose entire purpose is to
     * silence the device is pointed at a feedback channel the hardware does not
     * have.
     *
     * ⚠️ It is not merely unset. `settings get secure volume_hush_gesture`
     * returned **1** on a freshly provisioned device, and unset would read the
     * same way regardless: `PreventRingingGesturePreferenceController.java:115`
     * passes `VOLUME_HUSH_VIBRATE` as its own *default argument*, so absence and
     * Vibrate are indistinguishable. That controller contains no `hasVibrator()`
     * call at all — checked, the whole file.
     *
     * AOSP does gate the volume paths on the same fact
     * (`RingerModeAffectedVolumePreferenceController:56`,
     * `NotificationVolumePreference.kt:192` both map VIBRATE to SILENT when there
     * is no vibrator); the gesture path was simply missed. That makes it an
     * upstream defect on every vibrator-less device, not something this port
     * caused — worth sending to AOSP, and worth not shipping meanwhile.
     *
     * ⚠️ There is no `def_` resource for it, so `SettingsProviderOverlayMalbec`
     * cannot reach this one the way it reaches `def_user_rotation`. A grep of
     * `packages/SettingsProvider/res/values/defaults.xml` for "hush" is empty.
     * Seeding here is the only mechanism the device tree has, which is why this
     * sits next to the touchpad seed rather than in an overlay.
     *
     * Written only when the key is absent, so the moment the owner touches the
     * control it owns the value; a `--wipe` clears it and the next boot restores
     * it. Same contract as [materialiseTouchpadScrollDefault].
     *
     * The constants are `Settings.Secure.VOLUME_HUSH_{OFF,VIBRATE,MUTE}` = 0/1/2
     * (`Settings.java:12645,12648,12651`) and the key is @hide, so both are
     * spelled out here for the reason [PenModeController] gives for the refresh
     * rate keys.
     */
    private fun materialiseVolumeHushDefault(context: Context) {
        if (Settings.Secure.getString(context.contentResolver, VOLUME_HUSH_GESTURE) != null) {
            return
        }
        val vibrator = context.getSystemService(Vibrator::class.java)
        if (vibrator?.hasVibrator() == true) {
            // Defensive rather than expected: if a future variant of this chassis
            // does have a motor, upstream's default is the right one and this
            // should stay out of the way.
            return
        }
        Log.i(Constants.TAG, "seeding $VOLUME_HUSH_GESTURE=$VOLUME_HUSH_MUTE (no vibrator)")
        Settings.Secure.putInt(context.contentResolver, VOLUME_HUSH_GESTURE, VOLUME_HUSH_MUTE)
    }

    private companion object {
        const val VOLUME_HUSH_GESTURE = "volume_hush_gesture"
        const val VOLUME_HUSH_MUTE = 2
    }
}
