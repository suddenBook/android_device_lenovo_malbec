# Locked upstream inputs

These two files are exact members of the `3.01.48` release asset named in
`../../hyperos_source_lock.json`:

- `embedded_rules_list.xml`: that release's Activity Embedding rules;
- `embedded_setting_config.xml`: that release's per-package defaults.

⚠️ **Whose release matters.** This is `sothx/mipad-magic-window`, a community
compatibility module — **not** the `/product/etc/embedded_rules_list.xml` a
HyperOS device actually ships. The two differ by more than 4x in row count
(8,046 vs 1,946) and this file carries `skipSelfAdaptive` on every row where the
firmware carries it once. Calling these "the deployed rules" was wrong; they are
a community superset that preserves most of the firmware's rules and adds
6,082 packages. `work/notes/parallel-window-corpus-provenance.md` has the
measurements.

They are vendored so a fresh checkout can regenerate the product JSON offline.
Do not edit them. The importer verifies their SHA-256 digests before parsing.
The release's separate fixed-orientation list selects a different display mode
and is intentionally not an importer input.
