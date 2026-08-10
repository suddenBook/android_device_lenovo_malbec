/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.malbec.parts.display

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.SystemProperties
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import org.lineageos.malbec.parts.Constants
import org.lineageos.malbec.parts.R
import org.lineageos.malbec.parts.gesture.GestureBinder

/**
 * The device's two modes: Daily and Game.
 *
 * ── What the hardware actually does ───────────────────────────────────────
 *
 * The panel rate and the touch controller's scan mode are one knob with a small
 * number of coherent settings. All of this is measured on this unit, 8 s per
 * window, with the panel rate read back inside every window
 * (work/scripts/61-touch-rate-at-144.sh, raw evdev in work/s18/touch-rate/):
 *
 *   panel  HighReportRate   finger      pen
 *   120    0                120 Hz      240 Hz   (Pen_ID 2;87 — model 2, 87 %)
 *   120    1                360 Hz      none
 *   144    0                185 Hz      none
 *   144    1                185 Hz      none
 *   144    1 + pen on       112 Hz      none     <- worst state on the device
 *
 * Three things follow, and two of them overturn what this project believed:
 *
 *  1. The maximum finger report rate is ~360 Hz and it is ONLY available at
 *     120 Hz. Above 120 the controller caps at ~185 Hz and HighReportRate stops
 *     having any effect at all. So "the fastest touch" and "144 Hz" are mutually
 *     exclusive, in the opposite direction from the obvious assumption — and
 *     144 Hz is what stock's own no-pen state uses, so stock never reaches
 *     360 Hz either.
 *  2. HighReportRate is a BOOLEAN and only the literal value 1 arms it. At
 *     120 Hz, HRR=4 measured 120.5 Hz — indistinguishable from HRR=0. The driver
 *     passes the byte through unclamped (nvt_high_report_rate_set @0x946c →
 *     {0x76, v}, one hex digit accepted) but the firmware only recognises 1.
 *     There is nothing above 1 to find.
 *  3. Since every rate above 120 is strictly worse here, AOSP's refresh-rate
 *     picker is switched off — see overlay/SettingsOverlayMalbec — because
 *     RefreshRateUtils.getRefreshRates() builds its list at runtime from
 *     Display.getSupportedModes(), so no RRO can take 144 out of it. This app
 *     offers 60 / 90 / 120 instead: the same power-saving choice without the
 *     trap. 120 is therefore a CEILING both modes share, not a setpoint, and the
 *     modes differ only in the other three knobs.
 *
 * ── The two modes ────────────────────────────────────────────────────────
 *
 *   DAILY: ceiling <= 120 · support_pen 1 · HighReportRate 0 · thermal normal
 *   GAME:  ceiling <= 120 · support_pen 0 · HighReportRate 1 · thermal game
 *
 * The owner rejected automatic switching: every transition is a mode change in
 * the touch controller, and it would fire every time the pen came into range.
 * Two named modes, the user picks one, it stays.
 *
 * ── Where each half is applied ───────────────────────────────────────────
 *
 * This class owns only the refresh rate and the mode property. The /proc writes
 * and the thermal policy are applied by rootdir/etc/init.malbec.rc off that same
 * property, because /proc/HighReportRate and /proc/support_pen are labelled
 * proc_lenovo_touch / proc_lenovo_pen and vendor.thermal.mode is
 * vendor_thermal_prop — all vendor types, which a core domain must not write.
 * init already has the rules for them.
 *
 * `persist.sys.` is already u:object_r:system_prop:s0
 * (system/sepolicy/private/property_contexts:79) and system_app already holds
 * set_prop for it (private/system_app.te:43), so this adds no policy at all.
 */
object PenModeController {

    private const val TAG = "${Constants.TAG}/PenMode"

    /**
     * Settings.System.PEAK_REFRESH_RATE / MIN_REFRESH_RATE are @hide. Spelling
     * the values out rather than reaching for the hidden constants keeps this
     * compiling if the constants ever move.
     */
    private const val PEAK_REFRESH_RATE = "peak_refresh_rate"
    private const val MIN_REFRESH_RATE = "min_refresh_rate"

    /**
     * The mode this process last told the user about; null until the first sync.
     *
     * Every writer runs on this app's main thread — the QS tile (TileService is
     * in-process, main thread), the widget receiver, the settings fragment, the
     * boot receiver, and the observer below (constructed with a main-looper
     * Handler in MalbecPartsService). So this needs no synchronisation, and
     * single-threaded-by-construction is cheaper to reason about than @Volatile
     * plus hope.
     */
    private var announcedMode: String? = null

    /**
     * ★ The mode is the PROPERTY, not the refresh rate.
     *
     * It used to be derived from peak_refresh_rate, which worked only while the
     * two modes had different rates. They do not any more — both are 120 — so the
     * choice has to be stored. The property is the right place for it rather than
     * SharedPreferences: init.malbec.rc already reads it, it is `persist.` so it
     * survives a reboot, and having exactly one source of truth is what stops the
     * UI and the hardware disagreeing.
     */
    fun currentMode(context: Context): String =
        if (SystemProperties.get(Constants.PROP_TOUCH_MODE, Constants.TOUCH_MODE_DAILY)
            == Constants.TOUCH_MODE_GAME
        ) {
            Constants.TOUCH_MODE_GAME
        } else {
            Constants.TOUCH_MODE_DAILY
        }

    fun isGameMode(context: Context) = currentMode(context) == Constants.TOUCH_MODE_GAME

    /**
     * ★ The effective ceiling is max(min, peak), not peak.
     *
     * This mirrors the framework exactly. DisplayModeDirector's
     * updateRefreshRateSettingLocked (:1209-1215, :1219-1221) builds
     *     Vote.forPhysicalRefreshRates(0, Math.max(min, peak))
     *     Vote.forRenderFrameRates(min, POSITIVE_INFINITY)
     * so a MIN_REFRESH_RATE above our peak keeps the panel up there regardless of
     * what we wrote to peak. Reading only peak was a real desync: with the picker
     * still present, choosing 144 with VRR off wrote BOTH knobs
     * (RefreshRateUtils.setCurrentRefreshRate:104-107), and then selecting Daily
     * here left the panel at 144 while every surface said "120 Hz, stylus on".
     *
     * Infinity is a real value here, not paranoia:
     * PeakRefreshRatePreferenceController:118-120 writes Float.POSITIVE_INFINITY
     * when back_up_smooth_display_and_force_peak_refresh_rate is set, and that
     * flag is enabled on this build. DisplayModeDirector:1186-1188 resolves it to
     * the highest supported rate, i.e. 144.
     */
    private fun effectiveRefreshRate(context: Context): Float {
        val cr = context.contentResolver
        val peak = Settings.System.getFloat(cr, PEAK_REFRESH_RATE, Constants.REFRESH_RATE_MAX)
        val min = Settings.System.getFloat(cr, MIN_REFRESH_RATE, 0f)
        return maxOf(peak, min)
    }

    /** True when something has pushed the panel above the rate the pen needs. */
    private fun aboveStylusLimit(context: Context) =
        effectiveRefreshRate(context) > Constants.REFRESH_RATE_MAX + 0.5f

    /**
     * The ceiling the user chose, from our own preferences.
     *
     * ★ Our SharedPreferences are the source, not a cache — the same rule
     * GestureBinder states for the key bindings, and for the same reason. If
     * peak_refresh_rate were the store, then a backup restore, a `settings put`,
     * or the POSITIVE_INFINITY that PeakRefreshRatePreferenceController can still
     * write would not merely be overridden, it would DELETE the choice: the clamp
     * would land on 120 and a user who had picked 60 for battery would silently be
     * back at 120 with the row agreeing. Keeping the intent separate from the
     * mechanism is what lets enforceCeiling() restore the right value rather than
     * the safe one.
     *
     * Validated against the list rather than parsed loosely, so an out-of-range
     * value left by an older build cannot select a row that no longer exists.
     */
    fun currentRefreshRate(context: Context): Float =
        GestureBinder.prefs(context)
            .getString(Constants.PREF_REFRESH_RATE, null)
            ?.toFloatOrNull()
            ?.takeIf { it in Constants.REFRESH_RATE_CHOICES }
            ?: Constants.REFRESH_RATE_MAX

    /**
     * The user picked a ceiling in our own page.
     *
     * No toast. The owner asked for one on every MODE switch, and this is not one
     * — the ListPreference summary already shows the new value, and a toast for a
     * row that visibly updated itself is noise. The toast that does exist on this
     * path is in onExternalRefreshRateChange, and it fires only when something
     * took the panel somewhere the pen cannot follow.
     *
     * The value is applied here and PERSISTED by the ListPreference itself when
     * the change listener returns true. Applying the argument rather than
     * re-reading the preference is deliberate: onPreferenceChange runs before the
     * write lands, so currentRefreshRate() would still report the old value.
     */
    fun setRefreshRate(context: Context, rate: Float) {
        val ceiling = rate.coerceAtMost(Constants.REFRESH_RATE_MAX)
        applyCeiling(context, ceiling)
        Log.i(TAG, "user selected refresh rate ceiling=$ceiling")
    }

    /**
     * The user picked a mode. Always announces.
     *
     * announcedMode is set BEFORE the settings writes on purpose: those writes
     * wake RefreshRateObserver, and having already recorded the destination is
     * what stops it announcing the same transition a second time.
     */
    fun setMode(context: Context, mode: String) {
        val game = mode == Constants.TOUCH_MODE_GAME
        announcedMode = mode

        enforceCeiling(context)
        publish(context, mode)
        announce(context, if (game) R.string.touch_mode_game_toast else R.string.touch_mode_daily_toast)
        refreshSurfaces(context)
        Log.i(TAG, "user selected mode=$mode")
    }

    fun toggle(context: Context) {
        setMode(
            context,
            if (isGameMode(context)) Constants.TOUCH_MODE_DAILY else Constants.TOUCH_MODE_GAME,
        )
    }

    /**
     * Put the chosen ceiling back into Settings.System, wherever it drifted from.
     *
     * ⚠️ This used to force peak to exactly 120 on any mismatch, which was right
     * only while 120 was the single legal value. It is not any more: the page now
     * offers 60 / 90 / 120, and a hard 120 here would have undone the owner's
     * choice on the next boot, the next mode switch and every process restart —
     * silently, because reassert() takes this path and is not allowed to toast.
     *
     * The shapes this has to survive, all reachable with no UI at all:
     *
     *   * above 120 — a stale 144 from a backup restore, or `settings put`.
     *   * POSITIVE_INFINITY — PeakRefreshRatePreferenceController:118-120 writes
     *     it when back_up_smooth_display_and_force_peak_refresh_rate is set, and
     *     that flag is enabled on this build. DisplayModeDirector:1186-1188
     *     resolves it to the highest supported rate, i.e. 144.
     *   * zero or negative — DisplayModeDirector:1197-1200 treats peak == 0 as
     *     "post no peak vote at all", which UNCAPS the panel; with
     *     config_defaultRefreshRate also 0 it additionally hits AOSP's own
     *     "both are 0" error branch at :1231-1237. A restore can produce it.
     *   * below the choice — nothing legitimate writes this today, but restoring
     *     upward is the same operation and costs nothing to support.
     */
    private fun enforceCeiling(context: Context) =
        applyCeiling(context, currentRefreshRate(context))

    private fun applyCeiling(context: Context, ceiling: Float) {
        val cr = context.contentResolver
        // Written whenever it differs, including when the stored value is lower
        // than what is live — that is the restore path, not just a clamp.
        // NaN-safe by construction: `!(a == b)` and `!(a <= b)` are true for NaN,
        // where `a != b` reads the same but `a > b` would not.
        if (!(Settings.System.getFloat(cr, PEAK_REFRESH_RATE, -1f) == ceiling)) {
            Settings.System.putFloat(cr, PEAK_REFRESH_RATE, ceiling)
        }
        // MIN is clamped to the same ceiling and never raised. Leaving a min of 0
        // alone is what preserves AOSP's idle drop to 30 Hz, which is worth real
        // standby power; raising it would pin the panel and cost that.
        if (!(Settings.System.getFloat(cr, MIN_REFRESH_RATE, 0f) <= ceiling)) {
            Settings.System.putFloat(cr, MIN_REFRESH_RATE, ceiling)
        }
    }

    /**
     * Exactly the condition applyCeiling would act on — nothing more.
     *
     * Kept as a mirror of applyCeiling's two tests, including the `!(a == b)` /
     * `!(a <= b)` spelling, so the pair cannot drift apart. That equivalence is
     * also what makes the observer terminate: applyCeiling leaves peak == ceiling
     * and min <= ceiling, so the write it performs wakes the observer once and
     * the next pass reads false here and returns.
     */
    private fun needsCeiling(context: Context, ceiling: Float): Boolean {
        val cr = context.contentResolver
        return !(Settings.System.getFloat(cr, PEAK_REFRESH_RATE, -1f) == ceiling) ||
            !(Settings.System.getFloat(cr, MIN_REFRESH_RATE, 0f) <= ceiling)
    }

    /**
     * Re-assert the mode on the hardware without claiming the user just chose it.
     *
     * Called at boot and whenever this process starts. It writes the property
     * even when unchanged, which is deliberate: init notifies on every set, not
     * only on a change (system/core/init/property_service.cpp:427).
     *
     * ⚠️ The rest of that sentence used to read "…so this is what makes the /proc
     * writes and the thermal policy happen on a fresh boot where the property
     * already held the right value." Measured in session 24, that is no longer
     * true of a cold boot, and has not been since init.malbec.rc grew its
     * `on property:sys.boot_completed=1` blocks:
     *
     *   2.876 s  load_persist_props sets persist.sys.malbec.touch_mode, the bare
     *            trigger fires, `setprop vendor.thermal.mode <mode>`
     *   3.935 s  thermal-switch-engine starts with the right conf
     *  11.770 s  boot_completed blocks re-write /proc/support_pen and
     *            /proc/HighReportRate (both modes are covered — the stylus block
     *            first, the game block after it, in file order)
     *  12.270 s  this process starts, reassert() -> publish()
     *  12.400 s  init.qcom.rc:523-526 `on property:vendor.thermal.mode=*` does
     *            stop/stop/start, so thermal-engine-v2 is restarted for nothing
     *
     * So on a cold boot this call costs one redundant thermal-engine restart and
     * buys nothing. It is kept anyway, and the reason is the OTHER thing the rc
     * file documents: nvt_touch.ko's Boot_Update_Firmware delayed work lands at
     * ≈7.3 s and resets the controller, and nothing guarantees that is the last
     * time something outside this app moves those nodes. Re-asserting a known
     * state when the process starts is worth 130 ms once per boot.
     *
     * If that trade is ever re-decided, the change is to skip publish() when
     * SystemProperties.get(PROP_TOUCH_MODE) already equals `mode` — but note
     * that also gives up the /proc re-assertion, which is the half with value.
     */
    fun reassert(context: Context) {
        val mode = currentMode(context)
        announcedMode = mode
        enforceCeiling(context)
        publish(context, mode)
        refreshSurfaces(context)
    }

    /**
     * Something outside this app moved the refresh rate.
     *
     * With AOSP's picker switched off the remaining routes are `settings put`, a
     * backup restore, and the POSITIVE_INFINITY that
     * PeakRefreshRatePreferenceController can still write. None of them can be
     * left alone, and they split into two cases that deserve different answers:
     *
     *   * ABOVE 120 — the pen stops working and the finger rate halves, silently,
     *     with nothing on screen to say so. Say so, then put it back.
     *   * anything else that is not the chosen ceiling — put it back without a
     *     word. Nothing user-visible broke; a toast here would fire on ordinary
     *     restores for no reason.
     *
     * ⚠️ The early return compares against the CHOSEN ceiling, not against 120.
     * Comparing against 120 was the obvious version and it leaves a hole: with a
     * 60 Hz choice, an external write of 90 is below the stylus limit, so it
     * would be accepted in silence while the picker went on showing 60.
     *
     * ★ ⚠️ SESSION 21 — the guard did not match the contract two paragraphs up,
     * and the case it missed is the dangerous one. It read
     *
     *     if (effectiveRefreshRate(context) <= ceiling + 0.5f) return
     *
     * which restores only what is ABOVE the ceiling, while the documented rule is
     * "anything else that is not the chosen ceiling — put it back". The gap is
     * peak_refresh_rate = 0:
     *
     *   · effectiveRefreshRate() is max(peak, min), so a stored 0 makes it 0 —
     *     the REFRESH_RATE_MAX default only applies when the key is ABSENT, not
     *     when it is present and zero;
     *   · 0 <= 120.5, so the observer returned and did nothing;
     *   · and DisplayModeDirector.java:1202-1206 posts NO physical vote at all
     *     for peak == 0, which does not mean "60" — it means the panel is
     *     uncapped and free to run 144.
     *
     * So the one write that silently kills the stylus was the one write this
     * function ignored. enforceCeiling() already handled it (its own KDoc lists
     * "zero or negative" explicitly) but that only runs at boot and on a mode
     * switch, so the panel stayed uncapped until the next reboot.
     *
     * The guard is now needsCeiling(), which is applyCeiling's own condition. It
     * still cannot loop, and the reason is unchanged — see needsCeiling.
     */
    private fun onExternalRefreshRateChange(context: Context) {
        val ceiling = currentRefreshRate(context)
        if (!needsCeiling(context, ceiling)) return
        if (aboveStylusLimit(context)) {
            Log.w(TAG, "refresh rate pushed to ${effectiveRefreshRate(context)}; restoring $ceiling")
            announce(context, R.string.touch_mode_rate_restored_toast)
        } else {
            Log.i(TAG, "refresh rate drifted to ${effectiveRefreshRate(context)}; restoring $ceiling")
        }
        applyCeiling(context, ceiling)
    }

    /** Hand the mode to init, which owns the /proc writes and the thermal policy. */
    private fun publish(context: Context, mode: String) {
        // SystemProperties.set throws (android_os_SystemProperties.cpp:183-198)
        // rather than returning a failure. Nothing here is worth taking the
        // process down for, and this runs from Service.onCreate on every boot.
        runCatching { SystemProperties.set(Constants.PROP_TOUCH_MODE, mode) }
            .onFailure { Log.e(TAG, "could not publish ${Constants.PROP_TOUCH_MODE}=$mode", it) }
    }

    /**
     * One toast per real transition, from one place.
     *
     * The owner asked for a toast on every mode switch. Routing every entry point
     * — tile, widget, the list in our own settings page, and the external-change
     * path above — through this single funnel is what makes "every switch" true
     * without any of them double-announcing.
     *
     * applicationContext, because the caller may be a broadcast receiver whose
     * context is gone by the time the toast is shown. A system-uid package is
     * exempt from the background-toast restriction, so this works from the widget
     * receiver with nothing of ours in the foreground.
     */
    private fun announce(context: Context, resId: Int) {
        Toast.makeText(
            context.applicationContext, context.getString(resId), Toast.LENGTH_LONG
        ).show()
    }

    private fun refreshSurfaces(context: Context) {
        PenModeTileService.requestUpdate(context)
        PenModeWidgetProvider.requestUpdate(context)
    }

    /**
     * ★ The observer that makes the knobs impossible to desynchronise.
     *
     * This is not the "automatic switching" the owner rejected. Nothing here
     * watches the pen, samples anything, or decides which mode to be in; it only
     * defends the one refresh rate both modes depend on. Zero cost when nothing
     * changes.
     *
     * ⚠️ BOTH uris. Registering only peak_refresh_rate misses a min-only change,
     * and min is the half that can hold the panel above the ceiling —
     * DisplayModeDirector.java:955-957 registers both for the same reason.
     */
    class RefreshRateObserver(
        private val context: Context,
        handler: Handler,
    ) : ContentObserver(handler) {

        fun register() {
            val cr = context.contentResolver
            for (key in arrayOf(PEAK_REFRESH_RATE, MIN_REFRESH_RATE)) {
                cr.registerContentObserver(
                    Settings.System.getUriFor(key),
                    /* notifyForDescendants = */ false,
                    this,
                )
            }
            // Establish announcedMode and bring the hardware into line, silently:
            // the process may have just been (re)started, which is not a user
            // action and must not produce a toast.
            reassert(context)
        }

        fun unregister() {
            context.contentResolver.unregisterContentObserver(this)
        }

        override fun onChange(selfChange: Boolean) {
            onExternalRefreshRateChange(context)
        }
    }
}
