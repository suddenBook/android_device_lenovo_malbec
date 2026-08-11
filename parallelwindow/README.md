# Parallel window rules

Per-app routing that lets a phone-only app be laid out as two panes — list on the
left, detail on the right — without the app being modified or aware of it.

The engine is in `frameworks/base` and is device-agnostic. This directory is the
part that is not: which packages get the treatment, and how.

---

## What the engine does with this file

`ParallelWindowService` (system_server) parses it at `systemReady()` and keeps an
immutable snapshot. Two things read that snapshot:

| reader | question |
|---|---|
| `WindowOrganizerController#createTaskFragment` | may this non-resizeable owner activity be embedded anyway? |
| `ActivityTaskManagerService#getParallelWindowConfigForCaller` | what are this app process's own rules? |

The second is answered once per app process, from `ActivityThread`, which hands
the answer to that process's own stock `SplitController`. **The split itself is
built entirely by AOSP** — containers, transitions, divider, back navigation. The
only thing this device supplies is the rules and the permission to apply them.

An app remains authoritative over its own embedding policy. If it calls
`SplitController#setEmbeddingRules`, including with an empty set to opt out, the
late system injection is skipped. The framework registers its lifecycle callback
and activity monitor through non-virtual platform helpers so compatibility
wrappers cannot accidentally discard the callbacks the embedding controller
requires.

That division is not a design preference. `createTaskFragment` requires
`ownerTask.effectiveUid == ownerActivity.getUid() == Binder.getCallingUid()` with
no system-organizer bypass, so nothing in system_server can create a TaskFragment
inside a third-party app's task. The organizer has to be in the app's process,
and the only organizer there is AOSP's.

## Schema

The format is the one Lenovo ships in this tablet's own stock firmware
(`framework-res.apk` → `res/raw/embedding_config.json`), which is in turn the
Huawei `easygo` lineage that OPPO's `activityPairs` / `transActivities` also
descends from. Picking it means a rule file lifted from stock loads verbatim, and
that the much larger Xiaomi corpus converts into it mechanically.

```jsonc
{
  "EmbeddingConfigVersion": "1.0.0",
  "blocklist": [ "com.example.never" ],        // ours: hard off, whatever an entry says
  "packages": [
    {
      "name": "com.example.app",

      // ── routing ───────────────────────────────────────────────────────────
      // "when an activity matching `from` launches one matching `to`, the new
      // one goes to the second pane". `to` is "*" in ~90% of every vendor
      // corpus measured, and a missing `to` means the same thing.
      "activityPairs": [ { "from": "com.example.app.MainActivity", "to": "*" } ],

      // Split on entry instead of lazily: show `defaultRelate` in the empty
      // second pane as soon as `mainPage` appears. Both halves or neither.
      "mainPage":      "com.example.app.MainActivity",
      "defaultRelate": "com.example.app.PlaceholderActivity",

      // Must span the whole task: scanners, capture, payment, login, video.
      // Becomes ActivityRule(alwaysExpand=true), so the split underneath
      // survives and is still there when the activity finishes.
      "forceFullscreenPages": [ "com.example.app.LoginActivity" ],

      // Trampolines: splash screens, routers, forwarding activities. Neither
      // triggers a split nor occupies a pane. No stock rule type can express
      // this, so it is handled inside the match predicate.
      "transActivities": [ "com.example.app.SplashActivity" ],

      // ── presentation ──────────────────────────────────────────────────────
      "showEmbeddingDivider": "true",   // AOSP's draggable divider (vendor API 6+)
      "splitRatio":            0.35,    // primary pane fraction; default 0.35
      "minWidthDp":            840,     // task must be this wide to split
      "minSmallestWidthDp":    600,

      // ── imported relaunch metadata; intentionally not applied ───────────
      "suppressRelaunch": true,
      "forceRelaunch":  [ "com.example.app.CanvasActivity" ],
      "limitRelaunch":  [ "com.example.app.ListActivity" ],

      "enabled": "true"                 // ours: off without deleting the entry
    }
  ]
}
```

Keys not listed above are ignored. Booleans may be JSON booleans or the strings
`"true"` / `"false"` — Lenovo's corpus writes every one of them as a string, so a
reader that only accepted real booleans would read every one as absent. Class
names may be fully qualified or use the `.Foo` shorthand. Any key beginning with
`_` is a comment; the seed file uses `_why`.

**The parser is lenient on purpose.** Every corpus this will be fed is hand-edited
at a scale where hand-editing goes wrong — measured: 28 duplicate-attribute
elements and four misspelt attribute names in Xiaomi's, a `forceFullScreenPages`
typo costing three packages their rule in Lenovo's, 83 malformed JSON bodies in
OPPO's of which a strict reader silently drops 69 working rules. So a malformed
entry is skipped with a log rather than failing the file, a duplicate name lets
the last win, and unknown keys are ignored.

## Relaunch metadata is preserved, but not enforced

Forming or collapsing a split changes an activity's bounds, which surfaces as
`CONFIG_SCREEN_SIZE | CONFIG_SMALLEST_SCREEN_SIZE | CONFIG_SCREEN_LAYOUT |
CONFIG_ORIENTATION`. An activity that declares none of the matching
`android:configChanges` tokens — which is most phone-only apps, and all of the
ones this feature exists for — is **destroyed and recreated every single time the
split appears or goes away**, losing scroll position, form state and playback.
`CONFIG_WINDOW_CONFIGURATION` is `@hide` with no manifest token, so an app could
not have opted in even if it wanted to.

Vendor corpora carry `suppressRelaunch`, `forceRelaunch`, and `limitRelaunch`
because their embedding engines can associate a configuration change with a
specific split transition. This port currently cannot. Applying those fields in
`ActivityRecord#shouldRelaunchLocked` by package alone also suppresses legitimate
rotation, freeform-resize, display-move, and resource-density relaunches when no
split exists. That is a correctness bug, not a tuning trade-off.

The parser and parcel retain the fields so curated vendor data is not lost, but
normal Android relaunch handling remains in force until the engine has a
transition-scoped signal. Apps are expected to save and restore their ordinary
instance state across split-bound changes just as they do across rotation.

## Excluded regardless of what this file says

`ParallelWindowRules.NEVER_ELIGIBLE_PREFIXES` in the engine drops SystemUI,
Settings, the permission controller, the package installer, the credential
manager, DocumentsUI, the photo picker, the intent resolver, IMEs, launchers,
setup, and everything under `com.google.android.`. A rule file may *add*
exclusions with `blocklist`; it cannot remove one of those.

## Tuning without a build

`ParallelWindowService` reads
`/data/system/parallel_window/embedding_config.json` **first**. A structurally
valid file replaces the product rules wholesale, even when it intentionally
contains zero packages. An unreadable, oversized, malformed, or wrong-schema
override is rejected and the product file is tried instead. The bounded loader
accepts at most 8 MiB, so a privileged but accidental giant file cannot force an
unbounded system-server allocation.

```sh
adb push candidate.json /data/system/parallel_window/embedding_config.json
adb shell wm parallel-window reload
adb shell wm parallel-window status     # says which file is live
adb shell wm parallel-window disable com.example.app
```

Changing rules or using `enable` / `disable` for a package that is already
running does not affect its copied controller state: rules are read once per
process, at first activity launch. Force-stop and relaunch that package after
every change. Runtime disables last only until reboot.

## Provenance of the corpus

The first three entries ship on the device and keep the engine testable after a
wipe. The Weibo and Taobao entries are tied to the exact APK version, version
code, and SHA-256 recorded alongside each rule. Taobao's imported Xiaomi rule was
narrowed against its decoded manifest; two stale transition activities absent
from 10.65.0 were removed. Future imports must arrive in a clearly labelled
commit with source provenance and exact-device evidence.
