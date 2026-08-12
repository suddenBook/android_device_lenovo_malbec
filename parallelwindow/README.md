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
descends from. Picking it means a rule file lifted from stock loads verbatim.
The product file is a deterministic conversion of the `sothx/mipad-magic-window`
`3.01.48` release corpus. The owner confirmed a signed cross-licensing agreement
covering AI-assisted processing and ROM redistribution; the private agreement is
not copied here. See `AUTHORIZATION.md`, `hyperos_source_lock.json`, and the
exact vendored inputs in `upstream/3.01.48/`.

⚠️ **That is a community compatibility module, NOT the ruleset a HyperOS device
ships.** This file used to call it "the official HyperOS release corpus", which
was wrong and is corrected here. Measured against a real
`/product/etc/embedded_rules_list.xml` unpacked from HyperOS 3.1 on a Xiaomi
Pad 8:

| | firmware | what we vendor |
|---|---:|---:|
| rows | 1,946 | 8,046 |
| `splitPairRule` | 89 | 2,040 |
| rows with only `name` | 1,305 | 0 |
| `skipSelfAdaptive` | 1 | **8,046 — every row** |

As a *package list* ours is a near-perfect superset (1,942 of the firmware's
1,946, plus 6,082 more). As a *rule set* it is genuinely enhanced rather than
invented: of the shared packages, 1,686 `splitPairRule` values are identical to
the firmware's, 207 were added by the community, 26 changed, 23 dropped. So it
is a defensible thing to ship — it is just not Xiaomi's, and four packages the
firmware has (`com.coze.space` among them) are missing from it, which means the
firmware is also *newer*.

Full comparison and method: `work/notes/parallel-window-corpus-provenance.md`.

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

      // Split on entry instead of lazily. More than one relation is legal;
      // Taobao's official rule has two. Legacy mainPage/defaultRelate remains
      // accepted as a single-relation compatibility spelling.
      "placeholderPairs": [
        {
          "from": "com.example.app.MainActivity",
          "to": "com.example.app.PlaceholderActivity"
        }
      ],

      // Must span the whole task: scanners, capture, payment, login, video.
      // Becomes ActivityRule(alwaysExpand=true), so the split underneath
      // survives and is still there when the activity finishes.
      "forceFullscreenPages": [ "com.example.app.LoginActivity" ],

      // Trampolines: splash screens, routers, forwarding activities. Neither
      // triggers a split nor occupies a pane. No stock rule type can express
      // this, so it is handled inside the match predicate.
      "transActivities": [ "com.example.app.SplashActivity" ],

      // ── presentation ──────────────────────────────────────────────────────
      // Effective HyperOS divider: raw isShowDivider AND scaleMode == 0.
      "showEmbeddingDivider": true,     // AOSP's draggable divider (vendor API 6+)
      "dividerDraggingToFullscreenAllowed": true, // raw supportFullSize
      "splitRatio":            0.5,     // HyperOS primary fraction default
      "minWidthDp":            600,     // HyperOS metrics default
      "minSmallestWidthDp":    600,
      "clearTop":              true,
      "finishPrimaryWithSecondary": 0,
      "finishSecondaryWithPrimary": 2,

      // ── imported relaunch metadata; intentionally not applied ───────────
      "suppressRelaunch": true,
      "forceRelaunch":  [ "com.example.app.CanvasActivity" ],
      "limitRelaunch":  [ "com.example.app.ListActivity" ],

      // `enabled=false` removes the rule. `defaultEnabled=false` keeps it
      // eligible and visible in MalbecParts, but initially switched off.
      // HyperOS embedded_setting_config.xml is authoritative. In an existing
      // row only literal embeddedEnable=true is on; when there is no row,
      // rules XML defaultSettings is the fallback.
      "enabled": true,
      "defaultEnabled": false
    }
  ]
}
```

For a HyperOS row without `splitPairRule`, the generated entry contains
`"autoPrimary": true` instead of `activityPairs`. The process latches its first
eligible activity as primary and routes later launches from it. Generated rules
never carry both forms.

⚠️ **`autoPrimary` is this importer's invention, not upstream's, and where
nothing guards it the entry ships default-disabled (#81).** A HyperOS row with
no `splitPairRule` says the package is embeddable and says *nothing* about how;
HyperOS covers that with engine defaults recorded below as unsupported. "The
first activity the process creates is the primary, forever" is our substitute
for those defaults — and it then inherits `finishSecondaryWithPrimary = 2`
(`FINISH_ADJACENT`), which really is upstream's, materialized onto every row.
Composed, on the very common shape of a splash that finishes itself:

    splash is the first activity   -> becomes the permanent primary
    splash starts the real main    -> pair matches, split forms
    splash finishes itself         -> FINISH_ADJACENT finishes the secondary
                                      with it, and the app exits

`transActivities` and `forceFullscreenPages` are the two things that keep a
splash out of contention. A row with `autoPrimary`, neither of those, and a
finish behaviour that can take the secondary down is therefore **imported in
full, enableable from `wm parallel-window` or the MalbecParts row, and off until
someone asks for it.** 4,834 rows are gated this way; six more were already
disabled upstream and are left alone.

★ **This is not a precaution any more — it was reproduced.** `cn.com.sina.finance`
is a gated row with exactly this shape. Enabled by hand on the 2026-08-12 build,
it closes itself **1.07 s** after launch: 29 activity references at t+1s, **0 at
t+2s**, launcher back on top, no FATAL and no ANR — it does not crash, it exits.

    04.875  ParallelWindow: pair? from=LoadingActivity to=MainActivity2 -> MATCH
    05.530  Remove task fragment: removeLastChild LoadingActivity t-1 f
    05.552  Remove task fragment: removeLastChild MainActivity2   t-1 f

22 ms between the two removals, and the splash is literally named
`LoadingActivity`. Trace:
`work/session-30-build-repair-20260812/evidence/81-confirmed-sina-finance.txt`.

The distinction this draws is the one the data draws: the 2,028 packages where
upstream said how to split are automatic, and the ones where we guessed are
opt-in. Rows whose `finishSecondaryWithPrimary` is `0` are deliberately *not*
gated — there the same wrong guess is inert, because the splash finishes, the
secondary survives, and `autoPrimary` is simply left pointing at a dead class.

This is the only place the importer departs from materializing exactly what
upstream released. It changes the DEFAULT only — never whether a rule exists,
never a routing value — and every package it touches is listed in
`hyperos_import_audit.json` under `unguardedAutoPrimaryDefaultDisabled`.

An item in `placeholderPairs` may carry `"waitForContent": true`. It delays the
placeholder until the primary activity has attached content. The importer emits
this only for HyperOS's exact Taobao
`welcome.Welcome → MagicWindowActivity` relation, whose stock predicate checks
that `android.R.id.content` has a child; the second Taobao placeholder and every
other relation use the ordinary predicate.

Keys not listed above are ignored. Booleans may be JSON booleans or the strings
`"true"` / `"false"` — Lenovo's corpus writes every one of them as a string, so a
reader that only accepted real booleans would read every one as absent. Class
names may be fully qualified or use the `.Foo` shorthand. The importer also
normalizes Android's `/fully.qualified`, `package/.Relative`, and
`package/fully.qualified` flattened-component spellings. Any key beginning with
`_` is metadata.

**The parser is lenient on purpose.** Every corpus this will be fed is hand-edited
at a scale where hand-editing goes wrong — measured: 28 duplicate-attribute
elements and four misspelt attribute names in Xiaomi's, a `forceFullScreenPages`
typo costing three packages their rule in Lenovo's, 83 malformed JSON bodies in
OPPO's of which a strict reader silently drops 69 working rules. So a malformed
JSON entry is skipped with a log rather than failing the file, a duplicate name
lets the last classifiable JSON entry win, and unknown keys are ignored. The
source importer has a deliberately stricter duplicate boundary: HyperOS inserts
each XML row into its package map unconditionally, so it selects the final XML
row before conversion and does not fall back if that row is malformed. The
locked release has 22 duplicate rows and none of their final rows is malformed.

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

The parser and parcel retain the fields so imported vendor data is not lost, but
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
adb shell wm parallel-window master off
adb shell wm parallel-window disable --user 0 com.example.app
adb shell wm parallel-window reset --user 0 com.example.app  # use rule default again
```

The shell and **Settings > Apps > Parallel windows** page in MalbecParts are two
projections of the same hidden per-user `Settings.Secure` master and per-package
state; MalbecParts does not persist a second copy. The choices survive a reboot.
Packages not covered by the loaded rule snapshot have no UI or disable path.
`status` reports the selected user's master, installed eligible count and
package-preference-disabled subset.

Every app process receives one immutable decision on its first rule lookup, and
the non-resizeable server gate latches that same package decision. Consequently
changing rules, the master, or a package preference never leaves the two halves
of an already-running process disagreeing: both retain the old snapshot. The
change applies when that package next starts. The UI does not stop apps; when
testing from the shell, force-stop and relaunch the affected package explicitly.

## Provenance and deterministic regeneration

`embedding_config.json` is **release-only**: every row comes from the pinned
`3.01.48` release, there is no local curated prefix, and no special priority for
Taobao, Weibo, Contacts, Etar, or Glimpse. ("Official-only" was the old wording
and overstated it — see the provenance warning above; the release is a community
module's, not Xiaomi's.) The release's
last package row is authoritative.

The locked source has 8,046 rows / 8,024 unique packages. Exactly 1,056 final
rows carry `fullRule`; HyperOS uses those for its fullscreen/orientation mode,
not Activity Embedding, so they are recorded and excluded. The separate
`fixed_orientation_list.xml` is likewise not an input. A
`fixedOrientationEnable=true` value in the settings file does **not** erase an
existing non-full embedding row: the five such packages remain eligible but
default-disabled, so the user can explicitly enable them. The resulting product
contains 6,968 embedding packages. **Upstream's own defaults are 6,952 enabled
and 16 disabled**; after the unguarded-`autoPrimary` gate described above
default-disables 4,834 more, the shipped product is **2,118 enabled and 4,850
disabled by default**. Both numbers stay pinned by the golden test, and the
release figure remains recoverable as
`default_disabled - imported_auto_primary_default_disabled = 4850 - 4834 = 16`.
HyperOS supplies missing presentation fields in its app-side extension jar as
`clearTop=true`, `finishPrimaryWithSecondary=0`, and
`finishSecondaryWithPrimary=2`. The importer writes those effective values into
every output row. Five packages explicitly override `clearTop` to false and ten
override `finishSecondaryWithPrimary` to 0; primary finish remains 0 throughout.
Thus behavior does not depend on target-parser defaults.

The same rule applies to metrics: HyperOS defaults to a 0.5 split ratio and
600dp minimum width / smallest width. All 6,968 rows materialize those values;
three source rows override minimum width to 900dp and 298 override the ratio.
This intentionally differs from the legacy Malbec JSON defaults of 0.35 and
840/600, which remain only for old override files that omit these keys.

Divider presentation is also an effective value rather than a direct rename.
HyperOS shows it only when `isShowDivider=true` and `scaleMode` is absent/zero,
yielding 5,504 shown and 1,464 hidden rules. `supportFullSize` is materialized as
`dividerDraggingToFullscreenAllowed`; among shown dividers it is true for 5,480
and false for 24. Other vendor scaling behavior remains unsupported and its raw
`scaleMode` value stays in the audit with the mapped divider side effect noted.

The final corpus has 2,028 explicit-pair packages and 3,750 usable relationships.
Two official tokens—one each in `com.boohee.box` and `com.kurogame.kjq`—contain
the unambiguous typo `A:*B:*` where a comma is missing. The importer
deterministically recovers each into `A:*` and `B:*`, recording the original
token and both output relations in `recoveredPairTokens`. It does not silently
repair other malformed syntax; Booking.com's empty token remains an audited
drop.

Four other official tokens contain surrounding component whitespace that
HyperOS's exact class-name matching would not recognize: the second pair in
`com.zhongan.ibank`, the only pair in `dxwt.questionnaire.ui`, the second
`activityRule` item in `com.cmcc.cmvideo`, and the second transition in
`com.ygkj.chelaile.standard`. Their intended class names are unambiguous, so the
importer restores only those exact package/attribute/raw-token tuples and records
each in `recoveredWhitespaceTokens`. There is no general whitespace trimming;
any unreviewed whitespace token is dropped with its original value in the audit.

The `com.wzsykj.wei` source also contains a pair whose primary component is `*`.
HyperOS tests the primary pattern as a literal class-name substring, so that row
can never match there, while the target schema would interpret it as match-any.
The importer therefore drops and audits that one relation, retaining the
package's other three pairs and its placeholder.

This distinction is visible in two familiar packages:

- the final official Weibo row is `fullRule="nra:cr:rcr:nr:uc"`, so Weibo is
  absent from the embedding product;
- Taobao is imported exactly as released, default-disabled, with all four
  activity pairs, all eight transition entries, and both placeholder relations;
  its Welcome relation also carries the audited stock content-readiness check.

The rendered XML files are checked in for offline reproducibility and verified
before every conversion. Regenerate and verify with:

```sh
python3 parallelwindow/tools/import_hyperos_rules.py
python3 parallelwindow/tools/import_hyperos_rules.py --check
python3 -m unittest discover -v -s parallelwindow/tools/tests
```

`hyperos_import_audit.json` accounts for every duplicate, exclusion, malformed
token, deterministic repair, component normalization, ignored or fallback rule
default, unsupported attribute, and unsupported flag directive down to package,
source row, and raw value. The golden test independently pins the input,
product and full audit hashes, literal release counts, Taobao's exact mapping,
Weibo's exclusion, and the 8 MiB loader bound.

Corpus membership expresses upstream intent, not per-version device proof. The
MalbecParts page intersects these rules with packages installed for the selected
user; applications outside the loaded rule snapshot are neither displayed nor
modified.

## Known approximation boundaries

The conversion does not claim that AOSP Activity Embedding is Xiaomi's engine.
The source audit retains unsupported `middleRule`, `autoUiRule`, portrait,
process-compatibility, scaling, camera-preview, and flag semantics. In
particular, HyperOS also consults application-manifest portrait/orientation
state; that runtime gate cannot be reconstructed from this static corpus alone.
Relaunch metadata is transported but deliberately unenforced for the reasons
above. These are explicit approximation boundaries, not silently converted
fields.
