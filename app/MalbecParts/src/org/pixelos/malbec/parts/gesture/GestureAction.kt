/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.malbec.parts.gesture

import android.hardware.input.AppLaunchData
import android.hardware.input.KeyGestureEvent
import org.pixelos.malbec.parts.Constants
import org.pixelos.malbec.parts.R

/**
 * The catalogue of things a pen button or a keyboard app key can be bound to.
 *
 * ★ Every entry here is either handled by a KeyGestureEvent handler that is
 * ACTUALLY REGISTERED on this device, or by MalbecParts itself. That distinction
 * is load-bearing and is the reason this list is curated rather than generated
 * from KeyGestureEvent's 84 constants.
 *
 * KeyGestureController.handleKeyGesture (:1223-1233) looks the type up in
 * mSupportedKeyGestureToPidMap and, if nobody has registered it, logs
 * "Key gesture: N is not supported" and drops it. Silently. So binding a button
 * to an unregistered type ships a menu entry that does nothing.
 *
 * Read off the running device (`dumpsys input`, grep KeyGestureController) at
 * boot+3 min with SystemUI and Launcher up:
 *
 *   mSupportedKeyGestures = [1,2,3,4,5,6,7,8,10,11,12,13,14,15,16,17,21,22,23,
 *                            25,27,28,31,32,51,52,53,54,55,56,57,59,60,61,62,
 *                            64,65,66,67,68,69,70,71,72,75,76,77,78,80,81,82,84]
 *
 * ⚠️ Notably ABSENT, and therefore not offered here even though they look like
 * the obvious stylus actions:
 *   33 OPEN_NOTES              — SystemUI's NoteTaskInitializer only registers
 *                                it when a notes role holder exists. "Open the
 *                                notes app" is provided instead via
 *                                LAUNCH_APPLICATION + RoleData(NOTES), which is
 *                                registered and resolves the same app
 *                                (ModifierShortcutManager.java:166-167).
 *   79 TOGGLE_QUICK_SETTINGS_PANEL, 83 TAKE_PARTIAL_SCREENSHOT — flag-gated in
 *                                SysUIKeyGestureEventInitializer.
 *   63, 73 accessibility        — registered only when the feature is on.
 *
 * If a future build registers more, add them here — but re-read the dump first
 * rather than assuming.
 */
data class GestureAction(
    /** Stable id stored in SharedPreferences. Never renumber or reuse. */
    val id: String,
    val labelRes: Int,
    val gestureType: Int,
    val appLaunchData: AppLaunchData? = null,
) {
    companion object {

        const val ID_NONE = "none"
        const val ID_LAUNCH_APP = "launch_app"

        /**
         * Prefix for "launch this specific app", stored as
         * `launch_app:<package>/<class>`.
         */
        const val LAUNCH_APP_PREFIX = "launch_app:"

        val NONE = GestureAction(
            ID_NONE,
            R.string.action_none,
            KeyGestureEvent.KEY_GESTURE_TYPE_UNSPECIFIED,
        )

        /**
         * Fixed actions, in the order the picker shows them. Grouped roughly by
         * how likely they are to be wanted on a stylus.
         */
        val CATALOGUE: List<GestureAction> = listOf(
            NONE,

            // -- The context-aware "remote". MalbecParts handles these itself;
            //    see SmartRemote.kt for what each one resolves to.
            GestureAction(
                "remote_primary",
                R.string.action_remote_primary,
                Constants.GESTURE_TYPE_SMART_REMOTE_PRIMARY,
            ),
            GestureAction(
                "remote_next",
                R.string.action_remote_next,
                Constants.GESTURE_TYPE_SMART_REMOTE_NEXT,
            ),
            GestureAction(
                "remote_previous",
                R.string.action_remote_previous,
                Constants.GESTURE_TYPE_SMART_REMOTE_PREVIOUS,
            ),
            GestureAction(
                "camera_shutter",
                R.string.action_camera_shutter,
                Constants.GESTURE_TYPE_CAMERA_SHUTTER,
            ),

            // -- Things the framework already does, bound with no code of ours.
            GestureAction(
                "screenshot",
                R.string.action_screenshot,
                KeyGestureEvent.KEY_GESTURE_TYPE_TAKE_SCREENSHOT,
            ),
            GestureAction(
                "notes",
                R.string.action_notes,
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                AppLaunchData.RoleData(ROLE_NOTES),
            ),
            GestureAction(
                "assistant",
                R.string.action_assistant,
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_ASSISTANT,
            ),
            GestureAction(
                "search",
                R.string.action_search,
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_SEARCH,
            ),
            GestureAction(
                "recents",
                R.string.action_recents,
                KeyGestureEvent.KEY_GESTURE_TYPE_RECENT_APPS,
            ),
            GestureAction(
                "all_apps",
                R.string.action_all_apps,
                KeyGestureEvent.KEY_GESTURE_TYPE_ALL_APPS,
            ),
            GestureAction(
                "home",
                R.string.action_home,
                KeyGestureEvent.KEY_GESTURE_TYPE_HOME,
            ),
            GestureAction(
                "back",
                R.string.action_back,
                KeyGestureEvent.KEY_GESTURE_TYPE_BACK,
            ),
            GestureAction(
                "notification_panel",
                R.string.action_notification_panel,
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_NOTIFICATION_PANEL,
            ),
            GestureAction(
                "lock_screen",
                R.string.action_lock_screen,
                KeyGestureEvent.KEY_GESTURE_TYPE_LOCK_SCREEN,
            ),
            GestureAction(
                "split_left",
                R.string.action_split_left,
                KeyGestureEvent.KEY_GESTURE_TYPE_SPLIT_SCREEN_NAVIGATION_LEFT,
            ),
            GestureAction(
                "split_right",
                R.string.action_split_right,
                KeyGestureEvent.KEY_GESTURE_TYPE_SPLIT_SCREEN_NAVIGATION_RIGHT,
            ),
            GestureAction(
                "desktop_mode",
                R.string.action_desktop_mode,
                KeyGestureEvent.KEY_GESTURE_TYPE_DESKTOP_MODE,
            ),
            GestureAction(
                "fullscreen",
                R.string.action_fullscreen,
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_FULLSCREEN,
            ),
            GestureAction(
                "dnd",
                R.string.action_dnd,
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_DO_NOT_DISTURB,
            ),
            GestureAction(
                "magnification",
                R.string.action_magnification,
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
            ),
            GestureAction(
                "brightness_up",
                R.string.action_brightness_up,
                KeyGestureEvent.KEY_GESTURE_TYPE_BRIGHTNESS_UP,
            ),
            GestureAction(
                "brightness_down",
                R.string.action_brightness_down,
                KeyGestureEvent.KEY_GESTURE_TYPE_BRIGHTNESS_DOWN,
            ),
            GestureAction(
                "settings",
                R.string.action_settings,
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_SYSTEM_SETTINGS,
            ),

            // -- Always last: opens a second picker.
            GestureAction(
                ID_LAUNCH_APP,
                R.string.action_launch_app,
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
            ),
        )

        /**
         * android.app.role.NOTES. Hardcoded rather than referenced from
         * RoleManager: that class lives in the Permission mainline module
         * (packages/modules/Permission/framework-s/), which platform_apis does
         * not put on this app's classpath. The string is the API.
         */
        const val ROLE_NOTES = "android.app.role.NOTES"

        fun byId(id: String?): GestureAction {
            if (id == null) return NONE
            if (id.startsWith(LAUNCH_APP_PREFIX)) {
                val flat = id.removePrefix(LAUNCH_APP_PREFIX)
                val slash = flat.indexOf('/')
                if (slash <= 0 || slash == flat.length - 1) return NONE
                return GestureAction(
                    id,
                    R.string.action_launch_app,
                    KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                    AppLaunchData.ComponentData(
                        flat.substring(0, slash),
                        flat.substring(slash + 1),
                    ),
                )
            }
            return CATALOGUE.firstOrNull { it.id == id } ?: NONE
        }

        /** Encodes "launch this component" into a stored id. */
        fun launchAppId(packageName: String, className: String) =
            "$LAUNCH_APP_PREFIX$packageName/$className"

        /** The five private types MalbecParts must register a handler for. */
        val PRIVATE_GESTURE_TYPES = listOf(
            Constants.GESTURE_TYPE_SMART_REMOTE_PRIMARY,
            Constants.GESTURE_TYPE_SMART_REMOTE_NEXT,
            Constants.GESTURE_TYPE_SMART_REMOTE_PREVIOUS,
            Constants.GESTURE_TYPE_CAMERA_SHUTTER,
            Constants.GESTURE_TYPE_PEN_LINK_GOING_AWAY,
        )
    }
}
