/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.malbec.parts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.pixelos.malbec.parts.display.PenModeController

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i(Constants.TAG, "boot completed")

        // Restate the touch mode. init.malbec.rc applies the persisted property
        // on its own, but the panel side lives in Settings.System and the two
        // must agree — if a previous boot was interrupted between the two
        // writes, this is where it gets fixed.
        PenModeController.setMode(context, PenModeController.currentMode(context))

        // The service reconciles the gesture table itself in onCreate.
        MalbecPartsService.sync(context)
    }
}
