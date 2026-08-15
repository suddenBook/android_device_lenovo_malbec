#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

# Picked up by vendor/lineage/config/common.mk:304 (`-include`), so this file
# only has to exist to take effect.
#
# Setting this to anything other than build/make/target/product/security/testkey
# is what flips BUILD_KEYS from test-keys to release-keys
# (build/make/core/config.mk:1364-1367), which is what lands in ro.build.tags.
#
# The sibling keys in this directory — platform, shared, media, networkstack,
# nfc, bluetooth, sdk_sandbox — are NOT named here on purpose. A module that
# says `LOCAL_CERTIFICATE := platform` is rewritten to
# $(dir $(DEFAULT_SYSTEM_DEV_CERTIFICATE))platform
# (build/make/core/package_internal.mk:466), so pointing this one variable at
# this directory redirects all of them. Naming them again would be redundant
# and would drift the day one of them is renamed upstream.
PRODUCT_DEFAULT_DEV_CERTIFICATE := vendor/lineage-priv/keys/releasekey

# ★ Bluetooth is the one seinfo AOSP does NOT derive from the line above, and
# leaving it alone kills the Bluetooth stack outright.
#
# build/make/core/config.mk:863-869 picks the bluetooth certificate directory
# from a three-way chain:
#
#   ifdef PRODUCT_MAINLINE_BLUETOOTH_SEPOLICY_DEV_CERTIFICATES   <- this
#   else ifneq (,$(filter com.google.android.bt,$(PRODUCT_PACKAGES)))
#   else  := $(dir build/make/target/product/security/testkey)   <- hardcoded AOSP
#
# This product ships AOSP's com.android.bt, not Google's com.google.android.bt,
# so without this line the third branch wins and mac_permissions.xml records the
# AOSP bluetooth certificate. The APK does not follow: `LOCAL_CERTIFICATE :=
# bluetooth` is rewritten against $(dir $(DEFAULT_SYSTEM_DEV_CERTIFICATE))
# (package_internal.mk:466), so Bluetooth.apk is signed with the key in THIS
# directory. Signed with one certificate, authorised against another.
#
# The failure is not a denial and not a boot loop — the app never reaches a
# policy decision. It gets seinfo `default` instead of `bluetooth`, no
# seapp_contexts rule matches AID_BLUETOOTH (1002) with that seinfo, and zygote
# aborts before the process exists:
#
#   F zygote64: JNI FatalError called: (com.android.bluetooth)
#     selinux_android_setcontext(1002, 0,
#       "default:privapp:targetSdkVersion=36:partition=system:complete",
#       "com.android.bluetooth") failed
#
# Symptom on the device: the Bluetooth toggle simply will not turn on, with
# nothing in the log that mentions certificates.
#
# ⚠️ Only bluetooth needs naming. @PLATFORM, @MEDIA and @SHARED resolve through
# $DEFAULT_SYSTEM_DEV_CERTIFICATE, and @NETWORK_STACK, @NFC and @SDK_SANDBOX
# through $MAINLINE_SEPOLICY_DEV_CERTIFICATES, which config.mk:856-859 already
# defaults to $(dir $(DEFAULT_SYSTEM_DEV_CERTIFICATE)) — this directory.
PRODUCT_MAINLINE_BLUETOOTH_SEPOLICY_DEV_CERTIFICATES := vendor/lineage-priv/keys
