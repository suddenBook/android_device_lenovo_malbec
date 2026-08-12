/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.malbec.parts.parallelwindow

import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.content.Context
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import android.window.ParallelWindowControlState

/** The only state boundary used by the UI. No preference is persisted locally. */
internal class ParallelWindowBackend {
    @Throws(RemoteException::class)
    fun getControlState(): ParallelWindowControlState =
        ActivityTaskManager.getService().getParallelWindowControlState()

    @Throws(RemoteException::class)
    fun setUserEnabled(enabled: Boolean): Boolean =
        ActivityTaskManager.getService().setParallelWindowUserEnabled(enabled)

    @Throws(RemoteException::class)
    fun setPackageEnabled(packageName: String, enabled: Boolean): Boolean =
        ActivityTaskManager.getService().setParallelWindowPackageEnabled(packageName, enabled)

    /**
     * Ends the app's processes so its next start reads the new rule.
     *
     * ★ Needs no permission this app has to ask for, which is not obvious.
     * KILL_BACKGROUND_PROCESSES is `normal` and auto-granted, but the call also
     * reaches a cross-appId check that wants KILL_ALL_BACKGROUND_PROCESSES
     * (`signature|privileged`) — and MalbecParts is
     * sharedUserId="android.uid.system", so
     * ActivityManager.canAccessUnexportedComponents grants appId 1000 every AMS
     * permission unconditionally. The manifest declares the `normal` one anyway,
     * for the @RequiresPermission lint and so the dependency is written down.
     *
     * ⚠️ Deliberately NOT forceStopPackage. That would work on every app,
     * including a foreground one, and would also set FLAG_STOPPED: cancelled
     * PendingIntents and alarms, suspended jobs and sync, a widget that stops
     * updating, a destroyed back stack, and a spurious ACTION_BOOT_COMPLETED on
     * the next launch (stay_stopped and app_restrictions_api are both ENABLED on
     * this build — checked with `aflags list`). That is a very large bill for a
     * layout preference, and it is why AOSP hides Force stop behind its own
     * warning dialog. See ParallelWindowRestartOutcome for what we pay instead.
     */
    fun killBackgroundProcesses(context: Context, packageName: String): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        return try {
            manager.killBackgroundProcesses(packageName)
            true
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Unable to kill background processes for $packageName", exception)
            false
        }
    }

    /**
     * Whether any process of [packageName] is still alive, or null if the list
     * could not be read.
     *
     * ⚠️ Named for what it RETURNS, not for what it waits for. It polls until the
     * process is gone, so "await…Gone" reads better and would have inverted the
     * sense of every call site — true here means the app survived.
     *
     * ⚠️ This POLLS, and the reason is a real race rather than caution.
     * ProcessList.getRunningAppProcessesLOSP filters on `app.getThread() != null`,
     * and ProcessRecord.killLocked does not clear the thread — that happens later,
     * in handleAppDiedLocked, when the binder death notification arrives. So a
     * single read taken immediately after the kill routinely still reports the
     * process, and reporting "still running" for an app that is two milliseconds
     * from gone would understate what happened on almost every toggle.
     *
     * The window is bounded and small, this runs on a background executor that
     * has just done two Binder round trips, and timing out lands on
     * NEXT_START — a claim that is true either way. So the failure direction is
     * the safe one.
     *
     * getRunningAppProcesses returns other apps' processes only to a caller
     * holding REAL_GET_TASKS, which this app declares and is allowlisted for.
     */
    fun stillRunningAfterKill(context: Context, packageName: String): Boolean? {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return null
        val deadline = SystemClock.uptimeMillis() + KILL_SETTLE_TIMEOUT_MS
        var everRead = false
        while (true) {
            val processes = manager.runningAppProcesses
            if (processes != null) {
                everRead = true
                if (processes.none { packageName in (it.pkgList ?: emptyArray()) }) return false
            }
            if (SystemClock.uptimeMillis() >= deadline) break
            try {
                Thread.sleep(KILL_SETTLE_POLL_MS)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        return if (everRead) true else null
    }

    private companion object {
        private const val TAG = "MalbecParts/ParallelWindow"

        /**
         * Long enough for a SIGKILLed process's death notification to reach AMS,
         * short enough to stay inside the settle already spent on the write and
         * the authoritative reread. Not a correctness bound — expiring means the
         * weaker, always-true message.
         */
        private const val KILL_SETTLE_TIMEOUT_MS = 600L
        private const val KILL_SETTLE_POLL_MS = 50L
    }
}
