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

    /*
     * ⚠️ HID usage 0x0c0604 (stock name PEN_PRESS_CLICK) is deliberately NOT
     * mapped, and F17 has been handed to the keyboard instead.
     *
     * It is not a gesture. Stock's own dispatcher proves it
     * (services.jar -> BluetoothPenInputPolicy.processStylusPenKeyEvent):
     *
     *     KEYCODE_PEN_PRESS_CLICK -> clickStatusType = 4
     *         if (down && !mIsStylusPenQuickCreateNote) {
     *             Log.d("Don't send stylus pen press click key event to screen");
     *             return false;
     *         }
     *     ...
     *     if (mRemoteControlEnable && clickStatusType != 4)   // 4 excluded
     *         processStylusPenRemoteControl(clickStatusType, down)
     *
     * It is the "button is being held" state, used only for hold-the-button-and-
     * tap-the-screen to start a note. Stock's own settings UI offers four
     * gestures, not five. The owner confirmed it on the device: it fires while
     * the button is held, so binding it collided with Press-and-hold, and the
     * note action it exists for has no default app here anyway.
     */

    /**
     * Not user-assignable. The pen sends HID usage 0x0c0605 when it is about to
     * drop the link on purpose (being switched off, firmware update). Stock
     * consumes it for exactly one thing: suppressing the out-of-range alert that
     * would otherwise fire a moment later. We do the same, so this key is bound
     * by the app itself and never appears in the picker.
     */
    const val KEY_PEN_BT_DISCONNECT = KeyEvent.KEYCODE_F18

    /*
     * Folio keyboard keys whose printed function PixelOS cannot provide as-is.
     * Each is routed to a spare F-key so it becomes bindable; the default is the
     * closest native equivalent of what the keycap says, and the owner can change
     * it like any other.
     *
     * Which physical key sends which HID usage is stock's own capability table,
     * work/unpacked/parts/system/usr/kb-type-config/lenovo_keyboard.xml entry
     * 17ef:62b2: support_touchpad_toggle, support_split_screen,
     * support_supper_connect, support_diy_app1, support_diy_app2, support_diy_ai.
     */
    const val KEY_KEYBOARD_TOUCHPAD = KeyEvent.KEYCODE_F17     // 0x0c0392  F7
    const val KEY_KEYBOARD_APP1 = KeyEvent.KEYCODE_F19         // 0x0c0398  right-hand key 1
    const val KEY_KEYBOARD_APP2 = KeyEvent.KEYCODE_F20         // 0x0c0399  right-hand key 2
    const val KEY_KEYBOARD_SPLIT = KeyEvent.KEYCODE_F21        // 0x0c0395  F10
    const val KEY_KEYBOARD_PC_LINK = KeyEvent.KEYCODE_F22      // 0x0c0397  F11
    const val KEY_KEYBOARD_SEARCH = KeyEvent.KEYCODE_F23       // 0x0c0393  Fn + right-hand key 1

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
    const val PREF_KEYBOARD_TOUCHPAD = "keyboard_touchpad"
    const val PREF_KEYBOARD_APP1 = "keyboard_app1"
    const val PREF_KEYBOARD_APP2 = "keyboard_app2"
    const val PREF_KEYBOARD_SPLIT = "keyboard_split"
    const val PREF_KEYBOARD_PC_LINK = "keyboard_pc_link"
    const val PREF_KEYBOARD_SEARCH = "keyboard_search"

    const val PREF_PEN_LOST_ALERT = "pen_lost_alert"
    const val PREF_PEN_LOST_WAKE = "pen_lost_wake"
    const val PREF_PEN_LAST_SEEN = "pen_last_seen"
    const val PREF_FOLIO_EVER_SEEN = "folio_ever_seen"

    const val PREF_TOUCH_MODE = "touch_mode"

    /** Category keys inside the preference XML, for show/hide. */
    const val PREF_CAT_PEN_BUTTONS = "cat_pen_buttons"
    const val PREF_CAT_KEYBOARD_KEYS = "cat_keyboard_keys"

    /**
     * Every configurable trigger, so a single loop can reconcile the whole set.
     * Order is the order the pen page shows them in.
     */
    val PEN_BUTTONS = listOf(
        PREF_PEN_ONE_CLICK to KEY_PEN_ONE_CLICK,
        PREF_PEN_TWO_CLICK to KEY_PEN_TWO_CLICK,
        PREF_PEN_THREE_CLICK to KEY_PEN_THREE_CLICK,
        PREF_PEN_LONG_CLICK to KEY_PEN_LONG_CLICK,
    )

    val KEYBOARD_KEYS = listOf(
        PREF_KEYBOARD_TOUCHPAD to KEY_KEYBOARD_TOUCHPAD,
        PREF_KEYBOARD_SPLIT to KEY_KEYBOARD_SPLIT,
        PREF_KEYBOARD_PC_LINK to KEY_KEYBOARD_PC_LINK,
        PREF_KEYBOARD_APP1 to KEY_KEYBOARD_APP1,
        PREF_KEYBOARD_APP2 to KEY_KEYBOARD_APP2,
        PREF_KEYBOARD_SEARCH to KEY_KEYBOARD_SEARCH,
    )

    /**
     * What a key does before the owner has ever opened the picker.
     *
     * Only the keys whose keycap already promises something get one: the
     * touchpad key toggles the touchpad, the split-screen key splits the screen.
     * The rest default to nothing, because guessing on the owner's behalf is
     * worse than an obviously unset row.
     */
    val DEFAULT_ACTIONS = mapOf(
        PREF_KEYBOARD_SPLIT to "split_left",
        PREF_KEYBOARD_APP1 to "assistant",
        PREF_KEYBOARD_APP2 to "all_apps",
        PREF_KEYBOARD_SEARCH to "search",
    )

    val ALL_TRIGGERS = PEN_BUTTONS + KEYBOARD_KEYS

    /**
     * The stylus's Bluetooth HID identity. Matched on VID/PID rather than name
     * so a localised or renamed device still resolves — the same approach
     * SystemUI's StylusManager.kt:121-150 takes.
     */
    const val PEN_VENDOR_ID = 0x17ef
    const val PEN_PRODUCT_ID = 0x617f

    /** The folio keyboard, same vendor. Its capability table is stock's
     *  system/usr/kb-type-config/lenovo_keyboard.xml entry 17ef:62b2. */
    const val KEYBOARD_PRODUCT_ID = 0x62b2

    /**
     * The device's two modes.
     *
     * Measured on this unit, 8 s per window, panel rate read back inside every
     * window (work/scripts/61-touch-rate-at-144.sh, raw evdev in
     * work/s18/touch-rate/):
     *
     *   panel  HighReportRate   finger      pen
     *   120    0                120 Hz      240 Hz  (Pen_ID 2;87 = model 2, 87 %)
     *   120    1                360 Hz      none
     *   144    0                185 Hz      none
     *   144    1                185 Hz      none
     *   144    1 + pen on       112 Hz      none    <- worst state on the device
     *
     * So:
     *
     *   DAILY: 120 Hz + support_pen 1 + HighReportRate 0 + thermal normal
     *   GAME:  120 Hz + support_pen 0 + HighReportRate 1 + thermal game
     *
     * ⚠️ Both modes are 120 Hz, and that is the finding, not an oversight. The
     * top finger report rate (~360 Hz) exists ONLY at 120 Hz: above it the
     * controller caps at ~185 Hz and HighReportRate stops doing anything at all.
     * So 144 Hz costs the stylus AND half the touch sampling to buy 24 frames,
     * which is why the refresh-rate picker is removed from Settings entirely
     * (overlay/SettingsOverlayMalbec). Stock's own no-pen state is 144 + HRR 1,
     * so stock never reaches 360 Hz either — 120 + HRR 1 is a corner ZUI never
     * enters.
     *
     * ⚠️ HighReportRate is a BOOLEAN; only the literal value 1 arms it. HRR=4 at
     * 120 Hz measured 120.5 Hz, indistinguishable from 0. This refutes session
     * 14's "0..4 swept, 1..4 all saturate at 349-366 Hz". The driver accepts one
     * hex digit and passes it through unclamped (nvt_high_report_rate_set @0x946c
     * → {0x76, v}); the firmware only recognises 1. There is nothing above 1.
     *
     * ⚠️ /proc/report_threshold ({0x75, v}, same unclamped path) does nothing
     * measurable: 0/1/4/0 at 120 Hz + HRR 1 gave 324.4 / 303.1 / 347.1 / 346.5 Hz,
     * a spread smaller than the gap between the two identical thr=0 windows.
     * Leave it at 0.
     *
     * ⚠️ The WIRE VALUES stay "stylus" and "game" even though the UI says Daily
     * and Game. Two reasons, neither of them inertia:
     *
     *   * The value names the HARDWARE state of the digitizer — pen scanning on or
     *     off — which is exactly what init.malbec.rc keys off. The product name
     *     for that state should be free to change without touching init.
     *   * persist.sys.malbec.touch_mode survives an OTA. Renaming the value would
     *     leave an upgraded device holding "stylus", matching no init trigger at
     *     all: no /proc writes, and the pen silently dead until the user opened
     *     this app. A migration trigger could cover it, but that is permanent
     *     scaffolding for a cosmetic rename.
     */
    const val TOUCH_MODE_DAILY = "stylus"
    const val TOUCH_MODE_GAME = "game"

    /**
     * The one refresh rate either mode wants, and the ceiling the pen needs.
     * Both modes pin peak_refresh_rate here; see the table above for why there is
     * no second value.
     */
    const val REFRESH_RATE_PINNED = 120f

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
