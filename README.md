# Device tree for Lenovo Idea Tab Pro Gen 2 (malbec)

The Lenovo Idea Tab Pro Gen 2 (marketed as *Lenovo Tab K13* in some regions) is a
tablet released in 2026. `malbec` is Lenovo's internal codename for the device;
`TB390FU` is the Wi-Fi-only model number.

## Specifications

| Feature | Specification |
|---|---|
| SoC | Qualcomm SM8735P (Snapdragon 8s Gen 4), silicon codename **TunaP**, `soc_id` 694 |
| Platform target | `sun` (`ro.board.platform`, `TARGET_BOOTLOADER_BOARD_NAME`) |
| CPU | 1× Cortex-X4 + 7× Cortex-A720 (ARM implementer `0x41`, parts `0xd82`/`0xd81`) |
| GPU | Adreno 825 |
| Architecture | `arm64-v8a` (64-bit only) |
| Kernel | GKI 2.0, `android15-6.6` (KMI generation 8), 4 KB pages |
| Display | 144 Hz DSI video mode; dual sourced — BOE `nt36536e` / CSOT `nt36536` |
| Touchscreen | **Novatek** (`nvt_touch`, SPI), supports Qualcomm Trusted Touch |
| Stylus | Active stylus on the Novatek digitizer — 4096 pressure levels, ±60° tilt, hover, eraser |
| Keyboard | Lenovo pogo-pin folio keyboard (`lenovo_kb` platform driver over UART) |
| Storage | UFS, dynamic partitions, Virtual A/B with compression |
| Connectivity | Wi-Fi + Bluetooth (no cellular on this model) |
| Shipped Android | 16 (ZUI 18), vendor frozen at API level 202404 |

The silicon codename and the platform name are two different things and both are
correct: DTS sources are `tunap.dts`/`tunap.dtsi` (`qcom,msm-id = <694 0x10000>`),
while the kernel build target and the HAL platform family are `sun`. Search for
`tuna`/`tunap` in device trees and for `sun` in kernel and HAL configuration.

The stock `vendor_boot` carries a 19-DTB bundle spanning three SoC families
(Kera, Sun, Tuna) because Lenovo ships one image across several SKUs. Only
DTB 19 (`TunaP`, msm-id 694) matches this device — reading the first DTB in the
bundle and stopping there is how an earlier revision of these notes ended up
claiming the SoC was "Kera".

## Device tree layout

```
malbec/
├── configs/          VINTF manifests, filesystem config, HAL configs
├── overlay/          RRO overlays: Framework, LineageSDK, Settings,
│                     SettingsProvider, SystemUI, Wifi.
│                     ⚠️ No Launcher3 overlay, deliberately -- the home screen is
│                     Launcher3's own default. Session 24's empty-workspace RRO
│                     was removed in session 25 (work/OPEN-ISSUES.md #63)
├── rootdir/          init scripts and device-specific rc files
├── sepolicy/         SELinux policy
└── proprietary-files.txt
```

## Repositories

| repo | path in tree |
|---|---|
| [`android_device_lenovo_malbec`](https://github.com/suddenBook/android_device_lenovo_malbec) | `device/lenovo/malbec` |
| [`android_device_lenovo_malbec-kernel`](https://github.com/suddenBook/android_device_lenovo_malbec-kernel) | `device/lenovo/malbec-kernel` |
| [`proprietary_vendor_lenovo_malbec`](https://github.com/suddenBook/proprietary_vendor_lenovo_malbec) | `vendor/lenovo/malbec` |

All three track **`lineage-23.2`**.

## ⚠️ `breakfast malbec` cannot fetch these repos. Use the local manifest.

`lineage.dependencies` is the LineageOS-native mechanism and it **cannot** work
for a fork outside the LineageOS organisation.
`vendor/lineage/build/tools/roomservice.py` hardcodes the org in two places:

```
:222   'name': f'LineageOS/{repo_name}'
:327   git ls-remote https://:@github.com/LineageOS/<repo_name>
```

and the `remote` escape at `:226-233` applies only to `aosp-*` remotes. So
`"repository": "suddenBook/…"` is probed as `LineageOS/suddenBook/…`, returns no
branches, and `:349` exits with *"Default revision lineage-23.2 not found …
Bailing."*

The branch name is a **separate** requirement and was also wrong until session 21
— `roomservice.py:282-292` resolves an unspecified branch from the manifest
default. Fixing that was necessary and not sufficient; the org prefix cannot be
fixed from a device tree at all.

**So:**

```bash
cp device/lenovo/malbec/malbec.xml .repo/local_manifests/malbec.xml
repo sync
```

`lineage.dependencies` is kept as-is: it is correct, and it starts working the
day these repos live under LineageOS.

## ⚠️ Then, before the first build

```bash
cd vendor/lenovo/malbec && git lfs install --local && git lfs pull
```

`libarcsoft_faceid.so` is 134 MB — over GitHub's per-file limit — so it is the
one path under Git LFS. Without this you get a **134-byte text file** and the
build stops with `must have a valid ELF magic word`, which reads like a corrupt
blob rather than a missing object. `git lfs pull` **on its own is not enough**:
it prints *"Skipping object checkout, Git LFS is not installed for this
repository"* and exits **0**.

## Building

```bash
source build/envsetup.sh
lunch lineage_malbec bp4a userdebug
mka bacon
```

or, equivalently, `bash work/scripts/40-build.sh`, which also exports
`MALBEC_BRINGUP`.

### ★ `MALBEC_BRINGUP` is a level, not a boolean

```
0   permissive + adb root + WITH_ADB_INSECURE     bring-up
1   ENFORCING  + adb root + WITH_ADB_INSECURE     the enforcing milestone
2   ENFORCING  + no adb root, no insecure adb     release
```

A bare `mka` gets **2** (the release posture, safe to hand to anyone);
`40-build.sh` supplies **0** unless you say otherwise. `true` and `false` are
still accepted and mean 0 and 2, so older invocations do not silently change
meaning; anything else is a build error.

⚠️ **Go 0 → 1 → 2, never 0 → 2.** Level 2 flips SELinux *and* removes root adb
from the running system **and from recovery** in one flash. If the result does not
boot, the only way back is bootloader fastboot — volume-down at power-on, i.e.
hands on the tablet. Level 1 flips SELinux and keeps the shell, so the enforcing
pass is diagnosable and reversible.

`WITH_ADB_INSECURE` is the half that matters, and it is set in
`lineage_malbec.mk` above the `common_full_tablet_wifionly.mk` line — see the
comment there for why that is the only place it can go.

⚠️ This paragraph used to say it "cannot live in this tree", because
`vendor/lineage/config/common.mk` tests it with `ifdef` while product config is
being parsed. The premise is true and the conclusion was wrong — it rules out
`BoardConfig.mk` and `device.mk`, not a line above the inherit that reaches
`common.mk`. The consequence of the gap was real: for three sessions,
`MALBEC_BRINGUP=1 mka bacon` (the command this README documents) built
**enforcing SELinux with no usable shell**, because the level's third promise
existed only in `work/scripts/40-build.sh`. `40-build.sh` still exports it, now
as belt-and-braces rather than as the only implementation.

⚠️ Use the **three-argument** form of `lunch`, not
`lunch lineage_malbec-bp4a-userdebug`. `build/envsetup.sh:588` decides between
the legacy and modern argument forms with `local legacy=$(echo $1 | grep "-")`.
Any `grep` that treats a bare `-` as an option rather than a pattern — `ugrep`,
and any shell function forwarding to it — returns empty, envsetup takes the
modern branch, and the whole combo string becomes the product name:

```
build/make/core/product_config.mk:226: error: Cannot locate config makefile
for product "lineage_malbec-bp4a-userdebug".
```

which reads exactly like a missing device tree and is not one.

The release config is **`bp4a`**. `bp1a` also lunches without an error but
silently produces a different configuration — a 13-month-stale
`RELEASE_PLATFORM_SECURITY_PATCH` and no `aconfig_value_set-lineage-bp4a`, so
every LineageOS feature flag falls back to its default. The authority is
`vendor/lineage/vars/aosp_target_release` and
`vendor/lineage/release/release_config_map.textproto`.

## Notes

The device ships an unmodified Google GKI kernel; all hardware support arrives as
loadable modules, so nothing is built from kernel source. The prebuilt kernel
image, the stock `dtbo` and the three module sets live in
[`android_device_lenovo_malbec-kernel`](https://github.com/suddenBook/android_device_lenovo_malbec-kernel).

Every partition in `AB_OTA_PARTITIONS` is rebuilt, `vendor`, `vendor_dlkm` and
`odm` included — they are reassembled from extracted blobs rather than reused as
stock images. That is what being a first-class A/B target requires; a build that
only replaced `system`/`system_ext`/`product` could not ship an OTA.

Panel selection happens entirely below this tree: the bootloader picks it on the
kernel command line (`msm_drm.dsi_display0=qcom,mdss_dsi_csot_nt36536_144hz_vid`
on this unit) and it is resolved inside the retained `dtbo` and the vendor
display HAL. Both suppliers therefore work with no device tree changes. Do not
hardcode panel-specific values here, and do not key anything off
`ro.boot.lcd_type` — on this device that property reads `glossy`, which is a
surface finish, not a panel vendor.

## Credits

Reference bringup based on the SM8735 platform work in
[`PixelOS-Devices/android_device_xiaomi_onyx`](https://github.com/PixelOS-Devices/android_device_xiaomi_onyx).
