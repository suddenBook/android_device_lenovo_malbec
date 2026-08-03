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
# Seven factory files are installed by the blob repo through PRODUCT_COPY_FILES
# while the tree also has a Soong install rule for the same path:
#
#   etc/init/{memtrack_qti, qspa_vendor, vendor.qti.audio-adsprpc-service,
#             vendor.qti.hardware.vibrator.service, vndservicemanager}.rc
#   etc/permissions/android.hardware.hardware_keystore.xml
#   etc/usb_compositions.conf
#
# In every one of the seven the FACTORY file is the correct one:
#   - the five .rc files start binaries we ship as blobs (verified: each .rc's
#     `service` line names a binary whose only install rule comes from
#     vendor/lenovo/malbec),
#   - hardware_keystore.xml declares feature version 300 to match the blob
#     KeyMint service; the tree's copy claims 400,
#   - usb_compositions.conf carries Lenovo's USB VID 0x17EF and the Lenovo-only
#     `readyfor` compositions; the tree's generic QTI copy uses 0x05C6.
#
# kati materialises PRODUCT_COPY_FILES from build/make/core/Makefile:148, i.e.
# after installs-$(TARGET_PRODUCT).mk, and Make keeps the LAST recipe -- so the
# blob wins all seven, which is what we want. Without this flag
# build/soong/ui/build/kati.go:253-256 adds --werror_overriding_commands and the
# build simply stops.
#
# What replaces the lost error: work/scripts/30-dup-installs.py lists every
# duplicate target with both sources AND which side kati keeps, and
# `--check work/analysis/blob-ownership.txt` fails if any winner stops matching
# the recorded decision. That is strictly stronger than the kati error, which
# only said "there is a tie" and never said who won.
#
# Note this is NOT the 58-duplicate problem that blocked sessions 3-5. Those
# were blob-vs-tree Soong rules caused by 70 misused ;MODULE_SUFFIX= tags
# dragging the tree's source audio stack into the build; removing the tags took
# 58 -> 0 and needed no BUILD_BROKEN_* at all.
BUILD_BROKEN_DUP_RULES := true

# A/B
AB_OTA_UPDATER := true
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
# 320, not stock's 360. This feeds ro.sf.lcd_density (build/make/core/
# sysprop_config.mk:136) and it is purely the dp->px factor: the panel always
# scans out its 2190x3504 real pixels either way, nothing is resampled.
#
# 320 is the measured physical density — dumpsys reports 319.69 x 320.14 dpi —
# so a dp lands at Android's defined 1/160 inch instead of 12.5% oversized, and
# it falls exactly on the xhdpi resource bucket, meaning bitmap drawables are
# used 1:1. Stock's 360 sits between xhdpi and xxhdpi, so Android picks xxhdpi
# assets and scales them to 0.75. Net effect of 320: ~27% more content on
# screen, and slightly better bitmap fidelity, not worse.
#
# No packaging cost: PRODUCT_AAPT_PREF_CONFIG is unset and PRODUCT_AAPT_CONFIG
# carries no density qualifiers, so every APK keeps all buckets and the choice
# is made at runtime. vendor/lineage/config/BoardConfigSoong.mk:44-49 derives
# the same "xhdpi" bucket name for both 320 and 360, so nothing else shifts.
# Window size class is unaffected (973dp vs 1095dp, both >= 840dp expanded).
# Users can still adjust via Settings > Display > Display size.
#
# ⚠️ An earlier revision claimed stock ships a 306 "display size" override.
# It does not — checked on the running device:
#   settings get secure display_density_forced -> null
#   settings get system display_density_forced -> null
#   wm density -> "Physical density: 360" with no Override line
# 306 is exactly 360 x 0.85, i.e. the first step *below* default that
# DisplayDensityUtils offers in Settings. It was a menu option read as a value.
#
# Panel is dual sourced (BOE nt36536e / CSOT nt36536), 144 Hz. The bootloader
# names the panel on the kernel command line
# (msm_drm.dsi_display0=qcom,mdss_dsi_csot_nt36536_144hz_vid on this unit) and
# the prebuilt dtbo plus the vendor display HAL resolve it. Do NOT hardcode
# panel specific values anywhere here, and do not key anything off
# ro.boot.lcd_type: it reads "glossy", a surface finish, not a vendor.
TARGET_SCREEN_DENSITY := 320

# Filesystem
TARGET_FS_CONFIG_GEN := $(DEVICE_PATH)/configs/config.fs

# HIDL
DEVICE_FRAMEWORK_COMPATIBILITY_MATRIX_FILE += \
    $(DEVICE_PATH)/configs/hidl/compatibility_matrix.device.xml \
    hardware/qcom-caf/common/vendor_framework_compatibility_matrix.xml \

DEVICE_MATRIX_FILE := hardware/qcom-caf/common/compatibility_matrix_aidl.xml
DEVICE_MANIFEST_FILE := $(DEVICE_PATH)/configs/hidl/manifest.xml

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

# ⚠️ BRING-UP ONLY — remove before the second flash, and definitely before
# anything is proposed upstream. work/scripts/35-upstream-readiness.py fails on
# this line.
#
# Why it is here for the first boot and not "cheating": an audit of the built
# image found 17 vendor service binaries that land on the catch-all
# u:object_r:vendor_file:s0 with no matching *_exec type, because their
# file_contexts entries were never written. Under enforcing, init cannot make
# the domain transition and those services simply never start — silently, with
# one avc denial each buried in a log we may not even be able to reach. Under
# permissive they all start and every denial is logged, so a single boot yields
# the complete list instead of one entry per flash cycle.
#
# This is honest about what it does and does not prove: booting permissive does
# NOT demonstrate the device boots enforcing. They are two separate milestones.
# Flash 1 = permissive, collect denials, write policy. Flash 2 = enforcing, and
# that is the one that counts.
#
# selinux.cpp:102-118 reads androidboot.selinux from bootconfig, and honours it
# only when ALLOW_PERMISSIVE_SELINUX is compiled in, which it is on userdebug.
BOARD_BOOTCONFIG += androidboot.selinux=permissive

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
# not the factory image file sizes. Note dtbo: the shipped dtbo.img is 48 MiB
# but the partition is 50 MiB.
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
# specific to this device — the ten Lenovo AIDL HALs, /dev/ttyHS1 and the
# soc:lenovo_kb sysfs subtree.

# Vendor security patch
VENDOR_SECURITY_PATCH := 2026-05-05

# Verified Boot
BOARD_AVB_ENABLE := true
BOARD_AVB_MAKE_VBMETA_IMAGE_ARGS += --flags 3
BOARD_AVB_ALGORITHM := SHA256_RSA2048
BOARD_AVB_KEY_PATH := external/avb/test/data/testkey_rsa2048.pem
BOARD_MOVE_GSI_AVB_KEYS_TO_VENDOR_BOOT := true

BOARD_AVB_BOOT_KEY_PATH := external/avb/test/data/testkey_rsa2048.pem
BOARD_AVB_BOOT_ALGORITHM := SHA256_RSA2048
BOARD_AVB_BOOT_ROLLBACK_INDEX := $(PLATFORM_SECURITY_PATCH_TIMESTAMP)
BOARD_AVB_BOOT_ROLLBACK_INDEX_LOCATION := 3

BOARD_AVB_RECOVERY_KEY_PATH := external/avb/test/data/testkey_rsa2048.pem
BOARD_AVB_RECOVERY_ALGORITHM := SHA256_RSA2048
BOARD_AVB_RECOVERY_ROLLBACK_INDEX := $(PLATFORM_SECURITY_PATCH_TIMESTAMP)
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
BOARD_AVB_VBMETA_SYSTEM_KEY_PATH := external/avb/test/data/testkey_rsa2048.pem
BOARD_AVB_VBMETA_SYSTEM_ALGORITHM := SHA256_RSA2048
BOARD_AVB_VBMETA_SYSTEM_ROLLBACK_INDEX := $(PLATFORM_SECURITY_PATCH_TIMESTAMP)
BOARD_AVB_VBMETA_SYSTEM_ROLLBACK_INDEX_LOCATION := 2

BOARD_AVB_VENDOR_ADD_HASHTREE_FOOTER_ARGS += --hash_algorithm sha256
BOARD_AVB_VENDOR_DLKM_ADD_HASHTREE_FOOTER_ARGS += --hash_algorithm sha256
BOARD_AVB_SYSTEM_DLKM_ADD_HASHTREE_FOOTER_ARGS += --hash_algorithm sha256

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
WIFI_HIDL_UNIFIED_SUPPLICANT_SERVICE_RC_ENTRY := true
WPA_SUPPLICANT_VERSION := VER_0_8_X
