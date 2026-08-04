/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.malbec.parts.display

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import org.pixelos.malbec.parts.R

/**
 * The home-screen half of the same switch. Requested by the owner specifically:
 * one tap on the desktop, plus a toast saying which state it landed in.
 */
class PenModeWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_TOGGLE = "org.pixelos.malbec.parts.TOGGLE_PEN_MODE"

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
                setTextViewText(
                    R.id.mode_label,
                    context.getString(
                        if (game) R.string.touch_mode_game_short
                        else R.string.touch_mode_stylus_short
                    ),
                )
                setTextViewText(
                    R.id.mode_detail,
                    context.getString(
                        if (game) R.string.touch_mode_game_detail
                        else R.string.touch_mode_stylus_detail
                    ),
                )
                setOnClickPendingIntent(R.id.widget_root, togglePendingIntent(context))
            }
            manager.updateAppWidget(id, views)
        }

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

        PenModeController.toggle(context)
        val game = PenModeController.isGameMode(context)
        Toast.makeText(
            context,
            context.getString(if (game) R.string.touch_mode_game else R.string.touch_mode_stylus),
            Toast.LENGTH_SHORT,
        ).show()
        requestUpdate(context)
        PenModeTileService.requestUpdate(context)
    }
}
