# malbec signing keys — PUBLIC, AND THEREFORE WORTHLESS AS KEYS

**Do not trust anything signed with these keys. Do not ship a device that
accepts them.** The `.pk8` private keys in this directory are published in a
public repository, which means everyone has them.

This is deliberate — the repository owner chose to publish them — but the
consequence is not optional, so it is written down here rather than left for
someone to discover:

* **`platform.pk8`** — anyone can build an APK that declares
  `android:sharedUserId="android.uid.system"`, sign it with this key, and have
  it run as the system UID on any build signed with this key set. Installing one
  APK is full compromise.
* **`releasekey.pk8`** — this is the only certificate in
  `system/etc/security/otacerts.zip`, so recovery accepts any OTA signed with it
  as genuine. Anyone can produce an update this device will install.
* A build signed with these keys reports `ro.build.tags=release-keys`. That
  string now means nothing here: it is weaker than the `test-keys` build it
  replaced, because test keys are *known* to be untrusted while these look
  legitimate.

Publication is irreversible. Public commits are indexed by secret scanners and
third-party scrapers within seconds; deleting the files or rewriting history
does not retract them. The only remedy is a new key set plus a wipe and
re-flash of every device that ever ran a build signed with these.

## Where the build actually reads them

Not from here. `vendor/lineage/config/common.mk` includes
`vendor/lineage-priv/keys/keys.mk`, so to reproduce a build with this key set,
copy this directory to `vendor/lineage-priv/keys/`. `keys.mk` and `Android.bp`
are included unchanged for that purpose.

`testkey` is a byte-identical copy of `releasekey`; see the comment in
`keys.mk` and the `[@RELEASE]` section of `system/sepolicy/private/keys.conf`
for why that name has to exist.

## What these keys never covered

AVB. `BoardConfig.mk` signs vbmeta, boot, recovery and vbmeta_system with
`external/avb/test/data/testkey_rsa4096.pem`, the public AOSP test key.
Verified boot on this port was never protected by this key set.
