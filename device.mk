#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

LOCAL_PATH := device/lenovo/malbec

# Soong namespaces
# vendor/lenovo/malbec/Android.bp imports device/lenovo/malbec, so the namespace
# has to be declared here and made visible to the product.
PRODUCT_SOONG_NAMESPACES += \
    $(LOCAL_PATH)

# Proprietary blobs
$(call inherit-product, vendor/lenovo/malbec/malbec-vendor.mk)

# Symlinks that point at runtime locations instead of shipped files, so
# extract-utils cannot generate them. See Android.bp for what each one is for.
PRODUCT_PACKAGES += \
    malbec_wlanmdsp_otaupdate_symlink \
    malbec_wlan_cfg_kiwi_v2_symlink \
    malbec_wlan_cfg_peach_symlink \
    malbec_wlan_cfg_peach_v2_symlink \
    malbec_wlan_cfg_qca6750_symlink \
    malbec_wlan_cfg_wcn7750_symlink \
    malbec_wlan_mac_kiwi_v2_symlink \
    malbec_wlan_mac_peach_symlink \
    malbec_wlan_mac_peach_v2_symlink \
    malbec_wlan_mac_qca6750_symlink \
    malbec_wlan_mac_wcn7750_symlink \
    malbec_cneapp_vndfwk_detect_symlink

# Generic ramdisk allow list
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_ramdisk.mk)

# Project ID Quota
$(call inherit-product, $(SRC_TARGET_DIR)/product/emulated_storage.mk)

# Virtual A/B
# Stock enables compression, userspace snapshots, batch writes, io_uring and
# XOR (ro.virtual_ab.compression.xor.enabled=true), so inherit the XOR variant.
# It chains through compression.mk to launch_with_vendor_ramdisk.mk, which is
# where PRODUCT_VIRTUAL_AB_OTA and the vendor ramdisk fsck tools come from.
$(call inherit-product, $(SRC_TARGET_DIR)/product/virtual_ab_ota/compression_with_xor.mk)

# Dalvik vm configs
# 6144 is the largest profile AOSP ships and is the right one for this 8 GB device.
# The "phone" in the name is historical; the profiles are keyed on RAM and density.
$(call inherit-product, frameworks/native/build/phone-xhdpi-6144-dalvik-heap.mk)

# AVF
# Stock ships com.android.virt, so keep parity. Note that VmTerminalApp is
# deliberately NOT included: the hypervisor here is Gunyah, not KVM, /dev/kvm
# does not exist and only protected VMs are supported, so the Terminal's
# non-protected Debian VM cannot start. See PROGRESS.md.
$(call inherit-product, packages/modules/Virtualization/apex/product_packages.mk)

# Qualcomm
$(call soong_config_set,rfs,mpss_firmware_symlink_target,modem_firmware)
$(call inherit-product, hardware/qcom-caf/common/common.mk)

# Device characteristics
# Not set anywhere in the inherited tablet config, but stock reports
# ro.build.characteristics=tablet and resource selection depends on it.
PRODUCT_CHARACTERISTICS := tablet

# A/B
AB_OTA_POSTINSTALL_CONFIG += \
    RUN_POSTINSTALL_system=true \
    POSTINSTALL_PATH_system=system/bin/otapreopt_script \
    FILESYSTEM_TYPE_system=ext4 \
    POSTINSTALL_OPTIONAL_system=true

AB_OTA_POSTINSTALL_CONFIG += \
    RUN_POSTINSTALL_vendor=true \
    POSTINSTALL_PATH_vendor=bin/checkpoint_gc \
    FILESYSTEM_TYPE_vendor=ext4 \
    POSTINSTALL_OPTIONAL_vendor=true

PRODUCT_PACKAGES += \
    otapreopt_script \
    checkpoint_gc

# API
# Both values are what the device actually shipped with, read off the stock
# vendor build.prop: ro.board.first_api_level / ro.board.api_level = 202404 and
# ro.product.first_api_level = 36.
#
# BOARD_SHIPPING_API_LEVEL is the one that matters for booting. It is the vendor
# API surface this device froze at (Android 15, VINTF target-level 202404) while
# the framework side is Android 16, which is the Treble GRF arrangement this
# port depends on. Declare it too low or too high and the VINTF compatibility
# check between the framework matrix and the vendor manifest fails.
#
# PRODUCT_SHIPPING_API_LEVEL is 36 rather than onyx's 35 simply because this
# tablet launched on Android 16 and onyx launched on 15.
#
# BOARD_API_LEVEL is deliberately absent: the release config sets it from
# RELEASE_BOARD_API_LEVEL and build/make/core/board_config.mk errors out if a
# device tree assigns it.
BOARD_SHIPPING_API_LEVEL := 202404
PRODUCT_SHIPPING_API_LEVEL := 36

# Input
# The Lenovo folio keyboard and touchpad both enumerate as 17ef:62b2 on bus
# 0x0019. The stylus digitizer (NVTCapacitivePen) needs no configuration at all:
# it sits on SPI with a zero VID/PID, so it matches no Vendor_*.idc, and AOSP
# classifies it as a STYLUS purely from its evdev capabilities
# (BTN_TOOL_PEN, BTN_STYLUS, BTN_STYLUS2, ABS_PRESSURE, ABS_TILT_X/Y).
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/keylayout/Vendor_17ef_Product_62b2.kl:$(TARGET_COPY_OUT_SYSTEM)/usr/keylayout/Vendor_17ef_Product_62b2.kl \
    $(LOCAL_PATH)/idc/Vendor_17ef_Product_62b2.idc:$(TARGET_COPY_OUT_SYSTEM)/usr/idc/Vendor_17ef_Product_62b2.idc

# Rootdir
# fstab.qcom is the one vendor config this port has to change, so the device
# tree owns it and proprietary-files.txt skips the stock copy.
PRODUCT_PACKAGES += \
    fstab.qcom

# Screen
TARGET_SCREEN_HEIGHT := 3504
TARGET_SCREEN_WIDTH := 2190

# WiFi
# Built from source, not carried as blobs — see the WiFi section of
# BoardConfig.mk for why. The per-device tuning files (WCNSS_qcom_cfg.ini for
# each chip directory, vendor_cmd.xml, the supplicant overlays and
# etc/hostapd/*) stay as blobs, since they are this board's configuration and
# do not exist in AOSP.
PRODUCT_PACKAGES += \
    android.hardware.wifi-service \
    hostapd \
    hostapd_cli \
    libwifi-hal-qcom \
    wpa_cli \
    wpa_supplicant \
    wpa_supplicant.conf
