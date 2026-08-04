/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.malbec.parts.display

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.SystemProperties
import android.provider.Settings
import android.util.Log
import org.pixelos.malbec.parts.Constants

/**
 * The two coherent panel + touch-controller states, and nothing in between.
 *
 * ── Why this exists ───────────────────────────────────────────────────────
 *
 * The panel refresh rate and the touch controller's report rate are not
 * independent knobs on this hardware. Measured on this unit:
 *
 *   panel 144 Hz  ->  the controller cannot detect the active pen AT ALL.
 *                     Not "no pressure" — zero events, and /proc/Pen_ID falls
 *                     back to 255;255.
 *   panel 120 Hz  ->  pen works fully (6746 events / 438 pressure samples over
 *                     23 strokes), finger reports at 120 Hz.
 *   HighReportRate 1 -> finger reports at 360 Hz (3x).
 *
 * Stock has exactly two states and switches between them from the framework
 * (ZUI's DisplayModeDirector patch, supportPen(true/false)):
 *
 *   supportPen(true):  render rate voted down to 120, HighReportRate 0
 *   supportPen(false): no vote (144), HighReportRate 1
 *
 * This tree has no such framework patch, and the owner explicitly rejected
 * automatic switching: every transition is a DSI mode change on the panel PLUS
 * a mode change in the touch controller, both visible and both costing power,
 * and it would fire every time the pen came into range. So: two named modes, a
 * user picks one, it stays.
 *
 * ── Why the property and not a direct write ───────────────────────────────
 *
 * /proc/HighReportRate and /proc/support_pen are labelled proc_lenovo_touch /
 * proc_lenovo_pen — vendor types. Granting a core domain write access to them
 * would cross the Treble boundary the rest of this tree's sepolicy is careful
 * about. init.malbec.rc does the write instead, triggered on this property.
 *
 * `persist.sys.` is already u:object_r:system_prop:s0
 * (system/sepolicy/private/property_contexts:79) and system_app already holds
 * set_prop for it (private/system_app.te:43), so this adds no policy at all.
 */
object PenModeController {

    private const val TAG = "${Constants.TAG}/PenMode"

    fun currentMode(context: Context): String =
        if (peakRefreshRate(context) > Constants.REFRESH_RATE_STYLUS + 0.5f) {
            Constants.TOUCH_MODE_GAME
        } else {
            Constants.TOUCH_MODE_STYLUS
        }

    fun isGameMode(context: Context) = currentMode(context) == Constants.TOUCH_MODE_GAME

    fun setMode(context: Context, mode: String) {
        val game = mode == Constants.TOUCH_MODE_GAME
        val rate = if (game) Constants.REFRESH_RATE_GAME else Constants.REFRESH_RATE_STYLUS

        // PEAK_REFRESH_RATE is the load-bearing one: it becomes
        // Vote.forPhysicalRefreshRates(0, peak) at
        // PRIORITY_USER_SETTING_PEAK_REFRESH_RATE
        // (DisplayModeDirector.java:1209-1215), i.e. a ceiling on the PHYSICAL
        // panel mode. MIN_REFRESH_RATE is deliberately left alone, so the idle
        // low-rate behaviour the panel already has is not disturbed.
        Settings.System.putFloat(context.contentResolver, PEAK_REFRESH_RATE, rate)

        // The observer below would do this too, but doing it here as well means
        // the touch controller changes in the same user action rather than one
        // content-observer hop later.
        applyReportRate(game)
        Log.i(TAG, "mode=$mode peak=$rate")
    }

    fun toggle(context: Context) {
        setMode(
            context,
            if (isGameMode(context)) Constants.TOUCH_MODE_STYLUS else Constants.TOUCH_MODE_GAME,
        )
    }

    private fun applyReportRate(game: Boolean) {
        SystemProperties.set(
            Constants.PROP_TOUCH_MODE,
            if (game) Constants.TOUCH_MODE_GAME else Constants.TOUCH_MODE_STYLUS,
        )
    }

    private fun peakRefreshRate(context: Context): Float =
        Settings.System.getFloat(
            context.contentResolver,
            PEAK_REFRESH_RATE,
            Constants.REFRESH_RATE_STYLUS,
        )

    /**
     * ★ The observer that makes the two knobs impossible to desynchronise.
     *
     * Without it the user can pick 144 Hz from Settings > Display > Refresh rate
     * — a list built at runtime from Display.getSupportedModes()
     * (RefreshRateUtils.java:45-49), which no RRO can filter — and the stylus
     * stops working with NO indication of why. That is the single worst failure
     * mode this feature can have, and it costs one callback to remove.
     *
     * This is not the "automatic switching" the owner rejected. Nothing here
     * watches the pen, samples anything, or decides on the user's behalf; it is
     * one callback on one settings write, and it only makes the second knob
     * follow the first. Zero cost when nothing changes.
     */
    class RefreshRateObserver(
        private val context: Context,
        handler: Handler,
    ) : ContentObserver(handler) {

        fun register() {
            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(PEAK_REFRESH_RATE),
                /* notifyForDescendants = */ false,
                this,
            )
            onChange(false)
        }

        fun unregister() {
            context.contentResolver.unregisterContentObserver(this)
        }

        override fun onChange(selfChange: Boolean) {
            val game = isGameMode(context)
            applyReportRate(game)
            PenModeTileService.requestUpdate(context)
            PenModeWidgetProvider.requestUpdate(context)
        }
    }

    /**
     * Settings.System.PEAK_REFRESH_RATE is @hide, and its value is the literal
     * "peak_refresh_rate". Spelling it out rather than reaching for the hidden
     * constant keeps this compiling if the constant ever moves.
     */
    private const val PEAK_REFRESH_RATE = "peak_refresh_rate"
}
