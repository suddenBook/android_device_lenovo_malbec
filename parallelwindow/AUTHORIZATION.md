# Rule-corpus authorization record

Two corpora are vendored under `upstream/`. They have different provenance and
are recorded separately.

## 1. `upstream/hyperos/` — `sothx/mipad-magic-window` 3.01.48

On 2026-08-11, the device owner confirmed that the parties had signed a
cross-licensing agreement covering both AI-assisted processing of this rule
corpus and redistribution as part of the owner's ROM build.

The signed agreement itself is private and remains in the owner's custody. This
file records the authorization asserted for this repository; it does not quote,
extend, or invent terms from that agreement.

The authorized upstream inputs are pinned byte-for-byte in
`hyperos_source_lock.json`. Changes of upstream tag, release asset, input file,
or intended distribution require a new authorization and source-lock review.

## 2. `upstream/zui/` — Lenovo's own corpus for this tablet

`res/raw/embedding_config.json`, extracted from
`system/system/framework/framework-res.apk` of the **TB390FU factory image this
port is built from** (`ZUI_18.0.10.335_260618_ROW`). Pinned byte-for-byte in
`local_source_lock.json`, including the SHA-256 of the containing APK.

The owner's position, recorded here on 2026-08-12 at their direction:

* it is a configuration file from the firmware shipped on the owner's own
  device, and this entire port is derived from that firmware — the vendor blob
  list, the VINTF fragments and the kernel headers all come from the same
  images;
* it is **data, not code**: 288 rows of package and activity names describing
  which third-party apps Lenovo lays out as two panes. It contains no Lenovo
  implementation, and the engine that reads it here is AOSP's;
* only the 77 packages named in `local_additions.json` are read from it. The
  other 211 already come from the 3.01.48 conversion and are untouched;
* it is vendored rather than referenced so `import_hyperos_rules.py --check`
  can prove the product is reproducible from a fresh checkout, which is the
  property that makes the whole corpus auditable.

As with §1, a different firmware version means a new source-lock review — the
digest pin is what forces that, and the importer refuses to run if it fails.

⚠️ Neither corpus is a golden rule. `README.md` records every place this port
departs from what a vendor wrote, and `hyperos_import_audit.json` counts them.
