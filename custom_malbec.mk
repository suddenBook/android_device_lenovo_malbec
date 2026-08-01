#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)

# Inherit some common PixelOS stuff.
# malbec (TB390FU) is a Wi-Fi only tablet, so no telephony is inherited.
$(call inherit-product, vendor/custom/config/common_full_tablet_wifionly.mk)

# TODO: confirm against `wm size` on the device. The dtbo carries a 1440-wide
# panel timing, and ro.surface_flinger.primary_display_orientation is
# ORIENTATION_270, so the panel is portrait-native and rotated to landscape.
TARGET_SCREEN_WIDTH := 1440

# Inherit from malbec device
$(call inherit-product, device/lenovo/malbec/device.mk)

PRODUCT_NAME := custom_malbec
PRODUCT_DEVICE := malbec
PRODUCT_MANUFACTURER := Lenovo
PRODUCT_BRAND := Lenovo
PRODUCT_MODEL := TB390FU

PRODUCT_SYSTEM_NAME := TB390FU
PRODUCT_SYSTEM_DEVICE := TB390FU

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildDesc="TB390FU-user 16 BQ2A.250610.001-BP2A.250605.031.A3 18.0.10.335_260618 release-keys" \
    BuildFingerprint=Lenovo/TB390FU/TB390FU:16/BQ2A.250610.001-BP2A.250605.031.A3/ZUI_18.0.10.335_260618_ROW:user/release-keys \
    DeviceName=$(PRODUCT_SYSTEM_DEVICE) \
    DeviceProduct=$(PRODUCT_SYSTEM_NAME)

PRODUCT_GMS_CLIENTID_BASE := android-lenovo
