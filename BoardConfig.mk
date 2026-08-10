#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

DEVICE_PATH := device/lenovo/malbec

# ⚠️ Still do not add BUILD_BROKEN_* here without proving the build needs it.
#
# BUILD_BROKEN_ELF_PREBUILT_PRODUCT_COPY_FILES stays OFF. It was once set for
# the system_dlkm .ko copy, which is BOARD_SYSTEM_KERNEL_MODULES now; nothing
# left in PRODUCT_COPY_FILES is an ELF. (onyx sets it because it uses
# ;MAKE_COPY_RULE_ONLY on .so blobs. We do not need that -- see below.)
#
# BUILD_BROKEN_DUP_RULES is ON, and here is the proof it is needed.
#
# 22 install targets are claimed by two rules. They fall into THREE classes, and
# describing them as one is how the version of this comment that stood until
# session 21 managed to be wrong in five separate ways at once. Do not edit this
# from memory -- run the script:
#
#     python3 work/scripts/30-dup-installs.py            # live list + winners
#     python3 work/scripts/30-dup-installs.py --check    # fail on drift
#
# -- A. blob PRODUCT_COPY_FILES vs a Soong install rule -- 13 paths, blob wins all
#
#   kati materialises PRODUCT_COPY_FILES from build/make/core/Makefile:148, i.e.
#   AFTER installs-$(TARGET_PRODUCT).mk, and Make keeps the LAST recipe. The blob
#   therefore wins every one of these, which is what we want. Without this flag
#   build/soong/ui/build/kati.go:253-256 adds --werror_overriding_commands and
#   the build simply stops.
#
#     etc/init/{memtrack_qti, qspa_vendor, vendor.qti.audio-adsprpc-service}.rc
#     etc/init/init.qti.display_boot.rc
#     etc/init/vendor.qti.hardware.display.{allocator,composer,demura}-service.rc
#     etc/permissions/android.hardware.hardware_keystore.xml
#     etc/usb_compositions.conf
#     etc/wifi/wpa_supplicant.conf
#     etc/aidl/hfp/hfp_codec_capabilities.xml
#     etc/aidl/le_audio/aidl_audio_set_{configurations,scenarios}.bfbs
#
#   Why the blob is right: the .rc files start binaries we ship as blobs (each
#   one's `service` line names a binary whose only install rule comes from
#   vendor/lenovo/malbec); hardware_keystore.xml declares feature version 300 to
#   match the blob KeyMint service, which our manifest declares at V3, while the
#   tree's copy claims 400; usb_compositions.conf carries Lenovo's USB VID 0x17EF
#   and the Lenovo-only `readyfor` compositions where the generic QTI copy uses
#   0x05C6. composer-service.rc is the one pair whose sides genuinely differ --
#   tree uses `task_profiles ServiceCapacityLow`, blob uses
#   `writepid /dev/cpuset/system-background/tasks` -- and the blob is
#   byte-identical to stock.
#
#   /!\ vndservicemanager.rc was listed here and no longer collides.
#   /!\ product/media/bootanimation.zip was here until session 21 and is gone:
#       the stock animation was dropped for LineageOS's generated one, which is
#       sized from TARGET_SCREEN_{WIDTH,HEIGHT} and therefore actually fits.
#
# -- B. Soong install rule vs Soong install rule -- 3 paths, blob wins all
#
#     bin/init.qti.display_boot.sh
#     etc/vintf/manifest/face-default.xml
#     etc/vintf/manifest/vendor.qti.hardware.display.composer-service3_v3.xml
#
#   Both sides are ordinary install rules in installs-$(TARGET_PRODUCT).mk, so
#   the winner is whichever module Soong visited LAST. That is deterministic for
#   a fixed tree and NOT contractual -- which is exactly why the winners are
#   recorded in work/analysis/blob-ownership.txt rather than left to luck.
#   face-default.xml matters because the AOSP reference face HAL leaks its vintf
#   fragment into the build while the only face service installed is the ArcSoft
#   blob (checked against the vendor image input list: the AOSP binary is not
#   packaged at all). composer-service3_v3.xml matters because device.mk pins the
#   whole QTI display stack to blobs. init.qti.display_boot.sh is the one whose
#   two candidates are functionally different -- the blob adds kera soc_ids
#   720/721/731/732 to the `sun` case, and that case has no `*)` default -- so a
#   flip there is not cosmetic.
#
# -- C. Soong install rule vs a `vintf_fragments:` rule -- 6 paths, tree wins all
#
#     etc/vintf/manifest/manifest_audio_qti_services.xml
#     etc/vintf/manifest/mapper.qti.xml
#     etc/vintf/manifest/memtrack_qti.xml
#     etc/vintf/manifest/soundtrigger.qti.xml
#     etc/vintf/manifest/vendor.qti.hardware.display.allocator-service.xml
#     etc/vintf/manifest/vendor.qti.hardware.display.demura-service.xml
#
#   *** This class is STRUCTURAL and cannot be changed from this device tree.
#   build/soong/android/module.go:2216-2228 turns a module's `vintf_fragments:`
#   into a katiVintfInstall, and build/soong/android/makevars.go:552-564 writes
#   those into a SEPARATE block appended after every normal install rule. Last
#   recipe wins, so a vintf_fragments-derived rule ALWAYS beats a
#   prebuilt_etc_xml install of the same path. The only lever is removing
#   `vintf_fragments:` from the source module, i.e. patching shared HAL repos.
#
#   It is also harmless. The recipe is `assemble_vintf`
#   (build/make/core/definitions.mk:3209-3215), not a copy -- the same
#   normalisation that produced stock's own copies -- and all six were compared
#   tuple-by-tuple on (format, name, version, fqname) against the extracted stock
#   vendor image and are semantically identical. In each of the six, the source
#   module that wins is itself replaced by a `prefer: true` blob and ships no
#   binary; only its manifest fragment reaches the image.
#
# /!\ vendor.qti.hardware.vibrator.service.rc used to be in class A. It is gone
# -- the whole vibrator stack was removed once the tablet was confirmed to have
# no motor, and session 21 re-confirmed that the hard way: stock runs a
# COMPLETE, LIVE vibrator stack (service running, IVibrator registered, nine
# primitives, a real PMIC node at pm7550ba@7:qcom,vibrator@df00) and the owner
# still feels nothing, because Lenovo shares one vendor image across SKUs. If it
# reappears here, something re-added the stack.
#
# What replaces the lost kati error: work/scripts/30-dup-installs.py lists every
# duplicate target with both sources AND which side kati keeps, and `--check`
# fails if any winner stops matching work/analysis/blob-ownership.txt. That is
# strictly stronger than the kati error, which only said "there is a tie" and
# never said who won.
#
# /!\ The claim that used to end this block -- "this is NOT the 58-duplicate
# problem ... removing the tags took 58 -> 0 and needed no BUILD_BROKEN_* at
# all" -- was FALSE for classes B and C: nine paths collide Soong-against-Soong
# today. The ;MODULE_SUFFIX= story is still true as history; the "took it to
# zero" conclusion never was.
BUILD_BROKEN_DUP_RULES := true

# A/B
AB_OTA_UPDATER := true

# ★ Both names, and it has to be both.
#
# lineage_malbec.mk deliberately reports the stock ZUI identity, and
# PRODUCT_BUILD_PROP_OVERRIDES += DeviceName=TB390FU feeds
# build/make/core/sysprop.mk:39 directly:
#
#     ro.product.$(1).device=$${DeviceName:-$(TARGET_DEVICE)}
#
# so this build ships ro.product.device=TB390FU while ro.lineage.device=malbec.
# With TARGET_OTA_ASSERT_DEVICE unset, ota_override_device is absent from
# misc_info (build/make/core/Makefile:6239-6241) and releasetools falls back to
# ro.product.device (build/make/tools/releasetools/common.py:448) — so every
# package would assert TB390FU and nothing else.
#
# That is self-consistent only for as long as the spoof never changes. The day
# the fingerprint block is dropped, ro.product.device becomes malbec and every
# previously built OTA refuses to install with "device is TB390FU, package is
# for malbec" — and the reverse for packages built before it was added. Naming
# both costs nothing and removes the trap in both directions.
TARGET_OTA_ASSERT_DEVICE := malbec,TB390FU
AB_OTA_PARTITIONS := \
    boot \
    dtbo \
    init_boot \
    odm \
    product \
    recovery \
    system \
    system_dlkm \
    system_ext \
    vbmeta \
    vbmeta_system \
    vendor \
    vendor_boot \
    vendor_dlkm

# Architecture
TARGET_ARCH := arm64
TARGET_ARCH_VARIANT := armv8-a-branchprot
TARGET_CPU_ABI := arm64-v8a
TARGET_CPU_VARIANT := generic
# SM8735 is 1x Cortex-X4 + 7x Cortex-A720, i.e. stock Arm cores. Note this
# deliberately differs from the onyx tree, which sets "oryon" — that is the
# SM8750 custom core and does not apply here.
TARGET_CPU_VARIANT_RUNTIME := cortex-a76

# Audio
AUDIO_FEATURE_ENABLED_DLKM := true
AUDIO_FEATURE_ENABLED_EXTENDED_COMPRESS_FORMAT := true
AUDIO_FEATURE_ENABLED_GKI := true
AUDIO_FEATURE_ENABLED_INSTANCE_ID := true
AUDIO_FEATURE_ENABLED_MCS := true
AUDIO_FEATURE_ENABLED_SVA_MULTI_STAGE := true
BOARD_SUPPORTS_OPENSOURCE_STHAL := true
BOARD_SUPPORTS_SOUND_TRIGGER := true
BOARD_USES_ALSA_AUDIO := true
TARGET_PROVIDES_AUDIO_HAL := true
TARGET_PROVIDES_LIBAGM := true
TARGET_PROVIDES_LIBAR_PAL := true

# Bootloader
TARGET_BOOTLOADER_BOARD_NAME := sun

# Graphics
#
# This has to be set here rather than as a property, because
# build/make/core/sysprop_config.mk:128-132 emits
#
#     ADDITIONAL_VENDOR_PROPERTIES += ro.hwui.use_vulkan=$(TARGET_USES_VULKAN)
#
# unconditionally. Leaving TARGET_USES_VULKAN unset does not omit the line --
# it emits `ro.hwui.use_vulkan=` with an empty value. properties/vendor.prop
# used to also carry `ro.hwui.use_vulkan=true`, and two non-optional
# assignments with different values make post_process_props.py:87-113 print
# "found duplicate sysprop assignments" and exit 1, failing the build.
# (--allow-dup only comes with BUILD_BROKEN_DUP_SYSPROP, which we do not want.)
#
# Same hazard the ro.bionic.cpu_variant comment in vendor.prop already
# documents; it just was not applied to this one. onyx sets it at device.mk:163.
# Stock value confirmed `true` in work/unpacked/parts/vendor/build.prop.
TARGET_USES_VULKAN := true

# Display
# 320. Feeds ro.sf.lcd_density (build/make/core/sysprop_config.mk:136). Purely
# the dp->px factor: the panel always scans out its 2190x3504 real pixels either
# way, nothing is resampled.
#
# ★ THIS IS THE DEVICE OWNER'S DECISION AND IT IS SETTLED. Stock is 360 and an
# earlier round shipped 360; the owner has since set 320 by hand and asked for
# it to stay. It is a preference about how big the UI should be on their own
# 13-inch tablet, and preferences of that kind are not something a derivation
# overrules. Do not "correct" it back.
#
# Everything below is kept because it is still the honest accounting of what 320
# costs relative to 360, and because the 306 arithmetic at the end is referenced
# from overlay/FrameworkOverlayMalbec. Read it as trade-offs that have been
# accepted, not as an argument against the value above.
#
# What the owner gains at 320: 1752 x 1095 dp of workspace instead of
# 1557 x 973 dp, i.e. 26% more logical area, which on a 13" screen with a
# keyboard attached is the point of the machine.
#
# What it costs, and why 360 was argued for before:
#
#   dp is a *nominal* unit. Its purpose is that a given dp count occupies
#   roughly the same physical size across devices, and the size Android
#   actually targets is set by what shipping devices do, not by the 1/160"
#   definition. Every Google large-screen device runs density well ABOVE
#   physical dpi. Pixel Tablet: 2560x1600 over 10.95" = 275.6 real dpi, ships
#   ro.sf.lcd_density 320 -- 16% above physical.
#
#   The metric that matters is dp per physical inch, because Material's 48dp
#   minimum touch target is meant to land at ~9 mm:
#
#     device                 real dpi   density   dp/inch   48dp
#     Pixel Tablet 10.95"      275.6      320      137.8    8.85 mm
#     malbec 13.0" @ 320       319.9      320      160.0    7.62 mm   <- shipped
#     malbec 13.0" @ 360       319.9      360      142.2    8.57 mm   <- stock
#
#   Panel is 2190/319.69 = 6.85" x 3504/320.15 = 10.94", diagonal 12.91",
#   i.e. the advertised 13.0". So 320 puts this 13" tablet 16% denser than
#   Google's own 11" tablet -- the wrong direction for a device this size.
#
# Second: logical workspace. At 320 the display is 1752 x 1095 dp -- 1.9x the
# *area* of the Pixel Tablet's 1280 x 800 dp, which is past what Google's
# SystemUI / Launcher / Settings layouts are tuned for; at 360 it is
# 1557 x 973 dp, closer to tested ground. Observed on the running device: the
# split-shade QS and the 2-pane Settings leave large dead zones at the wider
# size. Accepted -- dead space is a cosmetic cost, and it buys real estate.
#
# Third: a restored settings backup lands differently. Settings backs up display
# size as a *scale index*, not a px value (DisplayDensityUtils; MIN_SCALE 0.85).
# The owner runs the smallest step, which on stock is 360 * 0.85 = 306; on a 320
# build the same restore gives 320 * 0.85 = 272. If the UI ever comes back
# smaller than expected after a restore, this is why, and the fix is to move the
# Settings display-size step rather than to change the density here.
#
# One thing 320 actually gets for free: it IS a resource bucket. 360 sits
# between xhdpi (320) and xxhdpi (480), and ResTable_config::isBetterThan
# prefers the nearest bucket >= requested, so on a 360 build bitmap drawables
# come from xxhdpi and are scaled by 0.75. At 320 they are used at native size.
# Minor either way -- modern Material UI is overwhelmingly vector drawables --
# but it is a point in favour, not against.
#
# No packaging cost either way: PRODUCT_AAPT_PREF_CONFIG is unset and
# PRODUCT_AAPT_CONFIG carries no density qualifiers, so every APK keeps all
# buckets and the choice is made at runtime.
# vendor/lineage/config/BoardConfigSoong.mk:44-49 derives the same "xhdpi"
# bucket name for both 320 and 360, so nothing else shifts. Window size class
# is expanded either way (973dp vs 1095dp, both >= 840dp).
#
# ⚠️ About the 306 that keeps coming up, because this comment has been wrong in
# both directions and the value 306 also underpins the rounded_corner_radius in
# overlay/FrameworkOverlayMalbec:
#
#   - Stock's default IS 360. That part was always right.
#   - An earlier revision said stock ships a 306 "display size" override, and
#     derived things from it. Wrong: it is not a stock default.
#   - The correction that replaced it said `wm density` shows "no Override line".
#     Also wrong: there is an override, and it is whatever the owner last chose
#     in Settings > Display size.
#
# ⚠️ 306 is NOT a constant either, and treating it as one is how this comment
# went wrong twice. It is one particular Settings > Display size step, captured
# at one moment. Re-measured on the running stock ROM, session 21
# (work/device_dump/stock/display/wm.txt):
#
#         Physical size: 2190x3504
#         Physical density: 360
#         Override density: 269
#
# i.e. the owner has since moved further down the scale. Nothing in this tree
# derives anything from either number.
#
# What the new measurement is actually good for: it CORROBORATES 320. The owner's
# lived-in stock density is 269, well below 360 — so the preference is for more
# workspace, not less, and on a 320 base the same 269 is roughly one Settings
# step down rather than several. Shipping 360 would have put the owner three or
# four steps from where they want to be.
#
# Panel is dual sourced (BOE nt36536e / CSOT nt36536), 144 Hz. The bootloader
# names the panel on the kernel command line
# (msm_drm.dsi_display0=qcom,mdss_dsi_csot_nt36536_144hz_vid on this unit) and
# the prebuilt dtbo plus the vendor display HAL resolve it. Do NOT hardcode
# panel specific values anywhere here, and do not key anything off
# ro.boot.lcd_type: it reads "glossy", a surface finish, not a vendor.
TARGET_SCREEN_DENSITY := 320

# The panel's real pixel count, in the framework's (portrait) frame — the same
# frame TARGET_SCREEN_DENSITY is expressed in. The DRM connector scans out
# 3504x2190 landscape; ro.surface_flinger.primary_display_orientation=ORIENTATION_270
# in vendor.prop is what turns that into 2190x3504 for everything above it.
#
# Read by vendor/lineage/config/BoardConfigSoong.mk:27-28, which feeds the
# LineageOS boot-animation generator. gen-bootanimation.sh:35-52 takes
# min(HEIGHT, WIDTH) = 2190 and emits 2190x730 frames — so unlike PixelOS's
# fixed 720/1080/1440 assets, the animation is built to fit this panel and there
# is nothing to extract from stock.
#
# ⚠️ Do NOT bend these to steer an asset chooser. Earlier notes considered
# setting WIDTH to 720 or 1440 for exactly that reason; on LineageOS it would
# only produce a smaller animation on a 3.5K panel.
TARGET_SCREEN_HEIGHT := 3504
TARGET_SCREEN_WIDTH := 2190

# Filesystem
TARGET_FS_CONFIG_GEN := $(DEVICE_PATH)/configs/config.fs

# HIDL
DEVICE_FRAMEWORK_COMPATIBILITY_MATRIX_FILE += \
    $(DEVICE_PATH)/configs/hidl/compatibility_matrix.device.xml \
    hardware/qcom-caf/common/vendor_framework_compatibility_matrix.xml \

# += for exactly the reason the comment below gives for DEVICE_MANIFEST_FILE —
# this line was the one the argument was written about and did not get applied to
# (session 24). soong_config.mk:681 is `$(call add_json_list, DeviceMatrixFile,
# $(DEVICE_MATRIX_FILE))`, a list, and device/google/trout/aosp_trout_x86_64.mk:37
# appends to it in-tree, so the same silent-drop applies.
DEVICE_MATRIX_FILE += hardware/qcom-caf/common/compatibility_matrix_aidl.xml
# += rather than :=. build/make/core/soong_config.mk:699 consumes this as a list
# (add_json_list, DeviceManifestFiles) and AOSP's own build/make/target/product/
# full.mk:26 appends to it, so := silently drops anything an inherited config
# contributes. Nothing contributes today, which is exactly why this would go
# unnoticed until something did. Note the DEVICE_FRAMEWORK_COMPATIBILITY_MATRIX_FILE
# two lines up already uses +=; this line was just inconsistent with it.
DEVICE_MANIFEST_FILE += $(DEVICE_PATH)/configs/hidl/manifest.xml

# Kernel
BOARD_INCLUDE_DTB_IN_BOOTIMG := true
BOARD_RAMDISK_USE_LZ4 := true
TARGET_NEEDS_DTBOIMAGE := true

BOARD_KERNEL_BASE := 0x00000000
BOARD_KERNEL_PAGESIZE := 4096
BOARD_KERNEL_IMAGE_NAME := Image

BOARD_BOOT_HEADER_VERSION := 4
BOARD_MKBOOTIMG_ARGS := --header_version $(BOARD_BOOT_HEADER_VERSION)

BOARD_INIT_BOOT_HEADER_VERSION := 4
BOARD_MKBOOTIMG_INIT_ARGS += --header_version $(BOARD_INIT_BOOT_HEADER_VERSION)

# Taken from the stock vendor_boot header, which is the only cmdline the boot
# images actually carry — boot.img and recovery.img both ship an empty one.
# Everything else visible in /proc/cmdline is contributed by the bootloader and
# the DTB, including the panel selector
# (msm_drm.dsi_display0=qcom,mdss_dsi_csot_nt36536_144hz_vid) and
# kvm-arm.mode=protected. Do not copy those here; they are not ours to set.
# The trailing "bootconfig" token is appended by the build system because
# BOARD_BOOTCONFIG is set.
BOARD_KERNEL_CMDLINE := video=vfb:640x400,bpp=32,memsize=3072000

# Verbatim from the stock vendor_boot bootconfig section.
BOARD_BOOTCONFIG := \
    androidboot.hardware=qcom \
    androidboot.memcg=1 \
    androidboot.usbcontroller=a600000.dwc3 \
    androidboot.load_modules_parallel=true \
    androidboot.hypervisor.protected_vm.supported=true \
    androidboot.vendor.qspa=true

# ⚠️ BRING-UP ONLY. Gated on MALBEC_BRINGUP, which work/scripts/40-build.sh
# exports and 35-upstream-readiness.py checks.
#
# ⚠️ This paragraph used to say "the default is ON (?= true), i.e. opt-OUT",
# which stopped being true when the default became `false`, and is doubly wrong
# now that the switch is a LEVEL. The default here is **2** (release posture) and
# work/scripts/40-build.sh supplies **0** when you build through it — so a bare
# `m` is safe to hand to someone and the wrapper is what gives you a shell. See
# the level table below.
#
# ⚠️ The rationale that used to be here was WRONG, and it is worth writing down
# because it would have wasted the whole first flash:
#
#   "17 vendor services land on the catch-all vendor_file with no *_exec type;
#    under enforcing init cannot transition so they never start; under permissive
#    they all start and every denial is logged, so one boot yields the full list."
#
# The first two clauses are right. The third is not. system/core/init/service.cpp
# :102-113 — when the computed context equals init's own, init returns an error
# ONLY if enforcing; in permissive it just LOG(ERROR)s and carries on. So those
# services do start, but as u:r:init:s0. init's domain is broad enough that most
# of what they do is simply *allowed*, so the denials do not merely get
# misattributed — for the most part they never happen at all. A permissive boot
# in that state produces no per-domain rule information for precisely the
# services it was set up to diagnose.
#
# The order therefore has to be: label first (sepolicy/vendor/, done), then boot.
# Permissive is still worth keeping for flash 1, but as a safety net for whatever
# we missed — not as the discovery mechanism.
#
# What it does and does not prove is unchanged: booting permissive does NOT
# demonstrate the device boots enforcing. Two separate milestones.
#
# selinux.cpp:102-118 reads androidboot.selinux from bootconfig, and honours it
# only when ALLOW_PERMISSIVE_SELINUX is compiled in, which it is on userdebug.
# ⚠️ Session 9 flipped this default from `true` to `false`. The previous round
# had made it opt-out for a real reason — a casual bare `m` would produce an
# enforcing build with no adb, and at that time the device was hundreds of
# kilometres away with what looked like a single flashing attempt. The concern
# was genuine; the cost was that EVERY build, including any handed to someone
# else later, baked in androidboot.selinux=permissive with nothing in the
# artifact to distinguish it from a permanently permissive ROM.
#
# Both halves are now covered: work/scripts/40-build.sh exports
# MALBEC_BRINGUP=${MALBEC_BRINGUP:-true} explicitly, so building through the
# usual wrapper still yields permissive + adb, while a bare `m` yields
# enforcing. The switch is still here; it is simply no longer the default.
# ── MALBEC_BRINGUP is a LEVEL, not a boolean ────────────────────────────────
#
#   0   permissive + adb root + WITH_ADB_INSECURE     bring-up
#   1   ENFORCING  + adb root + WITH_ADB_INSECURE     the enforcing milestone
#   2   ENFORCING  + no adb root, no insecure adb     release
#
# ★ Level 1 exists because levels 0 and 2 are two changes at once, and the second
# of them removes the only way back in. Going 0 -> 2 means flipping SELinux AND
# taking away the shell in the same flash: if anything then fails to register or
# fails to start, there is nothing to diagnose it with, and getting back needs
# volume-down at power-on — i.e. hands on the device. Level 1 flips SELinux and
# keeps the shell, so the enforcing pass is diagnosable and reversible, including
# from recovery (WITH_ADB_INSECURE is what authorises recovery's adb — #33).
#
# ⚠️ DO NOT GO STRAIGHT TO 2 WITHOUT PHYSICAL ACCESS TO THE TABLET. Nothing in
# this tree can recover a device that does not boot and has no adb; the way back
# is bootloader fastboot, which is a button combination.
#
# `true` and `false` are still accepted and mean 0 and 2, so nothing that already
# says MALBEC_BRINGUP=true silently changes meaning.
MALBEC_BRINGUP ?= 2
ifneq (,$(filter true,$(MALBEC_BRINGUP)))
MALBEC_BRINGUP := 0
endif
ifneq (,$(filter false,$(MALBEC_BRINGUP)))
MALBEC_BRINGUP := 2
endif
ifeq (,$(filter 0 1 2,$(MALBEC_BRINGUP)))
$(error MALBEC_BRINGUP must be 0, 1 or 2 (got '$(MALBEC_BRINGUP)'))
endif

# Permissive is level 0 ONLY. Levels 1 and 2 are both enforcing.
ifeq ($(MALBEC_BRINGUP),0)
BOARD_BOOTCONFIG += androidboot.selinux=permissive
endif

# Kernel (prebuilt)
# The device ships a stock Google GKI image; nothing is built from source.
PREBUILT_PATH := $(DEVICE_PATH)-kernel
TARGET_NO_KERNEL_OVERRIDE := true
# ⚠️ This used to say "no TARGET_KERNEL_SOURCE, because nothing in this tree is
# compiled against kernel UAPI headers". The second half was wrong, and it cost
# a build: vendor/lineage/build/soong's generated_kernel_includes is declared
# unconditionally (Android.bp:21) and 69 modules in this product depend on it —
# the entire QTI display stack (libsdmcore, libsdedrm, libdrmutils, libsdmclient,
# the composer service, gralloc), plus libar-pal, audio.primary.sun,
# hwcomposer.qcom and ipacm. libdrmutils literally does
# #include <display/drm/sde_drm.h>, and that header exists nowhere else in the
# tree. The genrule runs `make -C $(TARGET_KERNEL_SOURCE) headers_install`, which
# with the variable unset defaults to kernel/lenovo/malbec and fails.
#
# ⚠️ Session 12 moved the entire QTI display cluster to blobs, so the display
# half of that list no longer builds. The variable is STILL REQUIRED —
# audio.primary.sun, hwcomposer.qcom and ipacm still consume the generated
# headers. Do not remove it on the grounds that the display stack is gone.
#
# m nothing cannot catch this class: it generates the build graph but does not
# execute genrules. To pre-flight it, check that every `-C <dir>` in
# out/soong/.intermediates/**/*.sbox.textproto points at a directory that exists.
#
# A prebuilt-kernel device does not skip this variable, it points it at a
# pre-extracted UAPI header set with a stub Makefile. See the provenance and the
# KMI-generation reasoning in kernel-headers/Makefile.
#
# Do not switch to TARGET_PREBUILT_KERNEL_HEADERS instead: build/soong/cc/cc.go
# reads that one from the process environment, not from a Make variable, so it
# would silently fall back to the broken path on a clean checkout.
TARGET_KERNEL_SOURCE := $(PREBUILT_PATH)/kernel-headers
BOARD_PREBUILT_DTBIMAGE_DIR := $(PREBUILT_PATH)/images/dtbs/
BOARD_PREBUILT_DTBOIMAGE := $(PREBUILT_PATH)/images/dtbo.img
PRODUCT_COPY_FILES += \
    $(PREBUILT_PATH)/images/kernel:kernel

# Kernel modules
# Deliberately no GKI_VERSION variable. It would only be right for one of the
# three module sets: system_dlkm's 96 modules carry the vermagic
# 6.6.87-android15-8-gc2569c3b141c-ab13768703-4k that matches the kernel banner,
# while vendor_dlkm and vendor_boot were built against 6.6.92-android15-8 and
# report "6.6.92-android15-8-maybe-dirty-4k". They all load anyway because every
# module carries modversions, which makes the kernel's same_magic() compare only
# the part after the version token, and that part is identical across all 718.
# A single GKI_VERSION here invites someone to build a
# /lib/modules/$(GKI_VERSION)/ install path out of it, which would be wrong for
# this device in two different ways at once.
DLKM_MODULES_PATH := $(PREBUILT_PATH)/modules/vendor_dlkm
RAMDISK_MODULES_PATH := $(PREBUILT_PATH)/modules/vendor_boot
SYSTEM_DLKM_MODULES_PATH := $(PREBUILT_PATH)/modules/system_dlkm

# NOTE: unlike most SM8750/SM8735 devices, malbec's stock system_dlkm keeps its
# modules flat under /lib/modules, not under /lib/modules/$(GKI_VERSION).
# Verified against the factory image; keep this layout or modprobe will not find
# them at first stage.
#
# ⚠️ This used to be a PRODUCT_COPY_FILES of the whole directory, which looked
# equivalent and was not. BOARD_SYSTEM_KERNEL_MODULES is also what
# build/make/core/Makefile:725 hands to the *vendor_dlkm* depmod run as its
# extra-modules argument, so with it unset depmod only sees the 300 vendor
# modules and the regenerated vendor_dlkm/lib/modules/modules.dep loses every
# cross-partition dependency. Stock's has 107 of them, covering 11 vendor
# modules that genuinely depend on GKI modules -- including
# qca_cld3_wcn7750 (the Wi-Fi driver on this Wi-Fi-only tablet),
# cfg80211 -> rfkill, btpower, btfm_slim_codec and zram_ext -> zsmalloc.
# It is masked at boot today because init.qti.kernel.rc:54 runs
# `exec_start gki.modprobe` first, which loads all of system_dlkm blindly, but
# it is not masked in recovery or for any on-demand/modalias load.
#
# The AOSP SYSTEM path matches stock on all three counts that matter here:
# BOARD_KERNEL_MODULE_DIRS is "top", so _kver is empty and the modules install
# flat (Makefile:570-599); the strip staging dir argument is empty, so the
# modules are not stripped and their signatures survive; and
# BOARD_SYSTEM_KERNEL_MODULES_LOAD defaults to false (Makefile:708-710), which
# produces an empty modules.load -- exactly what stock ships, and irrelevant
# either way because /vendor/bin/system_dlkm_modprobe.sh globs *.ko rather than
# reading modules.load.
#
# Dropping the PRODUCT_COPY_FILES form also removes the only ELF prebuilt in
# PRODUCT_COPY_FILES, hence BUILD_BROKEN_ELF_PREBUILT_PRODUCT_COPY_FILES is gone
# from the top of this file. (The prebuilt kernel is a raw ARM64 boot Image, not
# an ELF.)
BOARD_SYSTEM_KERNEL_MODULES := $(wildcard $(SYSTEM_DLKM_MODULES_PATH)/*.ko)

BOARD_VENDOR_KERNEL_MODULES := $(wildcard $(DLKM_MODULES_PATH)/*.ko)
BOARD_VENDOR_KERNEL_MODULES_LOAD := $(patsubst %,$(DLKM_MODULES_PATH)/%,$(shell cat $(DLKM_MODULES_PATH)/modules.load))
BOARD_VENDOR_KERNEL_MODULES_BLOCKLIST_FILE := $(DLKM_MODULES_PATH)/modules.blocklist

# Stock's /vendor/bin/system_dlkm_modprobe.sh reads this second blocklist to keep
# the GKI zram.ko out of the way of Qualcomm's zram_ext. Both modules ship here
# (system_dlkm/zram.ko and vendor_dlkm/zram_ext.ko), so the file has to come
# along or the wrong one loads. Neither the *.ko wildcard above nor
# BOARD_VENDOR_KERNEL_MODULES_BLOCKLIST_FILE, which only installs
# modules.blocklist, would pick it up.
PRODUCT_COPY_FILES += \
    $(DLKM_MODULES_PATH)/system_dlkm.modules.blocklist:$(TARGET_COPY_OUT_VENDOR_DLKM)/lib/modules/system_dlkm.modules.blocklist

BOARD_VENDOR_RAMDISK_KERNEL_MODULES := $(wildcard $(RAMDISK_MODULES_PATH)/*.ko)
BOARD_VENDOR_RAMDISK_KERNEL_MODULES_LOAD := $(patsubst %,$(RAMDISK_MODULES_PATH)/%,$(shell cat $(RAMDISK_MODULES_PATH)/modules.load))
BOARD_VENDOR_RAMDISK_RECOVERY_KERNEL_MODULES_LOAD := $(patsubst %,$(RAMDISK_MODULES_PATH)/%,$(shell cat $(RAMDISK_MODULES_PATH)/modules.load.recovery))
BOARD_VENDOR_RAMDISK_KERNEL_MODULES_BLOCKLIST_FILE := $(RAMDISK_MODULES_PATH)/modules.blocklist

# Partitions
# Sizes are the authoritative values from `fastboot getvar all` on the device,
# not the factory image file sizes. Note dtbo: the image we ship is 34.5 MiB
# while the partition is 50 MiB.
#
# ⚠️ This used to say "the shipped dtbo.img is 48 MiB", which described the
# factory partition DUMP rather than an image. The dump's own
# dt_table_header.total_size is 36,185,879, and the factory vbmeta's dtbo hash
# descriptor covers exactly that many bytes — recomputing
# sha256(salt ‖ dtbo[0:36185879]) reproduces stock's digest byte for byte, while
# hashing all 50,331,648 does not. The 13.49 MiB tail was partition slack that
# was being hashed, flashed and carried in every OTA for nothing. Truncated in
# malbec-kernel; see that repo's README for the derivation.
BOARD_BOOTIMAGE_PARTITION_SIZE := 100663296
BOARD_DTBOIMG_PARTITION_SIZE := 52428800
BOARD_INIT_BOOT_IMAGE_PARTITION_SIZE := 8388608
BOARD_RECOVERYIMAGE_PARTITION_SIZE := 104857600
BOARD_SUPER_PARTITION_SIZE := 23622320128
BOARD_VENDOR_BOOTIMAGE_PARTITION_SIZE := 100663296

# Vestigial on a UFS A/B device; matched to the onyx tree, which is a known
# good configuration on this SoC.
BOARD_FLASH_BLOCK_SIZE := 131072

BOARD_USES_METADATA_PARTITION := true

BOARD_QTI_DYNAMIC_PARTITIONS_PARTITION_LIST := odm product system system_dlkm system_ext vendor vendor_dlkm
BOARD_QTI_DYNAMIC_PARTITIONS_SIZE := 23618125824 # (BOARD_SUPER_PARTITION_SIZE - 4 MiB)
BOARD_SUPER_PARTITION_GROUPS := qti_dynamic_partitions

# Stock uses ext4 for every logical partition. Keep parity for the first bringup
# so the filesystem type is not an extra variable; erofs can be evaluated later.
BOARD_ODMIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_PRODUCTIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_SYSTEMIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_SYSTEM_DLKMIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_SYSTEM_EXTIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_VENDORIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_VENDOR_DLKMIMAGE_FILE_SYSTEM_TYPE := ext4

TARGET_COPY_OUT_ODM := odm
TARGET_COPY_OUT_PRODUCT := product
TARGET_COPY_OUT_SYSTEM := system
TARGET_COPY_OUT_SYSTEM_DLKM := system_dlkm
TARGET_COPY_OUT_SYSTEM_EXT := system_ext
TARGET_COPY_OUT_VENDOR := vendor
TARGET_COPY_OUT_VENDOR_DLKM := vendor_dlkm

-include vendor/lineage/config/BoardConfigReservedSize.mk

# Platform
# The silicon codename is "TunaP" (soc_id 694, DTS sources are tunap.dts /
# tunap.dtsi with qcom,msm-id = <694 0x10000>), while the kernel build target and
# the HAL platform family are both "sun". Both names are correct; they are
# different naming axes. Search device trees for tuna/tunap and kernel/HAL
# configuration for sun.
BOARD_USES_QCOM_HARDWARE := true
TARGET_BOARD_PLATFORM := sun

# Properties
TARGET_ODM_PROP += $(DEVICE_PATH)/properties/odm.prop
TARGET_PRODUCT_PROP += $(DEVICE_PATH)/properties/product.prop
TARGET_SYSTEM_PROP += $(DEVICE_PATH)/properties/system.prop
TARGET_SYSTEM_EXT_PROP += $(DEVICE_PATH)/properties/system_ext.prop
TARGET_VENDOR_PROP += $(DEVICE_PATH)/properties/vendor.prop

# Recovery
BOARD_EXCLUDE_KERNEL_FROM_RECOVERY_IMAGE := true
TARGET_RECOVERY_FSTAB := $(DEVICE_PATH)/rootdir/etc/fstab.qcom
TARGET_RECOVERY_PIXEL_FORMAT := RGBX_8888

# ⚠️ TARGET_RECOVERY_DEFAULT_ROTATION is deliberately NOT set, and the reasoning
# for setting it is good enough that it needs writing down as refuted.
#
# The argument was: the panel scans out 3504x2190, every framework client is
# turned round by ro.surface_flinger.primary_display_orientation=ORIENTATION_270
# (vendor.prop), and the boot animation by ro.bootanim.set_orientation_logical_0
# (system.prop) — and minui reads NEITHER of those. It reads
# ro.minui.default_rotation / ro.minui.default_touch_rotation
# (bootable/recovery/minui/graphics.cpp:480,492, recovery_ui/screen_ui.cpp
# :1810-1813), which build/make/core/sysprop_config.mk:43-51 emits only from
# TARGET_RECOVERY_DEFAULT_{,TOUCH_}ROTATION. So recovery must be rotated too.
#
# It must not. Measured, rather than reasoned: the STOCK recovery ramdisk
# (work/backup/stock-preflash/recovery_a.img, unpacked to
# work/unpacked/recovery/) contains exactly one minui property in prop.default:
#
#     ro.minui.pixel_format=RGBX_8888          # :399
#
# and NO ro.minui.default_rotation at all — `grep -rn minui` over the whole
# ramdisk finds only that line plus three plat_property_contexts declarations.
# Lenovo's own recovery therefore runs unrotated, i.e. the panel's native
# landscape raster IS the correct orientation for recovery on this device, and
# adding a rotation would turn it 90° away from the one arrangement known to
# work. (It also confirms TARGET_RECOVERY_PIXEL_FORMAT above matches stock
# byte-for-byte.)
#
# If recovery ever does come up sideways, re-measure before setting this — the
# variable is right, the value is not obvious, and stock is the reference.

# Flashing
# Ship our own fastboot-info.txt instead of the one the build synthesises at
# build/make/core/Makefile:5929-5933. The generated one flashes recovery three
# lines before `reboot fastboot`, i.e. it overwrites fastbootd and then requires
# fastbootd. That is what bricked this device on the first flash attempt.
# See the file itself for the full reasoning and for why `flashall` is not the
# recommended path on this device at all (work/scripts/42-flash.sh is).
TARGET_BOARD_FASTBOOT_INFO_FILE := $(DEVICE_PATH)/fastboot-info.txt

# Filesystems
# /data and /metadata are both f2fs in rootdir/etc/fstab.qcom, matching stock.
#
# ⚠️ This used to say "not a recovery setting despite where it used to sit".
# That is INVERTED — it is precisely a recovery setting, and the value is right
# for that reason. No partition here is *built* as f2fs, so the only two
# consumers in the tree are:
#   build/make/core/Makefile:2131            adds MKF2FSUSERIMG to host deps
#   build/make/core/android_soong_config_vars.mk:59  feeds
#       bootable/recovery/Android.bp:283-289, which is what pulls
#       make_f2fs.recovery / fsck.f2fs.recovery / sload_f2fs.recovery into the
#       recovery ramdisk.
# Confirmed in out/.../installed-files-recovery.txt:
#   /root/system/bin/{make_f2fs,fsck.f2fs,sload_f2fs}
# Recovery needs those to format the f2fs /data and /metadata, so keep it — but
# keep it for the real reason.
TARGET_USERIMAGES_USE_F2FS := true

# Sepolicy
# No device/lineage/sepolicy/libperfmgr/sepolicy.mk: that adds policy for the
# LineageOS libperfmgr power HAL, and this device keeps the stock QTI
# vendor/bin/hw/android.hardware.power-service instead (it is in
# proprietary-files.txt, and its VINTF fragment ships with it). onyx does use
# libperfmgr, which is where the include came from; adopting it here would mean
# a power/ directory with a powerhint.json and a mode-extension library, none of
# which exist yet.
include device/qcom/sepolicy_vndr/SEPolicy.mk
BOARD_VENDOR_SEPOLICY_DIRS += $(DEVICE_PATH)/sepolicy/vendor
# No SYSTEM_EXT_{PUBLIC,PRIVATE}_SEPOLICY_DIRS: nothing in this port adds
# platform-side policy, so sepolicy/public and sepolicy/private do not exist.
# Git does not track empty directories, which means a fresh clone would have had
# BoardConfig.mk pointing at two paths that were never there. Add the lines back
# together with the first file that needs them.
#
# The bulk of the vendor policy comes from device/qcom/sepolicy_vndr above:
# TARGET_BOARD_PLATFORM is sun, which qcom_defs.mk puts in UM_6_6_FAMILY, so
# SEPolicy.mk selects the sm8750 tree. sepolicy/vendor here only adds what is
# specific to this device — the eight Lenovo AIDL HALs, /dev/ttyHS1 and the
# soc:lenovo_kb sysfs subtree.

# Vendor security patch
VENDOR_SECURITY_PATCH := 2026-05-05

# Verified Boot
BOARD_AVB_ENABLE := true
#
# ⚠️ There is deliberately NO `BOARD_AVB_MAKE_VBMETA_IMAGE_ARGS += --flags 3` here,
# and the reasoning matters because earlier revisions of this file had one and
# described it as a permanent decision. The disable belongs on the *flash*, not in
# the *image*. Read this before adding it back.
#
# What --flags 3 (HASHTREE_DISABLED | VERIFICATION_DISABLED) would actually do:
# fs_avb.cpp:255-259 sets kVerificationDisabled, and SetUpAvbHashtree at :554-558
# treats kVerificationDisabled and kHashtreeDisabled identically and returns
# kDisabled. That is not "the bootloader skips a check at boot" — it means
# /system /vendor /product /system_ext /odm /vendor_dlkm /system_dlkm have no
# dm-verity at runtime at all. And because vbmeta and vbmeta_system are both in
# AB_OTA_PARTITIONS, every OTA built from this tree would re-apply that to whoever
# installs it. An official device may not ship OTAs that turn off verified boot
# for its users.
#
# `--flags 2` is not a middle ground either: :255 tests verification_disabled
# first and lands on the same kDisabled branch. flags 0 or nothing.
#
# Why flags 0 is safe on an unlocked device: fs_avb.cpp:280-290 IsAvbPermissive()
# returns true whenever the bootloader is unlocked (unless
# /metadata/gsi/dsu/avb_enforce exists, which it does not here), so
# allow_verification_error is set and a verification failure degrades to
# androidboot.veritymode=eio instead of refusing to boot.
# external/avb/README.md:768-786 says the same thing normatively.
#
# ⚠️ This used to add "the stock ROM on this very unit demonstrates it:
# ro.boot.veritymode=eio". It does not, and the dump in this repo says so:
#
#     work/device_dump/stock/props/getprop.txt
#       [ro.boot.verifiedbootstate]: [orange]      <- unlocked, as claimed
#       [ro.boot.veritymode]:        [enforcing]   <- NOT eio
#       [ro.boot.veritymode.managed]:[yes]
#
# Which is the correct outcome and a better demonstration than the one that was
# claimed: unlocked does NOT mean verity is off, it means a verity FAILURE is
# survivable. Stock's hashtrees match its partitions, so nothing fails and the
# mode stays enforcing. The dm-verity half of the old sentence is right — the
# device has live *-verity targets (proc/mounts.txt).
#
# The real hazard people hit — sitting on the boot logo forever — comes from a
# MISMATCHED image set (a patched boot, a GSI, or a partial flash against a stale
# vbmeta): boot proceeds, but dm-verity is built from a hashtree that does not
# describe the bytes on the partition, so every mismatched read returns EIO.
# Two things guard against that here:
#   1. work/scripts/43-avb-consistency.py --deep proves the descriptors match the
#      images before anything is flashed.
#   2. work/scripts/42-flash.sh writes the top-level vbmeta with
#      `fastboot --disable-verity --disable-verification flash vbmeta_a`.
#      fastboot.cpp:2487 passes is_vbmeta_partition("vbmeta_a") (true per
#      :1128-1132) as apply_vbmeta, so :1241 calls rewrite_vbmeta_buffer, which ORs
#      bits 0/1 into the flags field at offset 123 of the buffer being written.
#      The device therefore behaves exactly as if flags 3 had been built in, while
#      this image and every OTA generated from it stay correct. vbmeta_system does
#      not need the same treatment: avb_slot_verify.c:977-991 short-circuits before
#      it ever walks the chain descriptor.
# Flashing vbmeta without those switches is how we later prove verity really works.
#
# SHA256_RSA4096 rather than RSA2048: stock signs vbmeta, vbmeta_system, boot and
# recovery with RSA4096 (avbtool info_image on each Factory/image file), and
# RSA4096 is also what AOSP falls back to when no key is given. The 2048-bit test
# key was a silent downgrade with no stated reason.
BOARD_AVB_ALGORITHM := SHA256_RSA4096
BOARD_AVB_KEY_PATH := external/avb/test/data/testkey_rsa4096.pem
# No BOARD_MOVE_GSI_AVB_KEYS_TO_VENDOR_BOOT. It only redirects the
# {q,r,s}-developer-gsi.avbpubkey modules defined at
# system/core/rootdir/avb/Android.bp:15-70, and none of them is in this product's
# PRODUCT_PACKAGES (nothing here inherits developer_gsi_keys.mk), so the flag had
# no modules to move. Verified: `grep avbpubkey installed-files*.txt` is empty and
# out/.../vendor_ramdisk/ has no avb/ directory.
# (rootdir/etc/fstab.qcom still carries avb_keys= on the /system line, which is
# therefore also dead — and provably inert, not merely believed to be: fs_mgr.cpp:1641
# reaches the avb_keys branch only as the `else` of `if (current_entry.fs_mgr_flags.avb)`,
# and libfstab/fstab.cpp:330-334 sets that flag from `avb=vbmeta_system` on the same
# line. Left in place deliberately: it is byte-identical to the factory
# vendor/etc/fstab.qcom, i.e. a known-booting first-stage mount line, and there is
# nothing to gain from editing one.)

BOARD_AVB_BOOT_KEY_PATH := external/avb/test/data/testkey_rsa4096.pem
BOARD_AVB_BOOT_ALGORITHM := SHA256_RSA4096
# Match stock rather than PLATFORM_SECURITY_PATCH_TIMESTAMP, for the same reason
# spelled out under BOARD_AVB_RECOVERY_ROLLBACK_INDEX below. Measured:
# `avbtool info_image --image Factory/image/boot.img` -> Rollback Index 1777939200.
# On the bp4a release config PLATFORM_SECURITY_PATCH_TIMESTAMP is 1764892800, i.e.
# *lower* than stock, so the previous value was not even monotonic with the image
# it replaces.
BOARD_AVB_BOOT_ROLLBACK_INDEX := 1777939200
BOARD_AVB_BOOT_ROLLBACK_INDEX_LOCATION := 3

BOARD_AVB_RECOVERY_KEY_PATH := external/avb/test/data/testkey_rsa4096.pem
BOARD_AVB_RECOVERY_ALGORITHM := SHA256_RSA4096
# ⚠️ Stock uses a literal 1 at this same rollback index location (verified with
# avbtool info_image on Factory/image/recovery.img). This used to be
# PLATFORM_SECURITY_PATCH_TIMESTAMP = 1780272000.
#
# Harmless while the bootloader is unlocked — libavb/ABL only commit rollback
# indexes to RPMB on a locked, verified boot. But it is a one-way door: relock
# once with the old value and index location 1 is burned to 1780272000, after
# which stock recovery (index 1) can never boot again. Matching stock costs
# nothing and removes the trap.
BOARD_AVB_RECOVERY_ROLLBACK_INDEX := 1
BOARD_AVB_RECOVERY_ROLLBACK_INDEX_LOCATION := 1

# vbmeta_system is listed in AB_OTA_PARTITIONS and fstab.qcom mounts system,
# system_ext and product with avb=vbmeta_system, so the chained image has to
# exist — without these four lines no vbmeta_system.img is produced at all and
# those three partitions have nothing to verify against.
# ⚠️ system_dlkm was in this list and does not belong. Checked against the
# factory images with avbtool info_image:
#   vbmeta.img        chains boot/recovery/vbmeta_system, and carries top-level
#                     hashtree descriptors for odm, system_dlkm, vendor,
#                     vendor_dlkm
#   vbmeta_system.img carries pvmfw, product, system, system_ext -- no system_dlkm
# and rootdir/etc/fstab.qcom:70 mounts system_dlkm with avb=vbmeta, i.e. against
# the top-level image. Listing it here moves its descriptor into the chained
# image (Makefile:4973-4984 excludes chained members from vbmeta.img), which
# disagrees with both stock and our own fstab.
BOARD_AVB_VBMETA_SYSTEM := system system_ext product
BOARD_AVB_VBMETA_SYSTEM_KEY_PATH := external/avb/test/data/testkey_rsa4096.pem
BOARD_AVB_VBMETA_SYSTEM_ALGORITHM := SHA256_RSA4096
# Same one-way-door reasoning as BOARD_AVB_RECOVERY_ROLLBACK_INDEX above. This was
# 0 with a comment claiming it was inert "because the top-level vbmeta carries
# flags=3" — that justification is gone now that the image is built with verity on,
# so match stock instead: `avbtool info_image --image Factory/image/vbmeta_system.img`
# -> Rollback Index 1777939200. Equal to stock is the only value that traps neither
# direction if this device is ever relocked.
BOARD_AVB_VBMETA_SYSTEM_ROLLBACK_INDEX := 1777939200
BOARD_AVB_VBMETA_SYSTEM_ROLLBACK_INDEX_LOCATION := 2

# No --hash_algorithm sha256 lines here. hardware/qcom-caf/common/BoardConfigQcom.mk
# :452-460 already appends exactly that for all nine partitions; adding it again
# produced "--hash_algorithm sha256 --hash_algorithm sha256" in misc_info.txt for
# vendor, vendor_dlkm and system_dlkm. avbtool takes the last one so it was
# harmless, but it invited someone to "fix" the wrong copy.

# Vendor
include vendor/lenovo/malbec/BoardConfigVendor.mk

# WiFi
# This is a Wi-Fi only tablet, so this section is the single most important one
# in the file. The chip is wcn7750 — lsmod on the device shows qca_cld3_wcn7750
# bound, with icnss2 and cnss_* around it.
#
# The Wi-Fi stack is built from source rather than carried as blobs, matching
# onyx: wpa_supplicant, hostapd and android.hardware.wifi-service are AOSP
# components, and the stock copies were compiled against an Android 15 vendor
# while the framework here is Android 16. proprietary-files.txt excludes them
# for that reason; only the per-device tuning files stay as blobs.
#
# ── ★ ⚠️ SESSION 21: THE VENDOR HAL COMES FROM A DIFFERENT REPO ON LINEAGEOS ──
#
# Everything this block used to say about BOARD_WLAN_CHIP being "load-bearing"
# was measured against hardware/qcom/wlan. That repo exists in this checkout and
# IS NOT THE ONE USED. Ground truth, from the build's own variables file:
#
#     out/soong/soong.lineage_malbec.variables
#       NamespacesToExport … hardware/qcom-caf/wlan, hardware/qcom-caf/wlan/qcwcn
#                            (hardware/qcom/wlan is NOT exported)
#       VendorVars.qcom_wifi = {"board_wlan_chip": "wcn7760"}
#
# and build/soong/android/namespace.go: the root namespace sees the root plus
# EXPORTED namespaces only. So `libwifi-hal-qcom` resolves in
# hardware/qcom-caf/wlan, and the qcom_wifi/board_wlan_chip variable that the
# two lines below set is read by nobody — its only consumer is
# hardware/qcom/wlan/Android.bp:41, in a namespace this product does not export.
#
# The CAF repo uses a different namespace and a different variable:
#     hardware/qcom-caf/wlan/Android.bp:10-12  config_namespace "wifi",
#                                              variable "qcom_wlan_hal"
#
# ── The outcome is still correct, and NOT by luck in the way it looks ──
#
# qcom_wlan_hal is unset, so libwifi-hal-qcom takes conditions_default
# (Android.bp:64-73), which whole-static-links
# //hardware/qcom-caf/wlan/qcwcn:libwifi-hal-qcom. That IS the Wi-Fi 7
# generation — checked in the source rather than assumed: qcwcn/wifi_hal/ has
# twt.cpp, nan_pairing{,_initiator,_responder}.cpp, wifi_cached_scan_result.cpp
# and nud_stats.h, and llstats.cpp carries 39 MLO references. Those are exactly
# the files whose absence the old comment was worried about.
#
# ── ⚠️⚠️ DO NOT "FIX" THIS BY SETTING qcom_wlan_hal ⚠️⚠️ ──
#
# It is the obvious tidy-up and it would BREAK WI-FI COMPLETELY.
# hardware/qcom-caf/wlan/Android.bp:64 declares qcom_wlan_hal with
# conditions_default and NO `qcwcn:` branch, even though "qcwcn" is its only
# declared value (:52-56). Setting it therefore selects an EMPTY branch:
# whole_static_libs and shared_libs both vanish and libwifi-hal.so is built with
# no QCOM HAL inside it at all. Unset is the only correct state.
#
# BOARD_WLAN_CHIP is kept below because it is free and it records the part, but
# nothing in this tree reads it. BOARD_WLAN_DEVICE is the one that does real
# work — it selects lib_driver_cmd_qcwcn for wpa_supplicant and hostapd, and it
# reaches Soong as wifi/board_wlan_device.
#
# ★ The empirical check, after any change here, is in work/scripts/50-validate.sh
# and it is a runtime one because no build-time check can see this:
#
#     strings /vendor/lib64/libwifi-hal.so | grep -ciE "mlo|11be|eht"
#
# Zero means the legacy HAL got linked: the service still starts, wlan0 still
# comes up, and every Wi-Fi 7 capability silently reports NOT_SUPPORTED. On a
# Wi-Fi-ONLY tablet that is the whole point of the device.
BOARD_WLAN_CHIP := wcn7760

BOARD_WLAN_DEVICE := qcwcn
BOARD_HOSTAPD_DRIVER := NL80211
BOARD_HOSTAPD_PRIVATE_LIB := lib_driver_cmd_$(BOARD_WLAN_DEVICE)
BOARD_WPA_SUPPLICANT_DRIVER := NL80211
BOARD_WPA_SUPPLICANT_PRIVATE_LIB := lib_driver_cmd_$(BOARD_WLAN_DEVICE)
# (1 STA + 1 AP) or (1 STA + 1 of (P2P or NAN)) or (2 AP) or (2 STA)
WIFI_HAL_INTERFACE_COMBINATIONS := {{{STA}, 1}, {{AP}, 1}}, {{{STA}, 1}, {{P2P, NAN}, 1}}, {{{AP}, 2}}, {{{STA}, 2}}
WIFI_DRIVER_DEFAULT := qca_cld3
WIFI_DRIVER_STATE_CTRL_PARAM := "/dev/wlan"
WIFI_DRIVER_STATE_OFF := "OFF"
WIFI_DRIVER_STATE_ON := "ON"
WIFI_FEATURE_HOSTAPD_11AX := true
# ★ 11BE, session 24. WITHOUT THESE THE SOFTAP SILENTLY DOWNGRADES TO 11ax while
# the framework advertises Wi-Fi 7 hotspot support, which is the same
# "advertised and dead" shape this tree hunts everywhere else.
#
# external/wpa_supplicant_8/board_config_wpa_supplicant.mk:47-54 is the only
# reader; it turns these into soong_config hostapd_11be / wpa_supplicant_11be,
# and hostapd/Android.bp:483-485,638-641 gates -DCONFIG_IEEE80211BE and
# src/ap/ieee802_11_eht.c on them. Nothing else sets them, so unset means the
# EHT code is not compiled at all.
#
# Measured on the artefacts rather than reasoned about — count of `eht_` strings
# in the shipped binaries, ours before this change vs stock's:
#
#                     ours   stock
#   vendor/bin/hw/hostapd          6      20
#   vendor/bin/hw/wpa_supplicant   5       6
#   (`ieee80211be` as a whole word: hostapd 0 vs 1, supplicant 0 vs 8)
#
# i.e. Lenovo builds all of this and we did not, on the same silicon.
#
# ★★ And the decisive measurement is a runtime one, taken by starting the hotspot
# on this build before the change. hostapd prints its own verdict:
#
#   hostapd: getGeneration hwmode=1, ht_enabled=1, vht_enabled=0,
#            he_supported=1, eht_supported=1, ieee80211ax=1, ieee80211be=0
#   SoftApInfo{... wifiStandard= 6 ...}          <- 6 = WIFI_STANDARD_11AX
#
# `eht_supported=1` is the DRIVER answering that the radio does EHT AP mode;
# `ieee80211be=0` is our hostapd having no CONFIG_IEEE80211BE to turn it on with.
# So this is not a hardware limit and not a driver limit — it is one missing
# board variable, and the driver has already said yes.
#
# ⚠️ The STA side already worked WITHOUT the flag and that is not a reason to
# skip it: live on this build, `dumpsys wifi` reports `Wi-Fi standard: 11be,
# Link speed: 2882Mbps, mWifiStandard=7`, because EHT reporting reads
# wpa_s->connection_eht, which events.c sets unconditionally. What the supplicant
# flag adds is the ML-element handling in the SAE path (sme.c), i.e. MLO — and
# this build reports `AP MLO Affiliated links: []`, `mlo_mode=0` today.
#
# ✔ ACCEPTANCE TEST RUN AND PASSED on the flashed build, and the way it passed
# is worth writing down because the first attempt looked like a failure:
#
#   $ cmd wifi start-softap <ssid> wpa2 <psk> -b 5
#     SoftApInfo{... wifiStandard= 6 ...}          <- still 11ax!
#     hostapd: ... eht_supported=1, ieee80211be=0
#     SoftApManager[wlan1]: 11BE is not allowed, removing from configuration
#
#   $ cmd wifi start-softap <ssid> wpa3 <psk> -b 5
#     SoftApInfo{... wifiStandard= 8 ... mMldAddress=b6:67:42:6e:a0:bf}
#     hostapd: nl80211: Set freq 5745 (... eht_enabled=1 ...)
#     hostapd: ... eht_supported=1, ieee80211be=1
#
# ★ 8 is WIFI_STANDARD_11BE, and the MLD address only appears on a real EHT AP.
#
# ⚠️ THE GATE IS WPA3, NOT THE DRIVER. ApConfigUtil.is11beAllowedForThisConfiguration()
# ends in is11beDisabledForSecurityType(config.getSecurityType()), so a WPA2 AP is
# downgraded to 11ax by the framework before hostapd ever sees the config — which
# is correct, 802.11be mandates SAE. Anyone re-running this with `wpa2` will
# conclude the change did nothing. It is also why the framework's *persisted*
# hotspot config (Ieee80211beEnabled = true, Ieee80211axEnabled = true) is the
# one that matters in normal use: Settings' own hotspot defaults to WPA3 here.
#
# No `unknown configuration item`, no start failure, and the STA side is
# unaffected (`Wi-Fi standard: 11be, Link speed: 2882Mbps` on the same boot).
#
# If a future driver ever rejects EHT AP mode, revert BOTH of these AND set
# overlay/WifiOverlayMalbec/res/values/config.xml's
# config_wifiSoftapIeee80211beSupported back to false. The board flag and the
# overlay resource are one decision and must move together.
WIFI_FEATURE_HOSTAPD_11BE := true
WIFI_FEATURE_SUPPLICANT_11BE := true
# ⚠️ SUPPLICANT_11AX is REQUIRED BY SUPPLICANT_11BE and upstream does not say so.
# Setting 11BE alone does not fail a check — it fails the compile, ~2 minutes in:
#
#   src/ap/ieee802_11_eht.c:125:21: error: no member named 'he_oper_chwidth'
#                                          in 'struct hostapd_config'
#   (also :180, :335)
#
# because wpa_supplicant/Android.bp:1179-1181 adds src/ap/ieee802_11_eht.c under
# wpa_supplicant_11be, that file dereferences `hapd->iconf->he_oper_chwidth`
# unconditionally, and the field only exists inside `#ifdef CONFIG_IEEE80211AX`
# (src/ap/ap_config.h:1159-1164). The two selects at :1176-1181 are written as if
# they were independent and they are not.
#
# So this line is not "11ax as well, for completeness" — it is the dependency,
# and removing it breaks the build rather than the feature. Stock builds it too:
# its wpa_supplicant carries 14 `he_` strings to our 12 pre-change, and
# `ieee80211ax` twice to our once.
WIFI_FEATURE_SUPPLICANT_11AX := true
WIFI_HIDL_UNIFIED_SUPPLICANT_SERVICE_RC_ENTRY := true
WPA_SUPPLICANT_VERSION := VER_0_8_X
