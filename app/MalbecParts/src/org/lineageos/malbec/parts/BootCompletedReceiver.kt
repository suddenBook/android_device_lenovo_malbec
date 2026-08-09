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

        seedFetchedPif(context)
    }

    /**
     * Work around an upstream NPE that never repairs itself.
     *
     * AttestationService.FetchGmsCertifiedProps runs every five minutes and does:
     *
     *     String savedProps = Settings.Secure.getString(cr, Settings.Secure.FETCHED_PIF);
     *     String props = fetchProps();
     *     if (props != null && !savedProps.equals(props)) {      // <- :136
     *         Settings.Secure.putString(cr, Settings.Secure.FETCHED_PIF, props);
     *     }
     *
     * On a device that has never stored the key, savedProps is null, so :136
     * throws NullPointerException. The catch logs it, the putString never runs,
     * savedProps stays null — and it throws again five minutes later, forever.
     * Measured on this device: one stack trace every 300 s, indefinitely.
     *
     * Consequence beyond the log noise: PropImitationHooks.java:198-204 prefers
     * PIF_DATA, then FETCHED_PIF, and only falls back to the static
     * R.array.config_certifiedBuildProperties when both are empty. So Play
     * Integrity keeps working off the built-in list, but the *fetched* list can
     * never arrive, and the built-in one goes stale as Google rotates devices.
     *
     * ⚠️ This does NOT patch frameworks/base — the owner's rule is that upstream
     * problems are not fixed in the tree. Seeding the key with an empty string is
     * enough: "" is non-null, so :136 compares instead of throwing, the real
     * value gets stored on the next run, and this code never matters again.
     *
     * Only ever writes when the value is absent, so a real fetched payload — or
     * a user-provided PIF_DATA — is never touched. Delete this when upstream
     * uses Objects.equals or a null guard at AttestationService.java:136.
     */
    private fun seedFetchedPif(context: Context) {
        try {
            val cr = context.contentResolver
            if (android.provider.Settings.Secure.getString(
                    cr, android.provider.Settings.Secure.FETCHED_PIF
                ) != null
            ) {
                return
            }
            android.provider.Settings.Secure.putString(
                cr, android.provider.Settings.Secure.FETCHED_PIF, ""
            )
            Log.i(Constants.TAG, "seeded empty fetched_pif (AttestationService.java:136 NPE guard)")
        } catch (e: Exception) {
            Log.w(Constants.TAG, "could not seed fetched_pif", e)
        }
    }
}
