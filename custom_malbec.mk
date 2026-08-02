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

# Panel is 2190x3504 (confirmed by `wm size`; the Novatek digitizer reports a
# coordinate space of exactly 10x that, 21900x35040). Stock physical density is
# 360 with an override density of 306.
TARGET_SCREEN_WIDTH := 2190

# Inherit from malbec device
$(call inherit-product, device/lenovo/malbec/device.mk)

PRODUCT_NAME := custom_malbec
PRODUCT_DEVICE := malbec
PRODUCT_MANUFACTURER := Lenovo
PRODUCT_BRAND := Lenovo
PRODUCT_MODEL := TB390FU

PRODUCT_SYSTEM_NAME := TB390FU_EEA
PRODUCT_SYSTEM_DEVICE := TB390FU

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildDesc="TB390FU-user 16 BQ2A.250610.001-BP2A.250605.031.A3 18.0.10.335_260618 release-keys" \
    BuildFingerprint=Lenovo/TB390FU_EEA/TB390FU:16/BQ2A.250610.001-BP2A.250605.031.A3/ZUI_18.0.10.335_260618_ROW:user/release-keys \
    DeviceName=$(PRODUCT_SYSTEM_DEVICE) \
    DeviceProduct=$(PRODUCT_SYSTEM_NAME)

PRODUCT_GMS_CLIENTID_BASE := android-lenovo
