# Parallel window rules

Per-app routing that lets a phone-only app be laid out as two panes — list on the
left, detail on the right — without the app being modified or aware of it.

The engine is in `frameworks/base` and is device-agnostic. This directory is the
part that is not: which packages get the treatment, and how.

---

## What the engine does with this file

`ParallelWindowService` (system_server) parses it at `systemReady()` and keeps an
immutable snapshot. Three things read that snapshot:

| reader | question |
|---|---|
| `WindowOrganizerController#createTaskFragment` | may this non-resizeable owner activity be embedded anyway? |
| `ActivityRecord#shouldRelaunchLocked` | should this activity be spared the relaunch a split transition causes? |
| `ActivityTaskManagerService#getParallelWindowConfigForCaller` | what are this app process's own rules? |

The third is answered once per app process, from `ActivityThread`, which hands
the answer to that process's own stock `SplitController`. **The split itself is
built entirely by AOSP** — containers, transitions, divider, back navigation. The
only thing this device supplies is the rules and the permission to apply them.

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

      // ── relaunch (read the section below before touching these) ──────────
      "suppressRelaunch": true,                              // default true
      "forceRelaunch":  [ "com.example.app.CanvasActivity" ],  // must relaunch
      "limitRelaunch":  [ "com.example.app.ListActivity" ],    // must not

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

## Relaunch is not a tuning knob

Forming or collapsing a split changes an activity's bounds, which surfaces as
`CONFIG_SCREEN_SIZE | CONFIG_SMALLEST_SCREEN_SIZE | CONFIG_SCREEN_LAYOUT |
CONFIG_ORIENTATION`. An activity that declares none of the matching
`android:configChanges` tokens — which is most phone-only apps, and all of the
ones this feature exists for — is **destroyed and recreated every single time the
split appears or goes away**, losing scroll position, form state and playback.
`CONFIG_WINDOW_CONFIGURATION` is `@hide` with no manifest token, so an app could
not have opted in even if it wanted to.

That is why `suppressRelaunch` defaults to `true` for anything with a rule, and
why `forceRelaunch` / `limitRelaunch` exist per activity. It is also the largest
hidden curation cost of this feature: Lenovo ships 194 activities that must not
relaunch and 53 that must, for a 288-package corpus, and OPPO sets its equivalent
on 2,944 of 3,022 entries.

## Excluded regardless of what this file says

`ParallelWindowRules.NEVER_ELIGIBLE_PREFIXES` in the engine drops SystemUI,
Settings, the permission controller, the package installer, the credential
manager, DocumentsUI, the photo picker, the intent resolver, IMEs, launchers,
setup, and everything under `com.google.android.`. A rule file may *add*
exclusions with `blocklist`; it cannot remove one of those.

## Tuning without a build

`ParallelWindowService` reads
`/data/system/parallel_window/embedding_config.json` **first**, and if it exists
it replaces this file wholesale. Same slot Lenovo's own implementation uses, for
the same reason — rule work is an edit-measure loop, and making each iteration
cost a build and a flash means it does not happen.

```sh
adb push candidate.json /data/system/parallel_window/embedding_config.json
adb shell wm parallel-window reload
adb shell wm parallel-window status     # says which file is live
adb shell wm parallel-window disable com.example.app
```

Changing rules for a package that is already running does not affect it: rules
are read once per process, at first activity launch. Force-stop it.

## Provenance of the corpus

The seed set in `embedding_config.json` is three apps that ship on this device,
written by hand so the engine can be exercised on a freshly wiped tablet — which
has no third-party apps at all. Anything larger comes from vendor corpora, and
whatever is imported must arrive in one clearly labelled commit that names where
it came from.
