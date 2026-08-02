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

# No PRODUCT_SYSTEM_NAME / PRODUCT_SYSTEM_DEVICE / PRODUCT_BUILD_PROP_OVERRIDES.
#
# Those four lines used to restate the stock ZUI fingerprint
# (Lenovo/TB390FU_EEA/TB390FU:16/.../ZUI_18.0.10.335_260618_ROW:user/release-keys)
# so the build would report itself as the factory ROM. Removed deliberately:
#
#   - It buys nothing for Widevine. L1 depends on the keybox provisioned in
#     TrustZone and on the widevine blob, neither of which reads
#     ro.build.fingerprint.
#   - It buys nothing for Play Integrity beyond the BASIC verdict, and
#     DEVICE/STRONG cannot pass on an unlocked bootloader regardless.
#   - The build system otherwise derives a consistent fingerprint from
#     PRODUCT_NAME/PRODUCT_DEVICE for every partition, which is what a port
#     should report.
#
# Note that this is a deliberate divergence from onyx, which does carry the
# equivalent block. Reinstating it is four lines if a concrete, measured reason
# turns up -- record the measurement in the commit message if so.
PRODUCT_GMS_CLIENTID_BASE := android-lenovo
