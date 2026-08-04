/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.malbec.parts.display

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.pixelos.malbec.parts.Constants
import org.pixelos.malbec.parts.R

/**
 * One tap to swap between the stylus and the game state.
 *
 * A Quick Settings tile rather than only a home-screen widget, because the
 * moment you actually want this is while a game is already full-screen — where
 * the launcher is not reachable and the notification shade is.
 */
class PenModeTileService : TileService() {

    companion object {
        fun requestUpdate(context: Context) {
            runCatching {
                requestListeningState(
                    context,
                    ComponentName(context, PenModeTileService::class.java),
                )
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        PenModeController.toggle(this)
        refresh()
        PenModeWidgetProvider.requestUpdate(this)
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val game = PenModeController.isGameMode(this)
        tile.state = if (game) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.pen_mode_tile_label)
        tile.subtitle = getString(
            if (game) R.string.touch_mode_game_short else R.string.touch_mode_stylus_short
        )
        tile.contentDescription = getString(
            if (game) R.string.touch_mode_game else R.string.touch_mode_stylus
        )
        tile.updateTile()
    }
}
