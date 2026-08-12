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
      // Centred single pane: when an activity of this package is alone in its
      // task, it is shown at `middleRatio` of the task width, centred, instead
      // of stretched across the landscape tablet. Every imported row carries
      // this. See the MiddleRule section below.
      "middle":      true,
      "middleRatio": 0.5,               // optional; parser default is 0.5

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

## A row without `splitPairRule` gets no routing at all

4,917 of the 6,968 generated rows carry presentation attributes and **nothing
else**. That is upstream's shape, reproduced exactly, and it is the single most
important thing to understand about this corpus.

⚠️ **This file used to describe an `"autoPrimary": true` key here. It is gone
(#87), and so is the gate that shipped 4,834 of those rows default-disabled
(#81).** The history is worth keeping because the mistake is an easy one to make
again:

`autoPrimary` meant "the first activity the process creates is the primary,
forever", and this tree described it as *our substitute for HyperOS engine
defaults*. Unpacking a real HyperOS firmware settled that there are no such
defaults to substitute for. Their split predicate **requires** a `splitPairRule`,
and 1,305 of the firmware's 1,946 rows carry only a package name. A bare row says
the package is on the list. It says nothing whatsoever about how to split it.

The invention then inherited `finishSecondaryWithPrimary = 2` (`FINISH_ADJACENT`),
which really is upstream's, materialized onto every row. Composed, on the very
common shape of a splash that finishes itself:

    splash is the first activity   -> becomes the permanent primary
    splash starts the real main    -> pair matches, split forms
    splash finishes itself         -> FINISH_ADJACENT finishes the secondary
                                      with it, and the app exits

★ **That was reproduced on hardware, which is why the fix is removal and not
tuning.** `cn.com.sina.finance` is one of those rows. Enabled by hand on the
2026-08-12 build, it closed itself **1.07 s** after launch: 29 activity
references at t+1s, **0 at t+2s**, launcher back on top, no FATAL and no ANR —
it did not crash, it exited.

    04.875  ParallelWindow: pair? from=LoadingActivity to=MainActivity2 -> MATCH
    05.530  Remove task fragment: removeLastChild LoadingActivity t-1 f
    05.552  Remove task fragment: removeLastChild MainActivity2   t-1 f

22 ms between the two removals, and the splash is literally named
`LoadingActivity`. Trace:
`work/session-30-build-repair-20260812/evidence/81-confirmed-sina-finance.txt`.

So the importer materializes exactly what upstream released, with no departure
anywhere: 2,028 packages carry `activityPairs`, 23 more carry only
`placeholderPairs`, and the remaining 4,917 carry no routing. Defaults are once
again upstream's own, **6,952 enabled and 16 disabled**, because nothing in the
conversion decides a default any more.

## MiddleRule — what those 4,917 rows are for

AOSP has two presentations: two containers side by side, and one container
filling the task. HyperOS has a third, and it is the one most of this corpus
needs — **one container, narrower than the task, centred in it**.

`"middle": true` is on **every** imported row, and that is not a bulk edit: on
HyperOS the centring judgement runs for every package on the list and is decided
by geometry and by whether the activity is alone. The explicit `middleRule`
attribute — 36 of the source's 8,046 rows — *overrides* that judgement rather
than enrolling in it. Being on the list is the enrolment.

The rect is the middle `middleRatio` of the task width, full height. It is
**not applied**, and the container fills the task exactly as stock would, when:

* the task is portrait or square — the app already has a phone-shaped window;
* the ratio is degenerate;
* the pane's own aspect falls outside `(0.5, 1.0)`. This is HyperOS's own
  `ratioMatch` and it is the test that makes the feature mean something: it asks
  whether half of this screen is still roughly phone-proportioned. On this
  device's 1752 × 1095 dp landscape task a 0.5 pane is 876 × 1095 dp, i.e. 0.80.

`transActivities` and `forceFullscreenPages` opt an activity out — the first so
a splash still renders its launch image at full size, which is the same
exclusion HyperOS derives from `transitionRules`.

Centring and splitting compose rather than compete. A package with
`activityPairs` is centred while its primary is alone, expands into a two-pane
split when a pair matches, and returns to centred when the split collapses. A
package with no pairs — the overwhelming majority — is only ever centred.

`middleRatio` is deliberately **not** materialized into the rows. It has no
upstream counterpart; it is this port's own knob. The parser default is 0.5,
which is HyperOS's own fraction, and the `/data` override slot below can retune
it per package with no rebuild.

Two approximation boundaries, both audited rather than silently converted:

* the one source row whose `middleRule` names activities
  (`com.miui.hybrid`, ten launcher classes) becomes a whole-package `middle`,
  so **more** activities are centred there than upstream intended. It is a
  HyperOS system package that cannot be installed here;
* `flags: ignoreActivityBelowWhenJudgeMiddle` (16 rows) has no counterpart.
  HyperOS needs it because its judgement requires "nothing below me in the
  task", and apps that keep a junk activity underneath would never qualify.
  This port decides middle per *container* rather than per launch, so the
  condition the flag removes was never imposed. The directive stays in
  `unmappedFlagDirectives`.

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
contains 6,968 embedding packages, **6,952 enabled and 16 disabled by default —
which are upstream's own numbers**, because after #87 nothing in the conversion
decides a default. Both stay pinned by the golden test.
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
The source audit retains unsupported `autoUiRule`, portrait,
process-compatibility, scaling, camera-preview, and flag semantics — and
`middleRule`, which is now partially mapped and carries its side effect with it.
In
particular, HyperOS also consults application-manifest portrait/orientation
state; that runtime gate cannot be reconstructed from this static corpus alone.
Relaunch metadata is transported but deliberately unenforced for the reasons
above. These are explicit approximation boundaries, not silently converted
fields.
