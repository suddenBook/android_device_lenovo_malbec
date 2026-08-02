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
# passwd_vendor/group_vendor, the fs_config tables, shell_and_utilities_vendor
# and update_engine. Dropping full_base_telephony.mk without putting full_base.mk
# in its place — which is what this file used to do — removes the whole base
# product, not just telephony, and the resulting vendor image has no SELinux
# policy at all.
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base.mk)

# Inherit some common PixelOS stuff.
# malbec (TB390FU) is a Wi-Fi only tablet, so no telephony is inherited.
$(call inherit-product, vendor/custom/config/common_full_tablet_wifionly.mk)

# TARGET_SCREEN_WIDTH/HEIGHT live in device.mk, which sets both. This file used
# to set WIDTH again, with a comment claiming stock ships "an override density
# of 306". It does not: 306 appears in work/device_dump/display/wm.txt only
# because a Settings > Display size override happened to be active when that
# dump was taken. On the device now, `wm density` prints no Override line and
# both display_density_forced settings read null. 306 is exactly 360 x 0.85,
# the first step below default that DisplayDensityUtils offers. See the density
# discussion in BoardConfig.mk.

# Inherit from malbec device
$(call inherit-product, device/lenovo/malbec/device.mk)

PRODUCT_NAME := custom_malbec
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
# onyx, an official PixelOS device, carries the equivalent block.
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
