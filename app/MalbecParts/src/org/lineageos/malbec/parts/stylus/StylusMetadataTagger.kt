/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.malbec.parts.stylus

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.input.InputManager
import android.util.Log
import android.view.InputDevice
import org.lineageos.malbec.parts.Constants

/**
 * Two lines of code that unlock a whole AOSP settings page.
 *
 * AOSP already has stylus preferences — handwriting on/off, ignore the side
 * button, show the pointer icon, default notes app — in
 * Settings/connecteddevice/stylus/StylusDevicesController.java:69-77. They are
 * attached to a bonded device's detail page at
 * BluetoothDeviceDetailsFragment.java:311, and shown only when
 * SettingsLib's BluetoothUtils.isDeviceStylus() is true, which is
 * METADATA_DEVICE_TYPE == DEVICE_TYPE_STYLUS
 * (SettingsLib/.../bluetooth/BluetoothUtils.java:1289-1303).
 *
 * Nothing in AOSP or in this ROM ever sets that metadata for this pen, so the
 * page never appears. Setting it once per bond makes Settings > Connected
 * devices > Lenovo Tab Pen Plus > gear show the native stylus controls, with no
 * UI of ours at all.
 *
 * ⚠️ Metadata is persisted per device by the Bluetooth stack, so a wrong value
 * sticks until the pen is unpaired. Hence the VID/PID match rather than a name
 * match, and hence the read-before-write.
 */
object StylusMetadataTagger {

    private const val TAG = "${Constants.TAG}/Meta"

    /**
     * BluetoothDevice.METADATA_DEVICE_TYPE == 17 and
     * DEVICE_TYPE_STYLUS == "Stylus" (BluetoothDevice.java:880, :3532-3535).
     * Both are @SystemApi; naming the values here rather than the constants
     * keeps this compiling regardless of which Bluetooth module stub surface
     * ends up on the classpath.
     */
    private const val METADATA_DEVICE_TYPE = 17
    private const val DEVICE_TYPE_STYLUS = "Stylus"

    /**
     * Identify the pen by VID/PID rather than by name.
     *
     * The Bluetooth stack does not expose a HID device's VID/PID, but the input
     * stack does, and every InputDevice created from a Bluetooth HID node
     * carries the bonded address. That is exactly how SystemUI's
     * StylusManager.kt:121-150 correlates the two, so it is a supported
     * association and not a trick.
     */
    fun isPen(context: Context, device: BluetoothDevice): Boolean {
        val address = runCatching { device.address }.getOrNull() ?: return false
        val im = context.getSystemService(InputManager::class.java) ?: return false
        for (id in im.inputDeviceIds) {
            val input = im.getInputDevice(id) ?: continue
            if (input.vendorId != Constants.PEN_VENDOR_ID) continue
            if (input.productId != Constants.PEN_PRODUCT_ID) continue
            if (address.equals(input.bluetoothAddress, ignoreCase = true)) return true
        }
        // Fall back to the name only while the pen is disconnected, when it has
        // no input device to match against. Anchored, so it cannot match some
        // unrelated accessory.
        val name = runCatching { device.name }.getOrNull() ?: return false
        return name.startsWith("Lenovo Tab Pen", ignoreCase = true)
    }

    fun tagBondedPens(context: Context) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        if (adapter.state != BluetoothAdapter.STATE_ON) return
        val bonded = runCatching { adapter.bondedDevices }.getOrNull() ?: return
        for (device in bonded) {
            if (!isPen(context, device)) continue
            tag(device)
        }
    }

    private fun tag(device: BluetoothDevice) {
        try {
            val current = device.getMetadata(METADATA_DEVICE_TYPE)?.toString(Charsets.UTF_8)
            if (current == DEVICE_TYPE_STYLUS) return
            val ok = device.setMetadata(
                METADATA_DEVICE_TYPE,
                DEVICE_TYPE_STYLUS.toByteArray(Charsets.UTF_8),
            )
            Log.i(TAG, "tagged ${device.address} as a stylus: $ok")
        } catch (e: Exception) {
            // Needs BLUETOOTH_PRIVILEGED. If the platform ever tightens that,
            // the native stylus page simply does not appear; nothing else here
            // depends on it.
            Log.w(TAG, "setMetadata failed", e)
        }
    }

    /** True when a pen is bonded at all, used to decide whether to show a page. */
    fun hasBondedPen(context: Context): Boolean {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        if (adapter.state != BluetoothAdapter.STATE_ON) return false
        val bonded = runCatching { adapter.bondedDevices }.getOrNull() ?: return false
        return bonded.any { isPen(context, it) }
    }

    /** True when the pen's HID nodes are present right now. */
    fun isPenConnected(context: Context): Boolean {
        val im = context.getSystemService(InputManager::class.java) ?: return false
        return im.inputDeviceIds.any { id ->
            val d: InputDevice? = im.getInputDevice(id)
            d != null &&
                d.vendorId == Constants.PEN_VENDOR_ID &&
                d.productId == Constants.PEN_PRODUCT_ID
        }
    }
}
