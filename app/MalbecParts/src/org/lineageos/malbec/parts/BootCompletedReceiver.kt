/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.malbec.parts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

}
