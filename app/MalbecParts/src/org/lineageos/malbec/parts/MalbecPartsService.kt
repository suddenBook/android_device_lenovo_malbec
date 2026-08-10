/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.malbec.parts

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.hardware.input.KeyGestureEvent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.UserHandle
import android.util.Log
import org.lineageos.malbec.parts.display.PanelDirectionController
import org.lineageos.malbec.parts.display.PenModeController
import org.lineageos.malbec.parts.gesture.GestureAction
import org.lineageos.malbec.parts.gesture.GestureBinder
import org.lineageos.malbec.parts.gesture.SmartRemote
import org.lineageos.malbec.parts.stylus.PenPresenceWatcher

/**
 * The one long-lived piece of MalbecParts.
 *
 * It exists for three jobs that need a process alive when no UI is showing:
 *
 *   1. The KeyGestureEvent handler for the private gesture types — the
 *      context-aware "remote" and the camera shutter. Registration is
 *      per-process and exclusive (KeyGestureController.registerKeyGestureHandler
 *      :1539-1578 throws if the type is already taken, or if this pid already
 *      registered), so it must live in exactly one place.
 *   2. The pen link watcher for the out-of-range alert.
 *   3. The observer that keeps /proc/HighReportRate paired with
 *      peak_refresh_rate, so choosing 144 Hz from Settings > Display can never
 *      silently kill the stylus.
 *
 * It is NOT android:persistent. A device app that pins itself into memory for
 * three callbacks is exactly the sort of thing that makes a port feel heavier
 * than stock. It is started at boot and stays up.
 *
 * ⚠️ This sentence used to end "...and stopped again when every feature that needs
 * it is off — see [shouldRun]". There is no `shouldRun` (it was also a broken KDoc
 * link), and the [sync] KDoc twenty lines below says the opposite and is the
 * accurate one: there is deliberately no stop-when-idle branch, because job (3)
 * has no user switch. Corrected session 25; the file had two adjacent comments
 * giving opposite answers to the one question it owns.
 */
class MalbecPartsService : Service() {

    companion object {
        private const val TAG = "${Constants.TAG}/Svc"

        /**
         * Make sure the service is up. Safe to call from anywhere and as often
         * as you like; a second startService on a running service is a no-op
         * beyond onStartCommand.
         *
         * Called from BOOT_COMPLETED and from every settings screen's onCreate.
         * That, plus START_STICKY, is what keeps job (3) — the refresh-rate /
         * report-rate pairing — reliable without making the app persistent.
         *
         * ⚠️ There is deliberately no "stop when every feature is off" branch.
         * Job (3) has no user switch: it is what stops Settings > Display >
         * Refresh rate from silently killing the stylus, and it must hold
         * whether or not the owner has bound a single pen button.
         *
         * ⚠️ [Context.startServiceAsUser] with [UserHandle.SYSTEM], not plain
         * `startService`. This app shares `android.uid.system`, so an unqualified
         * `startService` makes `ContextImpl.warnIfCallingFromSystemProcess()` log
         *
         *     W ContextImpl: Calling a method in the system process without a
         *     qualified user: ContextImpl.startService … StylusSettingsActivity.onCreate
         *
         * on every settings-screen open and every tile tap — measured on the
         * flashed build, session 25. The warning is AOSP telling us the target
         * user is being inferred from whichever context happened to call, and
         * that inference is wrong here on purpose: everything this service owns
         * is **global to the device**, not per-user — sysfs nodes under /proc,
         * the InputManager custom-gesture table, and the pen's Bluetooth
         * metadata. It has to be exactly one instance, and it has to be the
         * system user's.
         *
         * `UserHandle.SYSTEM` rather than `CURRENT` for the same reason: this
         * tablet declares `android.software.managed_users`, so a secondary user
         * can open Device settings, and CURRENT would start a second copy that
         * fights the first over the same nodes.
         */
        fun sync(context: Context) {
            context.startServiceAsUser(
                Intent(context, MalbecPartsService::class.java), UserHandle.SYSTEM
            )
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var watcher: PenPresenceWatcher? = null
    private var observer: PenModeController.RefreshRateObserver? = null
    private var panelDirection: PanelDirectionController? = null
    private var handlerRegistered = false

    private val gestureHandler = InputManager.KeyGestureEventHandler { event, _ ->
        onGesture(event)
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "starting")

        observer = PenModeController.RefreshRateObserver(this, handler).also { it.register() }

        watcher = PenPresenceWatcher(this, handler).also { it.start() }

        panelDirection = PanelDirectionController(this, handler).also { it.start() }

        registerGestureHandler()

        // The framework's gesture table can be out of step with ours after a
        // factory reset, a user switch, or a `pm clear` — reconcile rather than
        // assume.
        GestureBinder.reconcile(this)
    }

    override fun onDestroy() {
        Log.i(TAG, "stopping")
        observer?.unregister()
        watcher?.stop()
        panelDirection?.stop()
        if (handlerRegistered) {
            runCatching {
                getSystemService(InputManager::class.java)
                    ?.unregisterKeyGestureEventHandler(gestureHandler)
            }
            handlerRegistered = false
        }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerGestureHandler() {
        val im = getSystemService(InputManager::class.java) ?: return
        try {
            im.registerKeyGestureEventHandler(
                GestureAction.PRIVATE_GESTURE_TYPES,
                gestureHandler,
            )
            handlerRegistered = true
        } catch (e: Exception) {
            // Exclusive registration: IllegalStateException if this pid already
            // holds one, IllegalArgumentException if another live process took
            // one of our types. Neither can happen with the private values in
            // Constants — AOSP's own enum is a dense range ending at 84 — but a
            // crash here would take the pen alert down with it, so it is caught
            // rather than trusted.
            Log.e(TAG, "could not register the key gesture handler", e)
        }
    }

    private fun onGesture(event: KeyGestureEvent) {
        // Fired on both the start and completion of a gesture; act once.
        if (event.action != KeyGestureEvent.ACTION_GESTURE_COMPLETE) return
        if (event.isCancelled) return

        when (val type = event.keyGestureType) {
            Constants.GESTURE_TYPE_PEN_LINK_GOING_AWAY ->
                watcher?.onPenAnnouncedDisconnect()

            else -> SmartRemote.handle(this, type)
        }
    }
}
