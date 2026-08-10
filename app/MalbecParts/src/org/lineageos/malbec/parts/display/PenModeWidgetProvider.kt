/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.malbec.parts.display

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.lineageos.malbec.parts.MalbecPartsService
import org.lineageos.malbec.parts.R

/**
 * The home-screen half of the Daily/Game switch.
 *
 * Requested by the owner specifically: one tap from the desktop.
 *
 * ⚠️ It is NOT pre-placed, and cannot be. This used to say "one instance already
 * on the default workspace so it is there the first time the tablet boots after
 * a flash", placed by MalbecParts' own AutoInstallsLayout resource. Session 24
 * made the home screen empty at the owner's request and removed that mechanism
 * entirely (OPEN-ISSUES.md #32a) — the empty screen now comes from
 * overlay/LauncherOverlayMalbec, which overrides Launcher3's internal default.
 *
 * The feature is not weaker for it: PenModeTileService puts the same switch in
 * quick settings, and SettingsProviderOverlayMalbec still adds that tile to the
 * default set — so a fresh flash still has a one-tap Daily/Game switch. This
 * widget is one long-press on the home screen away.
 */
class PenModeWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_TOGGLE = "org.lineageos.malbec.parts.TOGGLE_PEN_MODE"

        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, PenModeWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            for (id in ids) render(context, manager, id)
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val game = PenModeController.isGameMode(context)

            val views = RemoteViews(context.packageName, R.layout.widget_pen_mode).apply {
                // ★ setCharSequence(id, "setText", resId), not setTextViewText(id,
                // string). This passes the resource REFERENCE, which the host
                // re-resolves after a locale change (RemoteViews.java:7951-7967).
                // AppWidgetServiceImpl has no locale handler at all — it
                // re-broadcasts only on font scale (:646-661) and package change
                // (:4892) — so a baked-in string stays in the old language
                // indefinitely, until the mode is toggled or the APK is replaced.
                setCharSequence(
                    R.id.mode_label, "setText",
                    if (game) R.string.touch_mode_game_short
                    else R.string.touch_mode_daily_short,
                )
                setCharSequence(
                    R.id.mode_detail, "setText",
                    if (game) R.string.touch_mode_game_detail
                    else R.string.touch_mode_daily_detail,
                )

                // ★ setBackgroundResource and not a colour setter. setColorInt(id,
                // "setBackgroundColor", …) resolves to View.setBackgroundColor,
                // which replaces the background with a flat ColorDrawable and
                // discards the corner radius — the exact mistake that left the
                // first version of this widget square. setBackgroundResource is
                // @RemotableViewMethod (View.java:26633) and swaps the whole shape.
                setInt(
                    R.id.widget_root, "setBackgroundResource",
                    if (game) R.drawable.widget_background_active
                    else R.drawable.widget_background,
                )
                setInt(
                    R.id.icon_frame, "setBackgroundResource",
                    if (game) R.drawable.widget_icon_background_active
                    else R.drawable.widget_icon_background,
                )
                setImageViewResource(
                    R.id.mode_icon,
                    if (game) R.drawable.ic_speed else R.drawable.ic_stylus,
                )
                // The glyph colour is the one place a flat colour is correct: it is
                // a tint, not a shape. Both arguments are the same resource because
                // res/values-night/colors.xml already carries the dark variant and
                // the host resolves it for us.
                setColorInt(
                    R.id.mode_icon, "setColorFilter",
                    context.getColor(if (game) R.color.widget_icon_active else R.color.widget_icon),
                    context.getColor(if (game) R.color.widget_icon_active else R.color.widget_icon),
                )
                setTextColor(
                    R.id.mode_label,
                    context.getColor(
                        if (game) R.color.widget_title_active else R.color.widget_title
                    ),
                )
                setTextColor(
                    R.id.mode_detail,
                    context.getColor(
                        if (game) R.color.widget_detail_active else R.color.widget_detail
                    ),
                )
                // Same colour as the subtitle, so the affordance stays a hint.
                setColorInt(
                    R.id.swap_hint, "setColorFilter",
                    context.getColor(
                        if (game) R.color.widget_detail_active else R.color.widget_detail
                    ),
                    context.getColor(
                        if (game) R.color.widget_detail_active else R.color.widget_detail
                    ),
                )

                // TalkBack reads the whole card: which mode it is in, and that a
                // tap changes it. The glyph is importantForAccessibility="no", so
                // this is the only announcement.
                setContentDescription(
                    R.id.widget_root,
                    context.getString(
                        if (game) R.string.touch_mode_game else R.string.touch_mode_daily
                    ) + ". " + context.getString(R.string.pen_mode_widget_action),
                )

                setOnClickPendingIntent(R.id.widget_root, togglePendingIntent(context))
            }
            manager.updateAppWidget(id, views)
        }

        /**
         * One request code for every instance, because the action is global rather
         * than per-widget. FLAG_IMMUTABLE plus an explicit component, so nothing
         * else can retarget it.
         */
        private fun togglePendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, PenModeWidgetProvider::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) render(context, appWidgetManager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE) return

        // A widget tap can be the thing that starts this process, so make sure the
        // observer that defends the refresh rate is running before we leave.
        // startService on an already-running service costs one onStartCommand.
        MalbecPartsService.sync(context)

        // toggle() announces from the single funnel in PenModeController and
        // refreshes both surfaces, so there is nothing to do here afterwards.
        PenModeController.toggle(context)
    }
}
