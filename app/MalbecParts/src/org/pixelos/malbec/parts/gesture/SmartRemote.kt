/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.malbec.parts.gesture

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import org.pixelos.malbec.parts.Constants

/**
 * The context-aware half of the pen's remote-control mode.
 *
 * ── What stock does, and what we kept ──────────────────────────────────────
 *
 * ZUI's BluetoothPenInputPolicy.processStylusPenRemoteControl recomputes a
 * "remote control state" on every foreground-package change and then synthesises
 * ordinary AOSP keycodes: DPAD_DOWN/UP in presentation apps, DPAD_RIGHT/LEFT in
 * reading apps, MEDIA_NEXT/PREVIOUS/PLAY_PAUSE otherwise. That shape is right —
 * a presenter button has no single correct keycode — and it is what this class
 * reproduces.
 *
 * ── What stock does that we deliberately did NOT copy ──────────────────────
 *
 * Stock decides which bucket an app is in from five hardcoded package lists in
 * framework-res (config_stylus_pen_remote_control_{camara,office,reading,video,
 * music}_packages), naming about forty specific apps — WPS, iQiyi, Youku,
 * Bilibili, Douyin, Kuaishou, NetEase Music, Kugou and so on. Copying those
 * would mean this ROM's stylus works with Chinese video apps and not with, say,
 * Kindle or Keynote, which is both wrong for this owner and unmaintainable.
 *
 * The AOSP-native answer to "what kind of app is this" already exists:
 * ApplicationInfo.category, set by the app in its manifest via
 * android:appCategory and used by the framework for exactly this class of
 * question. It covers every app rather than forty, and it needs no updating.
 *
 * Apps that declare nothing land in CATEGORY_UNDEFINED and get media keys — the
 * same fallback stock uses for its "other" state.
 *
 * ── Escape hatch ──────────────────────────────────────────────────────────
 *
 * If music or video is actually playing, media keys win regardless of category.
 * That is the behaviour a user expects from a remote and it costs one call.
 */
object SmartRemote {

    private const val TAG = "${Constants.TAG}/Remote"

    fun handle(context: Context, gestureType: Int) {
        when (gestureType) {
            Constants.GESTURE_TYPE_CAMERA_SHUTTER ->
                // KEYCODE_CAMERA is a system key, so it goes straight to the
                // focused camera app. PixelOS's own camera (Aperture,
                // HardwareKey.kt:31-37) treats it as the shutter, and so does
                // GCam. This is the native equivalent of ZUI's private
                // lenovo.intent.action.INPUT_DEVICE_CLICK_STATE_CHANGED
                // broadcast, which only com.zui.camera understands.
                inject(context, KeyEvent.KEYCODE_CAMERA)

            Constants.GESTURE_TYPE_SMART_REMOTE_PRIMARY -> when (profile(context)) {
                Profile.PRESENTATION -> inject(context, KeyEvent.KEYCODE_F5)
                Profile.READING -> inject(context, KeyEvent.KEYCODE_DPAD_RIGHT)
                Profile.MEDIA -> media(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }

            Constants.GESTURE_TYPE_SMART_REMOTE_NEXT -> when (profile(context)) {
                Profile.PRESENTATION -> inject(context, KeyEvent.KEYCODE_DPAD_DOWN)
                Profile.READING -> inject(context, KeyEvent.KEYCODE_DPAD_RIGHT)
                Profile.MEDIA -> media(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            }

            Constants.GESTURE_TYPE_SMART_REMOTE_PREVIOUS -> when (profile(context)) {
                Profile.PRESENTATION -> inject(context, KeyEvent.KEYCODE_DPAD_UP)
                Profile.READING -> inject(context, KeyEvent.KEYCODE_DPAD_LEFT)
                Profile.MEDIA -> media(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
        }
    }

    private enum class Profile { PRESENTATION, READING, MEDIA }

    private fun profile(context: Context): Profile {
        // Anything actually playing wins.
        val am = context.getSystemService(AudioManager::class.java)
        if (am?.isMusicActive == true) return Profile.MEDIA

        return when (foregroundCategory(context)) {
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> Profile.PRESENTATION
            ApplicationInfo.CATEGORY_NEWS -> Profile.READING
            else -> Profile.MEDIA
        }
    }

    private fun foregroundCategory(context: Context): Int {
        val pkg = foregroundPackage(context) ?: return ApplicationInfo.CATEGORY_UNDEFINED
        return try {
            context.packageManager
                .getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                .category
        } catch (e: PackageManager.NameNotFoundException) {
            ApplicationInfo.CATEGORY_UNDEFINED
        }
    }

    private fun foregroundPackage(context: Context): String? {
        val am = context.getSystemService(ActivityManager::class.java) ?: return null
        return try {
            // Needs REAL_GET_TASKS, which this app holds; without it the
            // framework returns only our own tasks and this would silently
            // always take the default branch.
            @Suppress("DEPRECATION")
            am.getRunningTasks(1).firstOrNull()?.topActivity?.packageName
        } catch (e: SecurityException) {
            Log.w(TAG, "getRunningTasks denied", e)
            null
        }
    }

    /**
     * Media keys go through MediaSession, not the input pipeline. That routes to
     * whichever session is actually holding audio focus even when it is not the
     * foreground app — which is the whole point of a remote — and it needs no
     * INJECT_EVENTS.
     */
    private fun media(context: Context, keycode: Int) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val now = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keycode, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keycode, 0))
    }

    /**
     * Navigation and shutter keys have to reach the focused window, so they go
     * through the input pipeline.
     *
     * SOURCE_KEYBOARD rather than SOURCE_UNKNOWN: a DPAD event with no source
     * does not move focus in a view hierarchy.
     */
    private fun inject(context: Context, keycode: Int) {
        val im = context.getSystemService(InputManager::class.java) ?: return
        val now = SystemClock.uptimeMillis()
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val event = KeyEvent(
                /* downTime = */ now,
                /* eventTime = */ now,
                /* action = */ action,
                /* code = */ keycode,
                /* repeat = */ 0,
                /* metaState = */ 0,
                // The device id the framework itself uses for synthesised keys;
                // see Instrumentation#sendKeySync.
                /* deviceId = */ KeyCharacterMap.VIRTUAL_KEYBOARD,
                /* scancode = */ 0,
                /* flags = */ KeyEvent.FLAG_FROM_SYSTEM,
                /* source = */ InputDevice.SOURCE_KEYBOARD,
            )
            im.injectInputEvent(event, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)
        }
    }
}
