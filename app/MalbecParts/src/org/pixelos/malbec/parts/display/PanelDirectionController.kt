/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.malbec.parts.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.SystemProperties
import android.util.Log
import android.view.Display
import org.pixelos.malbec.parts.Constants

/**
 * Tell the touch controller which way up the tablet is.
 *
 * ── The defect this closes ────────────────────────────────────────────────
 *
 * The Novatek controller does its own edge and palm rejection in firmware, and
 * it needs to know the display rotation to place the rejection bands. On a
 * plain AOSP userspace nothing ever tells it, so it stays at its power-on
 * default of 0 degrees — PORTRAIT — on a 13-inch tablet whose normal
 * orientation is landscape with a keyboard attached. Measured on the device,
 * with the display at ROTATION_90:
 *
 *     /proc/panel_direction  ->  Vertical Direction!(0 degree)
 *     dumpsys window         ->  mRotation=ROTATION_90
 *
 * i.e. the rejection bands sit along the short edges while the user's palms
 * rest on the long ones.
 *
 * Stock does this from ZuiPhoneWindowManager's EdgeInhibitionInputPolicy, which
 * follows mCurrentRotation and re-sends on rotate and on screen-on. PixelOS has
 * no equivalent, and there is no ITouchscreen client anywhere in the tree — the
 * same gap that left the stylus disabled until session 14.
 *
 * ── The mapping, measured rather than assumed ────────────────────────────
 *
 * nvt_touch.ko's nvt_edge_reject_set() sends one byte, 0xBA..0xBD, selected by
 * the value written. Written on the device and read back:
 *
 *     0 -> Vertical Direction!(0 degree)            <- power-on default
 *     1 -> Right Up Direction!(90 degree)
 *     2 -> Vertical reverse Direction!(180 degree)
 *     3 -> Left Up Direction!(270 degree)
 *     4 -> Not Support!
 *
 * So the node's domain is exactly Surface.ROTATION_0..270, in the same order and
 * with the same numbering. Display.getRotation() can be passed through
 * unchanged, and 4 is rejected by the driver rather than misinterpreted.
 *
 * ── Why it goes through a property instead of writing the node ───────────
 *
 * /proc/panel_direction is labelled proc_lenovo_touch, a VENDOR type. This app
 * runs in system_app, a core domain, and the rest of this tree's sepolicy is
 * careful not to cross that boundary — the same reason init.malbec.rc, not this
 * app, owns the /proc/support_pen and /proc/HighReportRate writes. So this class
 * publishes the rotation and init.malbec.rc does the write, exactly as the
 * device-mode pair already works.
 *
 * `sys.` rather than `persist.sys.`: rotation is not worth a flash write every
 * time the tablet is turned over, and it has no meaning across a reboot — the
 * driver powers up at 0 and the first sync after boot corrects it. `sys.` is
 * u:object_r:system_prop:s0 (system/sepolicy/private/property_contexts:23), the
 * same type as persist.sys., so this needs no new sepolicy at all.
 *
 * ── Why DisplayListener and not OrientationEventListener ─────────────────
 *
 * OrientationEventListener reports the *sensor*, which moves while the display
 * is still and is throttled to nothing when rotation is locked. What the
 * controller needs is the display's actual rotation, which is what
 * onDisplayChanged reports and what the window manager acted on.
 *
 * onDisplayChanged is chatty — it also fires for refresh-rate votes, which this
 * device changes several times a minute — so the rotation is compared before
 * publishing. Cheap by construction: a getRotation() and an int compare.
 */
class PanelDirectionController(
    private val context: Context,
    private val handler: Handler,
) : DisplayManager.DisplayListener {

    private companion object {
        const val TAG = "${Constants.TAG}/PanelDir"
    }

    private var published = -1

    fun start() {
        context.getSystemService(DisplayManager::class.java)
            ?.registerDisplayListener(this, handler)
        // The driver powers up at 0 degrees and nothing has corrected it yet, so
        // the first sync is not an optimisation, it is the one that matters.
        sync()
    }

    fun stop() {
        context.getSystemService(DisplayManager::class.java)
            ?.unregisterDisplayListener(this)
    }

    override fun onDisplayAdded(displayId: Int) = Unit

    override fun onDisplayRemoved(displayId: Int) = Unit

    override fun onDisplayChanged(displayId: Int) {
        if (displayId == Display.DEFAULT_DISPLAY) sync()
    }

    private fun sync() {
        val rotation = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?.rotation
            ?: return
        if (rotation == published) return
        // Guard the domain here rather than trusting the driver's "Not Support!"
        // branch: an out-of-range value would leave the controller on whatever it
        // had, silently, and this is the only place that knows what was intended.
        if (rotation !in 0..3) {
            Log.w(TAG, "ignoring out-of-range rotation $rotation")
            return
        }
        published = rotation
        // SystemProperties.set throws rather than returning a failure
        // (android_os_SystemProperties.cpp:183-198). Nothing here is worth taking
        // the process down for; a wrong edge-rejection band is a nuisance, a dead
        // MalbecParts also costs the pen buttons.
        runCatching { SystemProperties.set(Constants.PROP_PANEL_DIRECTION, rotation.toString()) }
            .onSuccess { Log.i(TAG, "panel direction -> $rotation (${rotation * 90} degrees)") }
            .onFailure { Log.e(TAG, "could not publish ${Constants.PROP_PANEL_DIRECTION}", it) }
    }
}
