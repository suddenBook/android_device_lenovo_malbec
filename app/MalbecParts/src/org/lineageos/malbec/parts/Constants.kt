/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.malbec.parts

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

    /*
     * ★ The full-screen key (F9 keycap), added session 23.
     *
     * ⚠️ This was HALF fixed and the half that was missing is the one a user can
     * see. OPEN-ISSUES #24 correctly diagnosed that the key's native
     * KEYCODE_FULLSCREEN reaches gesture 82, that 82 is registered only by
     * DesktopModeKeyGestureHandler, and that this device never constructs it —
     * and remapped the key to F24 in keylayout/Vendor_17ef_Product_62b2.kl:680,
     * whose comment says "the key now goes through MalbecParts' picker like every
     * other ZUI key". It did not: there was no F24 anywhere in this app, so the
     * key went from "does nothing via a dead gesture" to "does nothing via an
     * unhandled keycode". Measured on the device before the fix — `dumpsys input`
     * Custom Gestures for user 0 held F23, F21, F20, F19 and F18, and no F24.
     *
     * KeyEvent.LAST_KEYCODE == KEYCODE_F24, so this is the highest keycode that
     * exists; InputGestureData.Builder's bound check (InputGestureData.java:234)
     * accepts it, and it is the last free F-key on this device (the pen holds
     * F13-F16 and F18, this folio holds F17 and F19-F23). There is no spare left.
     *
     * No entry in DEFAULT_ACTIONS: Android 16 has no "make this window full
     * screen" action that works with desktop mode off, so there is nothing to
     * point it at that would not be another dead default. Per this file's own
     * policy, a key whose keycap promises something Android cannot do ships
     * unset and bindable.
     */
    const val KEY_KEYBOARD_FULLSCREEN = KeyEvent.KEYCODE_F24   // 0x0c0394  F9

    /**
     * Display rotation, 0..3, handed to init so it can write
     * /proc/panel_direction. See display/PanelDirectionController.kt for why it
     * goes through a property and why the value needs no translation.
     *
     * `sys.` rather than `persist.sys.`: rotation has no meaning across a
     * reboot (the driver powers up at 0 and the first sync corrects it), and
     * persisting it would mean a flash write every time the tablet is turned
     * over. Both prefixes are u:object_r:system_prop:s0, so this needs no new
     * sepolicy either way.
     */
    const val PROP_PANEL_DIRECTION = "sys.malbec.panel_dir"

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
    const val PREF_KEYBOARD_FULLSCREEN = "keyboard_fullscreen"

    const val PREF_PEN_LOST_ALERT = "pen_lost_alert"
    const val PREF_PEN_LOST_WAKE = "pen_lost_wake"
    const val PREF_PEN_LAST_SEEN = "pen_last_seen"
    const val PREF_FOLIO_EVER_SEEN = "folio_ever_seen"

    const val PREF_TOUCH_MODE = "touch_mode"
    const val PREF_GESTURE_WAKE = "gesture_wake"
    const val PREF_REFRESH_RATE = "refresh_rate"

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
        PREF_KEYBOARD_FULLSCREEN to KEY_KEYBOARD_FULLSCREEN,
    )

    /**
     * What a key does before the owner has ever opened the picker.
     *
     * Only the keys whose keycap promises something Android can actually DO get
     * one: the split-screen key splits the screen, and the two app keys plus the
     * search key have obvious GMS equivalents.
     *
     * ⚠️ CORRECTED session 25. This used to claim "the touchpad key toggles the
     * touchpad". It does not, and it cannot: `PREF_KEYBOARD_TOUCHPAD` is absent
     * from the map below, and Android 16's `KeyGestureEvent` has **no touchpad
     * toggle** among its 85 types (`TOGGLE_TASKBAR` is the nearest and is
     * unrelated). Confirmed on the running device — `dumpsys input`'s Custom
     * Gestures list has five entries for F23/F21/F20/F19/F18 and no F17.
     *
     * So the F7 keycap ships unbound and bindable, which is the same class as
     * `KEY_KEYBOARD_FULLSCREEN` and is handled the same way: a picker entry the
     * user can select and that then does nothing is worse than an absent one.
     * The rest default to nothing, because guessing on the owner's behalf is
     * worse than an obviously unset row.
     *
     * ⚠️ TWO OF THESE DEFAULTS DO NOTHING ON A GAPPS-LESS BUILD, and that is an
     * owner decision (session 23), not an oversight. Measured on the device:
     *
     *   cmd role get-role-holders android.app.role.ASSISTANT   -> empty
     *   settings get secure voice_interaction_service          -> empty
     *   cmd package query-activities -a android.intent.action.ASSIST
     *                                                          -> No activities found
     *   cmd package query-activities -a android.intent.action.WEB_SEARCH
     *                                                          -> No activities found
     *   (control: -a MAIN -c LAUNCHER -> 12 activities found)
     *
     * So `assistant` reaches PhoneWindowManager.launchAssistAction ->
     * SearchManager.launchAssist, which needs a live voice-interaction service,
     * and `search` reaches launchTargetSearchActivity, which (with
     * config_searchKeyTargetActivity unset) fires ACTION_WEB_SEARCH and catches
     * the ActivityNotFoundException. Both are silent no-ops today. Jelly declares
     * no WEB_SEARCH filter, so nothing on this ROM can take either.
     *
     * They are kept anyway because OPEN-ISSUES #32b makes full GMS the intended
     * configuration, and on that build both keycaps do exactly what they say.
     * The cost of being wrong in this direction is two keys that do nothing until
     * GApps arrive; the cost of the other direction is re-choosing a default that
     * was right all along. If GMS is dropped as a plan, the fix is to filter both
     * out of the picker in GestureAction.isAvailable() the way `notes` already is
     * — one `when` branch each, and they reappear by themselves if a handler is
     * ever installed.
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
     * The highest rate this device may ever run at — a CEILING, not a setpoint.
     *
     * Both modes need <= 120: Daily because the digitizer cannot see the pen above
     * it, Game because HighReportRate stops working above it and the finger rate
     * falls from 360 to 185 Hz. See the table above.
     *
     * ⚠️ Ceiling, not setpoint, and the distinction is the whole reason the
     * refresh-rate choice below can exist. peak_refresh_rate becomes
     * Vote.forPhysicalRefreshRates(0, peak) — an UPPER BOUND
     * (DisplayModeDirector.java:1202-1209). min_refresh_rate stays 0, so AOSP's
     * own idle and content-driven switching still runs underneath: measured 30 Hz
     * idle, 120 Hz while scrolling. Nothing here pins the panel to one rate.
     */
    const val REFRESH_RATE_MAX = 120f

    /**
     * What the user may choose as that ceiling, lowest first.
     *
     * 144 is absent and that is the finding, not an oversight — see the table
     * above. The AOSP picker in Settings > Display is switched off
     * (overlay/SettingsOverlayMalbec) because
     * PeakRefreshRateListPreferenceController.java:82-92 builds its list at
     * runtime from Display.getSupportedModes(), so no RRO can remove 144 from
     * it; a list with a trap in it is worse than no list. This is that list minus
     * the trap. (⚠️ `RefreshRateUtils.getRefreshRates()`, cited here before, does
     * not exist in LineageOS 23.2 — PixelOS residue, same as the one
     * SettingsOverlayMalbec already calls out. The mechanism was right.)
     *
     * 60 and 90 are here because they are worth real standby power on a 13" LCD
     * and losing them was the actual cost of switching the AOSP picker off.
     *
     * ⚠️ Battery Saver is NOT one of the writers here and must not be confused
     * with one. It caps the RENDER rate through a vote —
     * DisplayModeDirector.updateLowPowerModeSettingLocked:1116-1127 posts
     * Vote.forRenderFrameRates(0, 60) at PRIORITY_LOW_POWER_MODE_RENDER_RATE —
     * and never touches Settings.System. So it composes with whatever is chosen
     * here instead of fighting it, and the observer below never sees it.
     */
    val REFRESH_RATE_CHOICES = listOf(60f, 90f, 120f)

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

    /**
     * Double-tap-to-wake.
     *
     * `persist.sys.` rather than a SharedPreference for the same reason
     * [PROP_TOUCH_MODE] is: rootdir/etc/init.malbec.rc is the thing that writes
     * /proc/gesture_mode, init can only read properties, and one source of truth
     * is what stops the UI and the hardware disagreeing.
     *
     * ⚠️ ABSENT MEANS ON. init.malbec.rc writes `gesture_mode 1` unconditionally at
     * boot_completed and only overrides it to 0 when this property says `0`, which
     * mirrors how touch_mode's default (`stylus`) is the unconditional write and
     * `game` is the override. So the default lives in the rc file, not here, and
     * there is deliberately no build.prop default to disagree with it.
     */
    const val PROP_GESTURE_WAKE = "persist.sys.malbec.gesture_wake"
}
