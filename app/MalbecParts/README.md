# MalbecParts

The device's own settings surface. One platform-signed app, injected into
Settings through Settings' own extension points.

**It contains no ported Lenovo code and requires no patch outside this device
tree.** That is not a nice-to-have; it is the constraint the whole design is
built around, and every decision below follows from it.

---

## What it does, and why each part needs an app at all

| Feature | What AOSP already has | What was missing |
|---|---|---|
| **Pen button actions** | `InputManager#addCustomInputGesture` — per-user, persisted by the framework in `/data/system/users/<id>/input_gestures.xml`, 84 bindable system actions | No UI binds a *single key*. The only AOSP screen that calls it is Settings' touchpad three-finger-tap page. |
| **Keyboard app keys** | Same mechanism | Same, plus the two keys were **structurally dead** — see below |
| **Out-of-range alert** | `BluetoothHidHost` connection-state broadcasts, `Vibrator`, `NotificationManager` | No "you left your accessory behind" concept anywhere in AOSP. Fast Pair / Find My Device need accessory-side provisioning this pen does not have. |
| **Touch mode** | `Settings.System.peak_refresh_rate` | The panel rate and the touch controller's scan mode are one hardware knob; nothing pairs them |
| **Native stylus prefs** | `StylusDevicesController` — handwriting, ignore side button, pointer icon, default notes | Only shown when `METADATA_DEVICE_TYPE == DEVICE_TYPE_STYLUS`, which nothing sets for this pen. **Two lines fix that.** |

---

## The load-bearing facts

### The keyboard's two app keys did nothing at all

Not "were unbound" — **could not be bound**. They were mapped to
`KEYCODE_MACRO_1` / `MACRO_2`, and `PhoneWindowManager.java:5124-5128` clears
`ACTION_PASS_TO_USER` for `MACRO_1..4` in `interceptKeyBeforeQueueing` — the
*queueing* stage. `InputDispatcher.cpp:1860-1873` then skips the whole
`interceptKeyBeforeDispatching` stage when that flag is absent, and that stage is
the only place custom input gestures are evaluated
(`KeyGestureController.java:1057-1070`).

So no amount of app work could have made MACRO keys configurable. The fix is in
the keylayout: `F19`/`F20`, which `PhoneWindowManager.java:5130-5148` passes
through because `com.android.hardware.input.enable_new_25q2_keycodes` is
`ENABLED`. Confirmed on the device rather than inferred:

```
$ adb shell device_config list input
com.android.hardware.input.enable_new_25q2_keycodes=true
com.android.hardware.input.enable_customizable_input_gestures=true
```

### The action catalogue is curated, not generated

`KeyGestureController.handleKeyGesture` (`:1223-1233`) looks the gesture type up
in `mSupportedKeyGestureToPidMap` and, if nobody registered a handler, logs
`"Key gesture: N is not supported"` and **drops it silently**. So offering all 84
`KEY_GESTURE_TYPE_*` values would ship a menu full of entries that do nothing.

`GestureAction.CATALOGUE` contains only types read off the running device:

```
$ adb shell dumpsys input | grep -A2 mSupportedKeyGestures
mSupportedKeyGestures = [1,2,3,4,5,6,7,8,10,11,12,13,14,15,16,17,21,22,23,25,
                         27,28,31,32,51,52,53,54,55,56,57,59,60,61,62,64,65,66,
                         67,68,69,70,71,72,75,76,77,78,80,81,82,84]
```

Notably **absent**, and therefore not offered: `33 OPEN_NOTES` (SystemUI only
registers it when a notes role holder exists), `79`, `83`, `63`, `73`.
"Open notes" is still in the menu, but implemented as
`LAUNCH_APPLICATION + RoleData("android.app.role.NOTES")`, which *is* registered
and resolves the same app (`ModifierShortcutManager.java:166-167`).

**If you add an entry, re-read that dump first.**

### The private gesture types are out of AOSP's range on purpose

`Constants.GESTURE_TYPE_*` are `0x4D42_00nn`. Three facts make that safe and
squatting on an unused AOSP value unsafe:

- `InputGestureManager.addCustomInputGesture` (`:413-435`) validates the
  *trigger*, never the gesture type. Any `int` is accepted.
- `InputGesturePersistedData` stores the type as a plain int attribute, so an
  unknown value survives a reboot.
- Every switch on the type has a safe default:
  `keyGestureTypeToLogEvent:611` → `LOG_EVENT_UNSPECIFIED`,
  `keyGestureTypeToString:851` → the hex,
  `isVisibleBackgrounduserAllowedGesture:250-262` → `true`.

AOSP's enum is a dense range ending at 84 and grows upward, so `0x4D420001`
cannot collide. `MEDIA_KEY` (38) and `SYSTEM_NAVIGATION` (35) are currently
unclaimed and would work today — but they would make `dumpsys input` lie about
what is registered, and they would break the day something else claims them.

### The "smart remote" classifies apps by category, not by a package list

Stock decides which bucket the foreground app is in from five hardcoded
`framework-res` string arrays naming about forty specific apps — WPS, iQiyi,
Youku, Bilibili, Douyin, Kuaishou, NetEase Music, Kugou. Copying that would mean
this ROM's stylus works with those apps and not with Kindle or Keynote.

`ApplicationInfo.category` is the AOSP-native answer to the same question, set by
each app in its own manifest. `CATEGORY_PRODUCTIVITY` → slide control,
`CATEGORY_NEWS` → page control, everything else → media keys, which is also the
fallback stock uses for its "other" state. Anything actually playing wins
regardless (`AudioManager.isMusicActive`).

Media keys go through `AudioManager.dispatchMediaKeyEvent`, not the input
pipeline — that routes to whichever session holds audio focus even when it is not
the foreground app, which is the entire point of a remote.

### The app never writes a vendor node

`/proc/HighReportRate` and `/proc/support_pen` are labelled `proc_lenovo_touch` /
`proc_lenovo_pen` — vendor types. Granting a core domain write access to them
would be a Treble boundary crossing, and would need
`BOARD_PLAT_PRIVATE_SEPOLICY_DIR`, which this device does not otherwise have.

Instead the app sets `persist.sys.malbec.touch_mode` and `init.malbec.rc` does
the write. That prefix is already `u:object_r:system_prop:s0`
(`system/sepolicy/private/property_contexts:79`) and `system_app` already holds
`set_prop` for it (`private/system_app.te:43`), so **this adds no policy at all**
beyond one `allow vendor_init proc_lenovo_touch` line that mirrors the
`proc_lenovo_pen` grant already there.

### The refresh-rate observer is not the "automatic switching" the owner rejected

`PenModeController.RefreshRateObserver` watches `Settings.System.peak_refresh_rate`
and makes the touch controller follow it. Without it, a user who picks 144 Hz from
**Settings → Display → Refresh rate** — a list built at runtime from
`Display.getSupportedModes()` (`RefreshRateUtils.java:45-49`), which no RRO can
filter — loses the stylus completely, with no indication of why.

Nothing here watches the pen, samples anything, or decides on the user's behalf.
It is one callback on one settings write, zero cost when nothing changes.

---

## Why it looks like Settings rather than beside it

Modelled on `packages/apps/DolbyAtmos`, the precedent this project already
accepted: `CollapsingToolbarBaseActivity` + `Theme.SubSettingsBase.Expressive` +
`SettingsBasePreferenceFragment` + SettingsLib widgets. Same collapsing title,
same typography, same dynamic palette, no chrome of its own. The action picker is
a `SelectorWithWidgetPreference` radio list, which is what AOSP itself uses for
the same question in `TouchpadThreeFingerTapSelector.java:81-89`.

Entry points, both free:

| Screen | Mechanism | Settings source change |
|---|---|---|
| Stylus and keyboard | `com.android.settings.action.IA_SETTINGS` + `meta-data com.android.settings.category = …ia.connect` → **Connected devices** | none |
| Advanced keyboard settings | the `RemotePreference` AOSP already ships at `physical_keyboard_settings.xml:83-89`, action `org.lineageos.settings.device.ADVANCED_KEYBOARD_SETTINGS`, auto-hidden when nothing answers | none |

---

## Not persistent

`DolbyAtmos` sets `android:persistent="true"` and that is right for an audio
effect that must survive every process death. Pinning a process into memory
forever for three callbacks is not. `START_STICKY` + `BootCompletedReceiver` +
a `sync()` from each settings screen covers it.

---

## What still has to be measured on the device

The six gesture → HID-usage assignments come from stock's own labels for the same
usages, which is a strong hint and not proof. See
`work/analysis/S16-pen-keyboard-probes.md` — it is a 10-minute checklist, and
correcting a wrong assignment is one line in the keylayout plus one string.

The one that matters most: after flashing, `dumpsys input` on the
`Lenovo Tab Pen Plus Consumer Control` node **must still read
`Classes: KEYBOARD | EXTERNAL`**. If `EXTERNAL_STYLUS` appears there, session
13's 50-minute phantom-side-button bug is back and the keylayout is wrong.
