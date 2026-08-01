#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

LOCAL_PATH := device/lenovo/malbec

# Proprietary blobs
$(call inherit-product, vendor/lenovo/malbec/malbec-vendor.mk)

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

# Input
# The Lenovo folio keyboard and touchpad both enumerate as 17ef:62b2 on bus
# 0x0019. The stylus digitizer (NVTCapacitivePen) needs no configuration at all:
# it sits on SPI with a zero VID/PID, so it matches no Vendor_*.idc, and AOSP
# classifies it as a STYLUS purely from its evdev capabilities
# (BTN_TOOL_PEN, BTN_STYLUS, BTN_STYLUS2, ABS_PRESSURE, ABS_TILT_X/Y).
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/keylayout/Vendor_17ef_Product_62b2.kl:$(TARGET_COPY_OUT_SYSTEM)/usr/keylayout/Vendor_17ef_Product_62b2.kl \
    $(LOCAL_PATH)/idc/Vendor_17ef_Product_62b2.idc:$(TARGET_COPY_OUT_SYSTEM)/usr/idc/Vendor_17ef_Product_62b2.idc

# Screen
TARGET_SCREEN_HEIGHT := 3504
TARGET_SCREEN_WIDTH := 2190
