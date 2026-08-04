/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.malbec.parts.gesture

import android.content.Context
import android.content.SharedPreferences
import android.hardware.input.InputGestureData
import android.hardware.input.InputManager
import android.util.Log
import androidx.preference.PreferenceManager
import org.pixelos.malbec.parts.Constants

/**
 * Turns the user's stored choices into framework state.
 *
 * The framework owns the persistence: addCustomInputGesture writes through to
 * /data/system/users/<id>/input_gestures.xml
 * (InputGesturePersistedData / PersistedData). Our SharedPreferences are the
 * *source*, not a cache — they also record which action id produced a binding,
 * which the framework does not store in a form we can map back to a menu entry.
 *
 * reconcile() is idempotent and is called from three places: boot, every
 * settings change, and whenever the service starts. That is deliberate — a
 * factory reset or a `pm clear` of the framework's gesture file would otherwise
 * leave the pen silently dead with the settings still showing the old choices.
 */
object GestureBinder {

    private const val TAG = "${Constants.TAG}/Bind"

    fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun actionFor(context: Context, prefKey: String): GestureAction =
        GestureAction.byId(prefs(context).getString(prefKey, GestureAction.ID_NONE))

    fun setAction(context: Context, prefKey: String, actionId: String) {
        prefs(context).edit().putString(prefKey, actionId).apply()
        reconcile(context)
    }

    /**
     * Rewrites the framework's custom-gesture table so it matches ours exactly.
     *
     * ⚠️ Only OUR triggers are touched. removeAllCustomInputGestures(Filter.KEY)
     * would be simpler and is what the obvious implementation does, but it would
     * also wipe any key shortcut the user set in Settings > System > Keyboard >
     * Keyboard shortcuts, which uses the same per-user table
     * (TouchpadThreeFingerTap* and the shortcut editor both call
     * addCustomInputGesture). Deleting another feature's rows as a side effect
     * of saving ours is the kind of thing that is only noticed months later.
     */
    fun reconcile(context: Context) {
        val im = context.getSystemService(InputManager::class.java) ?: run {
            Log.w(TAG, "no InputManager")
            return
        }

        val existing = try {
            im.getCustomInputGestures(InputGestureData.Filter.KEY).orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "getCustomInputGestures failed", e)
            emptyList()
        }

        val ourKeycodes = Constants.ALL_TRIGGERS.map { it.second }.toMutableSet()
        // The pen's link-going-away signal is ours too, but is never user-facing.
        ourKeycodes.add(Constants.KEY_PEN_BT_DISCONNECT)

        // 1. Drop every existing binding on a keycode we own.
        for (data in existing) {
            val trigger = data.trigger as? InputGestureData.KeyTrigger ?: continue
            if (trigger.keycode !in ourKeycodes) continue
            try {
                im.removeCustomInputGesture(data)
            } catch (e: Exception) {
                Log.w(TAG, "remove failed for keycode ${trigger.keycode}", e)
            }
        }

        // 2. Add back what the user actually chose.
        for ((prefKey, keycode) in Constants.ALL_TRIGGERS) {
            val action = actionFor(context, prefKey)
            if (action.id == GestureAction.ID_NONE) continue
            // "Launch app" with nothing picked yet is not a binding.
            if (action.id == GestureAction.ID_LAUNCH_APP) continue
            bind(im, keycode, action)
        }

        // 3. The reserved one, always on when the alert is enabled. It costs
        //    nothing when the pen never sends it.
        bind(
            im,
            Constants.KEY_PEN_BT_DISCONNECT,
            GestureAction(
                "pen_link_going_away",
                0,
                Constants.GESTURE_TYPE_PEN_LINK_GOING_AWAY,
            ),
        )
    }

    private fun bind(im: InputManager, keycode: Int, action: GestureAction) {
        try {
            val builder = InputGestureData.Builder()
                .setTrigger(InputGestureData.createKeyTrigger(keycode, /* modifierState = */ 0))
                // false: a focused window that has requested keyboard capture
                // must not be able to eat a hardware button the user has bound.
                // AOSP's own system shortcuts split on this; for a physical
                // device button, "always works" is the right answer.
                .setAllowCaptureByFocusedWindow(false)
            if (action.appLaunchData != null) {
                builder.setAppLaunchData(action.appLaunchData)
            } else {
                builder.setKeyGestureType(action.gestureType)
            }
            val result = im.addCustomInputGesture(builder.build())
            if (result != InputManager.CUSTOM_INPUT_GESTURE_RESULT_SUCCESS) {
                Log.w(TAG, "addCustomInputGesture($keycode -> ${action.id}) = $result")
            }
        } catch (e: Exception) {
            Log.w(TAG, "bind failed: $keycode -> ${action.id}", e)
        }
    }

    /** True when at least one trigger is bound to something we must handle. */
    fun needsHandler(context: Context): Boolean =
        Constants.ALL_TRIGGERS.any {
            actionFor(context, it.first).gestureType in GestureAction.PRIVATE_GESTURE_TYPES
        }
}
