# MalbecParts

The device's own settings surface. One platform-signed app, injected into
Settings through Settings' own extension points.

**It contains no ported Lenovo code.** The hardware controls below use existing
AOSP APIs or device properties. The parallel-window page is the one explicit
exception to the older "no framework API" boundary: it is a UI for this ROM's
own permission-gated `IActivityTaskManager` control surface.

---

## What it does, and why each part needs an app at all

| Feature | What AOSP already has | What was missing |
|---|---|---|
| **Pen button actions** | `InputManager#addCustomInputGesture` — per-user, persisted by the framework in `/data/system/users/<id>/input_gestures.xml`, 84 bindable system actions | No UI binds a *single key*. The only AOSP screen that calls it is Settings' touchpad three-finger-tap page. |
| **Keyboard app keys** | Same mechanism | Same, plus the two keys were **structurally dead** — see below |
| **Out-of-range alert** | `BluetoothHidHost` connection-state broadcasts, `Vibrator`, `NotificationManager` | No "you left your accessory behind" concept anywhere in AOSP. Fast Pair / Find My Device need accessory-side provisioning this pen does not have. |
| **Touch mode** | `Settings.System.peak_refresh_rate` | The panel rate and the touch controller's scan mode are one hardware knob; nothing pairs them |
| **Native stylus prefs** | `StylusDevicesController` — handwriting, ignore side button, pointer icon, default notes | Only shown when `METADATA_DEVICE_TYPE == DEVICE_TYPE_STYLUS`, which nothing sets for this pen. **Two lines fix that.** |
| **Parallel windows** | This ROM's `IActivityTaskManager` parallel-window control API | A per-user master switch and per-installed-supported-app switches in Settings > Apps |

---

## Parallel-window controls are a projection, not a second settings store

`ParallelWindowSettingsActivity` is injected into **Settings > Apps** with the
standard `com.android.settings.action.IA_SETTINGS` mechanism. Its profile mode
is `all_profiles`: when a work profile exists, Settings shows the normal profile
chooser and launches MalbecParts as that user. Managed-profile provisioning
keeps the package because it has no `MAIN`/`LAUNCHER` activity and is not in a
disallowed-app overlay; `OverlayPackagesProvider` only removes launchable or
explicitly disallowed system apps (`getNonRequiredApps` / `getLaunchableApps`).
That is a source-backed condition, not an unconditional manifest guarantee: if
a future product overlay explicitly disallows MalbecParts, the package must be
added to `vendor_required_apps_managed_profile` or the work-profile entry will
not exist. Device regression should therefore verify `pm list packages --user
<work-user> org.lineageos.malbec.parts` whenever work-profile overlays change.

The page reads and writes only these platform APIs, guarded by the signature
permission `android.permission.MANAGE_PARALLEL_WINDOW`:

- `getParallelWindowControlState()`
- `setParallelWindowUserEnabled(boolean)`
- `setParallelWindowPackageEnabled(String, boolean)`

Every preference is `persistent="false"`: the UI widgets do not cache a second
copy. The framework persists the per-user master and package choices in hidden
`Settings.Secure` keys shared with the `wm parallel-window` shell surface. It
returns the installed intersection of this device's loaded parallel-window
rules. The page intersects that result with the current user's `PackageManager`
view once more before drawing rows. That second intersection uses one
`getInstalledApplications` query and an in-memory set; it never probes every
known package name one at a time. An app outside the device rule set therefore
has no row and no write path. A supported app whose rule default is off still
has a row; its switch is unchecked.

After a successful setter call, the page reads the complete state back and
reports success only when the requested value is in that projection. If an app
leaves the installed-and-supported intersection during the write, the refreshed
list is shown with an unconfirmed warning.

**A confirmed per-app change then closes that app** (session 33), because a rule
is latched per process and a running app cannot pick a new one up. This replaced
a dialog that said *"the app was not stopped"* and offered an App info shortcut
so the user could press Force stop themselves.

The mechanism is `ActivityManager#killBackgroundProcesses`, **not**
`forceStopPackage`: it does not set `FLAG_STOPPED`, so alarms, jobs, sync,
widgets and saved instance state all survive and the user returns to the screen
they left. The price is that it does nothing for an app above the background
cutoff — one in the foreground, visible, or holding a foreground service — and
says nothing when it doesn't. So the page measures the process list afterwards
and shows one of two different sentences: *closed, already in effect* or *takes
effect the next time it starts*. `APPLIED` is a claim that requires evidence;
everything else, including an unreadable process list, degrades to the weaker
statement, which is true either way.

**The master switch never closes anything.** It affects every eligible installed
app, and stopping all of them from a toggle is not defensible at any scale.

The pure list and write-confirmation boundaries are covered by the host module
`MalbecPartsParallelWindowModelTests`.

The complete control path has also been built, flashed and verified on the
current enforcing image: state survives reboot, master and supported-package
off/on controls work, installed unsupported apps remain absent and unaffected,
and an already-running process retains its previous decision until its next
start. This page projects the five-package shipped corpus; it does not imply that
the quarantined full HyperOS corpus was integrated.

---

## The load-bearing facts

### The keyboard's two app keys did nothing at all

Not "were unbound" — **could not be bound**. They were mapped to
`KEYCODE_MACRO_1` / `MACRO_2`, and `PhoneWindowManager.java:5594-5598` clears
`ACTION_PASS_TO_USER` for `MACRO_1..4` in `interceptKeyBeforeQueueing` — the
*queueing* stage. `InputDispatcher.cpp:1860-1873` then skips the whole
`interceptKeyBeforeDispatching` stage when that flag is absent, and that stage is
the only place custom input gestures are evaluated
(`KeyGestureController.java:1099-1113`).

So no amount of app work could have made MACRO keys configurable. The fix is in
the keylayout: `F19`/`F20`, which `PhoneWindowManager.java:5600-5618` passes
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
`Display.getSupportedModes()` (`RefreshRateSettingsUtils.java:43`), which no RRO can
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

Entry points, all through Settings' existing extension hooks:

| Screen | Mechanism | Settings source change |
|---|---|---|
| Stylus and keyboard | `com.android.settings.action.IA_SETTINGS` + `meta-data com.android.settings.category = …ia.connect` → **Connected devices** | none |
| Advanced keyboard settings | the `RemotePreference` AOSP already ships at `physical_keyboard_settings.xml:83-89`, action `org.lineageos.settings.device.ADVANCED_KEYBOARD_SETTINGS`, auto-hidden when nothing answers | none |
| Parallel windows | `com.android.settings.action.IA_SETTINGS` + `meta-data com.android.settings.category = …ia.apps` → **Apps** | none in Settings; the control API lives in this ROM's framework |

---

## The MalbecParts process is not persistent

This section concerns process lifetime, not the `Settings.Secure`-backed
parallel-window choices above.

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
