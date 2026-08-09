#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)

# full_base.mk is the Wi-Fi only sibling of the full_base_telephony.mk that phone
# device trees (onyx included) inherit. It is not optional decoration: it is the
# only path to the AOSP base product, via
#
#   full_base.mk -> generic_no_telephony.mk -> handheld_vendor.mk
#                -> media_vendor.mk -> base_vendor.mk
#
# and base_vendor.mk is what contributes selinux_policy_nonsystem,
# passwd_vendor/group_vendor, the fs_config tables and shell_and_utilities_vendor.
#
# (Correction: this used to list update_engine here too. It does not come from
# base_vendor.mk -- update_engine appears only in generic_system.mk:42 /
# mainline_system.mk:42, neither of which is in this inheritance graph. It
# reaches this product from device.mk:311, which already adds it explicitly.
# The rest of the list is accurate, and the conclusion is unchanged: without
# full_base.mk the vendor image ships with no SELinux policy.) Dropping full_base_telephony.mk without putting full_base.mk
# in its place — which is what this file used to do — removes the whole base
# product, not just telephony, and the resulting vendor image has no SELinux
# policy at all.
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base.mk)

# Inherit some common LineageOS stuff.
# malbec (TB390FU) is a Wi-Fi only tablet, so no telephony is inherited.
# common_full_tablet_wifionly.mk chains common_mobile_full.mk + tablet.mk +
# wifionly.mk, i.e. exactly the three axes this device needs, and it is the
# LineageOS file of the same name the PixelOS port used under vendor/custom.
$(call inherit-product, vendor/lineage/config/common_full_tablet_wifionly.mk)

# TARGET_SCREEN_WIDTH/HEIGHT live in device.mk, which sets both. This file used
# to set WIDTH again, with a comment claiming stock ships "an override density
# of 306". It does not: 306 appears in work/device_dump/display/wm.txt only
# because a Settings > Display size override happened to be active when that
# dump was taken. 306 is exactly 360 x 0.85, the first step below default that
# DisplayDensityUtils offers. See the density discussion in BoardConfig.mk.

# Inherit from malbec device
$(call inherit-product, device/lenovo/malbec/device.mk)

PRODUCT_NAME := lineage_malbec
PRODUCT_DEVICE := malbec
PRODUCT_MANUFACTURER := Lenovo
PRODUCT_BRAND := Lenovo
PRODUCT_MODEL := TB390FU

# Report the stock ZUI identity rather than a generated custom_malbec one.
#
# The reason is Play Protect certification, and it is worth being precise about
# what this does and does not buy, because two of the three things people
# usually cite are wrong:
#
#   - It does NOT help Widevine L1. That depends on the keybox provisioned into
#     TrustZone and on the widevine blob; neither reads ro.build.fingerprint.
#   - It does NOT get Play Integrity past BASIC. DEVICE and STRONG additionally
#     require a locked bootloader, which this device does not have.
#   - It DOES matter for the "device is not Play Protect certified" dialog:
#     that check compares the reported fingerprint against Google's certified
#     device list, and the factory TB390FU_EEA build is on it. Without this the
#     device reports an unknown fingerprint and the dialog appears on every
#     Play Store launch until the GSF ID is registered by hand.
#
# This is also standard LineageOS practice, not a PixelOS import: LineageOS
# device trees routinely carry BuildDesc/BuildFingerprint overrides in
# PRODUCT_BUILD_PROP_OVERRIDES so the build reports the OEM identity the device
# was certified under. PRODUCT_SYSTEM_NAME/PRODUCT_SYSTEM_DEVICE are plain AOSP
# variables (build/make/core/product_config.mk:402-406) and feed
# ro.product.system.{name,device} via soong_extra_config.mk:26,29.
#
# Verify after first boot: `adb shell getprop ro.build.fingerprint` should match
# the value below on every partition, and Play Store should not show the
# uncertified dialog. If it still does, this block is not earning its keep and
# should go.
PRODUCT_SYSTEM_NAME := TB390FU_EEA
PRODUCT_SYSTEM_DEVICE := TB390FU

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildDesc="TB390FU-user 16 BQ2A.250610.001-BP2A.250605.031.A3 18.0.10.335_260618 release-keys" \
    BuildFingerprint=Lenovo/TB390FU_EEA/TB390FU:16/BQ2A.250610.001-BP2A.250605.031.A3/ZUI_18.0.10.335_260618_ROW:user/release-keys \
    DeviceName=$(PRODUCT_SYSTEM_DEVICE) \
    DeviceProduct=$(PRODUCT_SYSTEM_NAME)

PRODUCT_GMS_CLIENTID_BASE := android-lenovo
