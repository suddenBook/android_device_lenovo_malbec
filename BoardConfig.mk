#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

DEVICE_PATH := device/lenovo/malbec

BUILD_BROKEN_DUP_RULES := true
BUILD_BROKEN_ELF_PREBUILT_PRODUCT_COPY_FILES := true

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

# Display
# Panel is 2190x3504 and its physical density is 360, which is what stock puts
# in ro.sf.lcd_density and what this sets.
#
# Stock additionally ships a 306 "display size" override. That is a
# Settings.Secure value (display_density_forced), not a board property, and AOSP
# has no default for it to override — so it is a Settings toggle for the user,
# not something to bake in by lying about the physical density here.
#
# Panel is dual sourced (BOE nt36536e / CSOT nt36536), 144 Hz. The bootloader
# names the panel on the kernel command line
# (msm_drm.dsi_display0=qcom,mdss_dsi_csot_nt36536_144hz_vid on this unit) and
# the prebuilt dtbo plus the vendor display HAL resolve it. Do NOT hardcode
# panel specific values anywhere here, and do not key anything off
# ro.boot.lcd_type: it reads "glossy", a surface finish, not a vendor.
TARGET_SCREEN_DENSITY := 360

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

# Kernel (prebuilt)
# The device ships a stock Google GKI image; nothing is built from source.
PREBUILT_PATH := $(DEVICE_PATH)-kernel
TARGET_NO_KERNEL_OVERRIDE := true
# No TARGET_KERNEL_SOURCE: the kernel repo carries images and modules only, and
# nothing in this tree is compiled against kernel UAPI headers. Pointing the
# variable at a directory that does not exist is worse than leaving it unset —
# add a kernel-headers/ to the kernel repo first if a HAL ever needs it.
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
PRODUCT_COPY_FILES += \
    $(call find-copy-subdir-files,*,$(SYSTEM_DLKM_MODULES_PATH)/,$(TARGET_COPY_OUT_SYSTEM_DLKM)/lib/modules/)

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
BOARD_AVB_VBMETA_SYSTEM := system system_dlkm system_ext product
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
