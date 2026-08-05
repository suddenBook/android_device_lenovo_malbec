/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.malbec.parts.stylus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.preference.PreferenceManager
import org.pixelos.malbec.parts.Constants
import org.pixelos.malbec.parts.R

/**
 * "You left your pen behind."
 *
 * ── What the stock feature actually is ────────────────────────────────────
 *
 * Not RSSI, and not a buzzer in the pen — the Lenovo Tab Pen Plus has no
 * speaker. Verified two ways against the stock ROM: zero hits for
 * readRemoteRssi anywhere in ZUI's pen stack, and zero hits for
 * immediateAlert/0x1802/findMe across the whole of PenService.apk.
 *
 * The whole feature is: the HID link dropped, the pen did not initiate it, and
 * it did not come back within a grace period. Then the TABLET alerts.
 *
 * ── Suppression rules ─────────────────────────────────────────────────────
 *
 * Stock has eight, and they are all kept here, because each one exists because
 * it caused a false alarm. Reproducing them is the difference between a useful
 * feature and one the owner turns off in a week.
 *
 * The one addition: stock's 5 s grace period is short for a pen with no POGO
 * dock. 30 s is used here — long enough that walking to the next room and back
 * does not cry wolf, short enough to be useful when you leave a cafe.
 *
 * The one subtraction: stock calls PowerManager.wakeUp() unconditionally, so a
 * link drop while the tablet is in your bag turns the screen on. That is
 * arguably a bug; here it is opt-in and off by default.
 *
 * A notification, not a full-screen dialog: a modal dialog thrown up by a
 * background service is exactly the "foreign panel" shape the owner rejected
 * when they ruled out porting the ZUI apps.
 */
class PenPresenceWatcher(
    private val context: Context,
    private val handler: Handler,
) {

    companion object {
        private const val TAG = "${Constants.TAG}/Pen"
        private const val CHANNEL_ID = "pen_left_behind"
        private const val NOTIFICATION_ID = 1001

        /** See the class comment for why this is not stock's 5000. */
        private const val GRACE_MILLIS = 30_000L

        /**
         * BluetoothHidHost.ACTION_CONNECTION_STATE_CHANGED. The class itself is
         * @SystemApi inside the Bluetooth mainline module; the action string is
         * the stable part, and is what the framework broadcasts. See
         * packages/modules/Bluetooth/framework/java/android/bluetooth/
         * BluetoothHidHost.java:86-90.
         */
        private const val ACTION_HID_STATE_CHANGED =
            "android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED"
    }

    // @Volatile because these are written from two threads: the main thread
    // (the BroadcastReceiver, and onPenAnnouncedDisconnect via the key-gesture
    // handler) and a binder thread — IKeyGestureHandler.aidl:23 is `oneway`, so
    // InputManagerGlobal dispatches the pen's goodbye off the main looper
    // (InputManagerGlobal.java:1188-1199).
    @Volatile private var localInitiated = false
    @Volatile private var penSaidGoodbye = false
    @Volatile private var alertedThisSession = false
    // Rule 7's actual state. Set only in onConnected(); an alert can only be
    // armed for a pen this session has actually seen connected.
    @Volatile private var sawConnected = false
    @Volatile private var pendingAlert: Runnable? = null

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val device = intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
            )
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF
                    )
                    // Rule 1: the adapter is going down. Everything disconnects;
                    // none of it means the pen was left behind.
                    if (state == BluetoothAdapter.STATE_TURNING_OFF ||
                        state == BluetoothAdapter.STATE_OFF
                    ) {
                        cancelPending()
                    }
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    // Rule 2: the user just unpaired it on purpose.
                    if (isPen(device) && intent.getIntExtra(
                            BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE
                        ) == BluetoothDevice.BOND_NONE
                    ) {
                        cancelPending()
                    }
                }

                ACTION_HID_STATE_CHANGED -> {
                    if (!isPen(device)) return
                    when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
                        BluetoothProfile.STATE_CONNECTED -> onConnected()
                        // Rule 3: WE initiated it. STATE_DISCONNECTING is only
                        // ever seen on a local disconnect.
                        BluetoothProfile.STATE_DISCONNECTING -> localInitiated = true
                        BluetoothProfile.STATE_DISCONNECTED -> onDisconnected(device)
                    }
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(ACTION_HID_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        createChannel()
        // Tag whatever pen is already bonded, so the AOSP stylus preferences
        // appear without waiting for a reconnect.
        StylusMetadataTagger.tagBondedPens(context)
    }

    fun stop() {
        cancelPending()
        runCatching { context.unregisterReceiver(receiver) }
    }

    /**
     * The pen told us it is going away on purpose — HID usage 0x0c0605, which
     * the keylayout maps to F18 and GestureBinder binds to a private gesture
     * type so it reaches us. Stock consumes the same signal for the same reason
     * (BluetoothPenInputPolicy, the PEN_BT_DISCONNECT case).
     *
     * Rule 4.
     */
    fun onPenAnnouncedDisconnect() {
        Log.i(TAG, "pen announced disconnect; suppressing the next alert")
        penSaidGoodbye = true
        cancelPending()
    }

    private fun onConnected() {
        cancelPending()
        localInitiated = false
        penSaidGoodbye = false
        alertedThisSession = false
        sawConnected = true
        StylusMetadataTagger.tagBondedPens(context)
        prefs.edit().putLong(Constants.PREF_PEN_LAST_SEEN, System.currentTimeMillis()).apply()
    }

    private fun onDisconnected(device: BluetoothDevice?) {
        prefs.edit().putLong(Constants.PREF_PEN_LAST_SEEN, System.currentTimeMillis()).apply()

        // Rule 5: the user switched the feature off.
        if (!prefs.getBoolean(Constants.PREF_PEN_LOST_ALERT, false)) return
        // Rule 6: we already said so once for this connection.
        if (alertedThisSession) return
        // Rules 3 and 4, evaluated here because the state that sets them arrives
        // before this.
        if (localInitiated || penSaidGoodbye) {
            localInitiated = false
            penSaidGoodbye = false
            return
        }
        // Rule 7: the pen is bonded but was never connected in this session, so
        // this is a boot-time or adapter-restart artefact.
        //
        // ⚠️ This used to read `if (device == null) return`, which is
        // unreachable: `device` comes from EXTRA_DEVICE, and the branch at :122
        // (`if (!isPen(device)) return`) has already proved it non-null on every
        // path that reaches here. The rule was stated but not implemented, so a
        // HID STATE_DISCONNECTED emitted during adapter bring-up for a bonded
        // but absent pen would arm the 30 s timer and fire.
        if (!sawConnected) return
        if (device == null) return

        cancelPending()
        val alert = Runnable {
            pendingAlert = null
            alertedThisSession = true
            fire(device)
        }
        pendingAlert = alert
        // Rule 8, the grace period: a pen that reconnects inside it never
        // produced an alert at all.
        handler.postDelayed(alert, GRACE_MILLIS)
    }

    private fun cancelPending() {
        pendingAlert?.let { handler.removeCallbacks(it) }
        pendingAlert = null
    }

    private fun fire(device: BluetoothDevice) {
        Log.i(TAG, "pen out of range for ${GRACE_MILLIS}ms")

        // ⚠️ NO vibration. This device has no vibrator at all, and the alert
        // used to both call Vibrator#vibrate and promise a buzz in its summary
        // string. Measured, not assumed:
        //
        //     adb shell dumpsys vibrator_manager | grep -A2 Vibrators
        //       vibratorIds = []
        //       Vibrators:
        //     adb shell pm list features | grep -i vibrat   -> nothing
        //
        // Session 8 had already established this and removed the whole vibrator
        // stack from the tree; the anti-loss feature was written two rounds later
        // without checking. vibrate() on a device with no vibrator is a silent
        // no-op, so the code looked fine and the string lied to the owner.
        //
        // (Session 17 first "fixed" this by adding VibrationAttributes so Battery
        // Saver would not drop it -- a correct fix to a call that can never make
        // a sound. Recorded here because it is exactly the kind of plausible
        // change that survives review.)

        if (prefs.getBoolean(Constants.PREF_PEN_LOST_WAKE, false)) {
            context.getSystemService(PowerManager::class.java)?.wakeUp(
                SystemClock.uptimeMillis(),
                PowerManager.WAKE_REASON_APPLICATION,
                "$TAG:left-behind",
            )
        }

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val name = runCatching { device.name }.getOrNull()
            ?: context.getString(R.string.stylus_default_name)

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stylus)
            .setContentTitle(context.getString(R.string.pen_lost_notification_title, name))
            .setContentText(context.getString(R.string.pen_lost_notification_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()

        context.getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.pen_lost_channel),
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private fun isPen(device: BluetoothDevice?): Boolean =
        device != null && StylusMetadataTagger.isPen(context, device)
}
