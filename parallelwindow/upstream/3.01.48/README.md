# Locked upstream inputs

These two files are exact members of the official `3.01.48` release asset named
in `../../hyperos_source_lock.json`:

- `embedded_rules_list.xml`: the deployed Activity Embedding rules;
- `embedded_setting_config.xml`: the deployed per-package defaults.

They are vendored so a fresh checkout can regenerate the product JSON offline.
Do not edit them. The importer verifies their SHA-256 digests before parsing.
The release's separate fixed-orientation list selects a different display mode
and is intentionally not an importer input.
