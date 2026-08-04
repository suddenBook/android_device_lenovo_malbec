/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.malbec.parts

import android.view.KeyEvent

object Constants {

    const val TAG = "MalbecParts"

    /**
     * The F-keys the two keylayouts emit. See
     * device/lenovo/malbec/keylayout/Vendor_17ef_Product_617f.kl for the full
     * derivation of why F13..F24 and not STYLUS_BUTTON_* or MACRO_*.
     *
     * ⚠️ Custom input gestures are keyed on (keycode, modifierState) only, with
     * no device id (InputGestureManager.getCustomGestureForKeyEvent:499-513), so
     * the pen and the keyboard must never share one.
     */
    const val KEY_PEN_ONE_CLICK = KeyEvent.KEYCODE_F13
    const val KEY_PEN_TWO_CLICK = KeyEvent.KEYCODE_F14
    const val KEY_PEN_THREE_CLICK = KeyEvent.KEYCODE_F15
    const val KEY_PEN_LONG_CLICK = KeyEvent.KEYCODE_F16
    const val KEY_PEN_PRESS_CLICK = KeyEvent.KEYCODE_F17

    /**
     * Not user-assignable. The pen sends HID usage 0x0c0605 when it is about to
     * drop the link on purpose (being switched off, firmware update). Stock
     * consumes it for exactly one thing: suppressing the out-of-range alert that
     * would otherwise fire a moment later. We do the same, so this key is bound
     * by the app itself and never appears in the picker.
     */
    const val KEY_PEN_BT_DISCONNECT = KeyEvent.KEYCODE_F18

    const val KEY_KEYBOARD_APP1 = KeyEvent.KEYCODE_F19
    const val KEY_KEYBOARD_APP2 = KeyEvent.KEYCODE_F20

    /**
     * Private KeyGestureEvent types.
     *
     * These are NOT AOSP constants, deliberately. Three facts make an
     * out-of-range value the correct choice rather than squatting on an
     * AOSP one:
     *
     *  - InputGestureManager.addCustomInputGesture (:413-435) validates the
     *    *trigger* (blocklist, system shortcuts, modifier keys, system keys) and
     *    never the gesture type, so any int is accepted.
     *  - InputGesturePersistedData serialises the type as a plain int attribute
     *    (ATTR_KEY_GESTURE_TYPE), so an unknown value survives a reboot.
     *  - Every switch on the type has a safe default:
     *    keyGestureTypeToLogEvent :611 returns LOG_EVENT_UNSPECIFIED,
     *    keyGestureTypeToString :851 returns the hex, and
     *    isVisibleBackgrounduserAllowedGesture :250-262 returns true.
     *
     * AOSP's enum is a dense range currently ending at 84 and grows upward, so a
     * value up here can never collide. Squatting on MEDIA_KEY (38) or
     * SYSTEM_NAVIGATION (35) — both currently unclaimed — would work today but
     * would make `dumpsys input` lie about what is registered, and would break
     * the day something else claims them.
     */
    const val GESTURE_TYPE_SMART_REMOTE_PRIMARY = 0x4D42_0001
    const val GESTURE_TYPE_SMART_REMOTE_NEXT = 0x4D42_0002
    const val GESTURE_TYPE_SMART_REMOTE_PREVIOUS = 0x4D42_0003
    const val GESTURE_TYPE_CAMERA_SHUTTER = 0x4D42_0004
    const val GESTURE_TYPE_PEN_LINK_GOING_AWAY = 0x4D42_0005

    /** SharedPreferences keys. */
    const val PREF_PEN_ONE_CLICK = "pen_one_click"
    const val PREF_PEN_TWO_CLICK = "pen_two_click"
    const val PREF_PEN_THREE_CLICK = "pen_three_click"
    const val PREF_PEN_LONG_CLICK = "pen_long_click"
    const val PREF_PEN_PRESS_CLICK = "pen_press_click"
    const val PREF_KEYBOARD_APP1 = "keyboard_app1"
    const val PREF_KEYBOARD_APP2 = "keyboard_app2"

    const val PREF_PEN_LOST_ALERT = "pen_lost_alert"
    const val PREF_PEN_LOST_WAKE = "pen_lost_wake"
    const val PREF_PEN_LAST_SEEN = "pen_last_seen"

    const val PREF_TOUCH_MODE = "touch_mode"

    /** Category keys inside the preference XML, for show/hide. */
    const val PREF_CAT_PEN_BUTTONS = "cat_pen_buttons"

    /**
     * Every configurable trigger, so a single loop can reconcile the whole set.
     * Order is the order the pen page shows them in.
     */
    val PEN_BUTTONS = listOf(
        PREF_PEN_ONE_CLICK to KEY_PEN_ONE_CLICK,
        PREF_PEN_TWO_CLICK to KEY_PEN_TWO_CLICK,
        PREF_PEN_THREE_CLICK to KEY_PEN_THREE_CLICK,
        PREF_PEN_LONG_CLICK to KEY_PEN_LONG_CLICK,
        PREF_PEN_PRESS_CLICK to KEY_PEN_PRESS_CLICK,
    )

    val KEYBOARD_KEYS = listOf(
        PREF_KEYBOARD_APP1 to KEY_KEYBOARD_APP1,
        PREF_KEYBOARD_APP2 to KEY_KEYBOARD_APP2,
    )

    val ALL_TRIGGERS = PEN_BUTTONS + KEYBOARD_KEYS

    /**
     * The stylus's Bluetooth HID identity. Matched on VID/PID rather than name
     * so a localised or renamed device still resolves — the same approach
     * SystemUI's StylusManager.kt:121-150 takes.
     */
    const val PEN_VENDOR_ID = 0x17ef
    const val PEN_PRODUCT_ID = 0x617f

    /**
     * Touch controller / panel mode.
     *
     * The two knobs are NOT independent and must move together. Measured on this
     * unit, both directions, with the pen in the owner's hand:
     *
     *   peak_refresh_rate 120 -> 6746 pen events, 438 pressure samples;
     *                            finger reports at 120 Hz
     *   peak_refresh_rate 144 -> ZERO pen events; the controller cannot even
     *                            detect the pen, and /proc/Pen_ID falls back to
     *                            255;255
     *   HighReportRate 0      -> finger 120 Hz, pen 237 Hz
     *   HighReportRate 1      -> finger 360 Hz
     *
     * So there are exactly two coherent states, and they are the same two stock
     * uses (DisplayModeDirector.supportPen(true/false) in ZUI's patch):
     *
     *   STYLUS: 120 Hz panel + HighReportRate 0 + pen scanning on
     *   GAME:   144 Hz panel + HighReportRate 1 + pen scanning off
     *
     * ⚠️ The device ships in STYLUS. That is a deliberate default, not an
     * oversight: this tablet comes with a stylus, and a pen that produces no
     * events at all is not a trade against 1.4 ms of frame time. But GAME is a
     * real gain and not only for games — it triples the finger report rate,
     * which is the part an earlier round of this project understated.
     */
    const val TOUCH_MODE_STYLUS = "stylus"
    const val TOUCH_MODE_GAME = "game"

    const val REFRESH_RATE_STYLUS = 120f
    const val REFRESH_RATE_GAME = 144f

    /**
     * init.malbec.rc turns these into writes to /proc/HighReportRate and
     * /proc/support_pen. The app cannot write those nodes itself and should not:
     * they are labelled proc_lenovo_touch / proc_lenovo_pen, i.e. vendor types,
     * and handing a core domain write access to them would be exactly the Treble
     * boundary crossing the rest of this tree's sepolicy is careful to avoid.
     *
     * The `persist.sys.` prefix is not arbitrary — it is already
     * u:object_r:system_prop:s0 (system/sepolicy/private/property_contexts:79)
     * and system_app already holds set_prop for it
     * (private/system_app.te:43), so this needs no new policy at all.
     */
    const val PROP_TOUCH_MODE = "persist.sys.malbec.touch_mode"
}
