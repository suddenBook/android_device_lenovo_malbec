/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.malbec.parts.parallelwindow

import android.app.ActivityTaskManager
import android.os.RemoteException
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
}
