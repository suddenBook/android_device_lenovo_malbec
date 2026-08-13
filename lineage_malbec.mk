#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

# ★ The third half of MALBEC_BRINGUP, and it has to be HERE — above the
# common_full_tablet_wifionly.mk line below, not in BoardConfig.mk and not in
# device.mk.
#
# The level table in BoardConfig.mk promises three things at levels 0 and 1:
# permissive-or-not, `service.adb.root=1`, and WITH_ADB_INSECURE. Until session 25
# the tree implemented only the first two; the third lived solely in
# work/scripts/40-build.sh, which is not part of the published device tree. So
# `MALBEC_BRINGUP=1 mka bacon` — the build command README.md itself documents —
# produced **enforcing SELinux with no usable shell**: exactly the hazard the level
# table exists to prevent, reached by accident instead of by going 0 -> 2.
#
# Why it cannot be adb root alone: with WITH_ADB_INSECURE unset,
# vendor/lineage/config/common.mk:35-45 emits `ro.adb.secure=1` AND sets
# PRODUCT_NOT_DEBUGGABLE_IN_USERDEBUG, which becomes `ro.debuggable=0`.
# packages/modules/adb/daemon/main.cpp:66-96 only keeps root
# `if (ro_debuggable && adb_root)` — so `service.adb.root=1` goes inert, and on top
# of that adb needs an on-screen authorisation dialog that a device which has not
# finished booting can never show (#33).
#
# ⚠️ The placement is load-bearing and the old README claim about it was wrong. It
# said WITH_ADB_INSECURE "cannot live in this tree" because common.mk tests it with
# `ifdef` during product-config parsing. The premise is true; the conclusion only
# rules out BoardConfig.mk (parsed after) and device.mk (inherited at the bottom of
# this file, i.e. after common.mk). It does NOT rule out this spot. The chain is
#
#   lineage_malbec.mk:below -> common_full_tablet_wifionly.mk -> common_mobile_full.mk
#                           -> common_mobile.mk -> common.mk   <- the ifdef
#
# and `inherit-product` includes its target immediately, so anything set above that
# line is visible to it.
#
# ⚠️ Aliases spelled out, and no reuse of BoardConfig.mk's normalisation, for the
# same reason device.mk:1444 spells them out: BoardConfig.mk has not been parsed
# yet, so `?=` and the true/false rewriting there are not visible. An unset
# variable filters to nothing and gets the release posture, which is the correct
# default for anything shipped.
ifneq (,$(filter 0 1 true,$(MALBEC_BRINGUP)))
WITH_ADB_INSECURE := true
endif

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

# ── Google Mobile Services ──────────────────────────────────────────────────
#
# Owner's decision (session 23): this device ships with full GMS.
#
# ⚠️ WITH_GMS := true is NOT the switch here, and that is not an oversight.
# LineageOS's own mechanism is vendor/lineage/config/partner_gms.mk, which is
# gated `ifeq ($(WITH_GMS),true)` and then `ifneq (,$(wildcard
# vendor/partner_gms))` — a guard added by change 379220 precisely so the switch
# is a silent no-op when the payload is absent. And it is absent: partner_gms is
# a LineageOS *partner* repository, not in the public manifest
# (`grep partner_gms .repo/manifests/*.xml` is empty) and
# github.com/LineageOS/android_vendor_partner_gms is not a public repo. Setting
# WITH_GMS with no partner_gms would change exactly two things — it would drop
# the /product, /system and /system_ext sideload headroom in
# BoardConfigReservedSize.mk:8, and set WITH_GMS_COMMS_SUITE in telephony.mk,
# which this Wi-Fi-only product does not inherit — and it would arm a trap: the
# day someone does drop a real vendor/partner_gms in, BOTH it and MindTheGapps
# would be inherited and GmsCore/Phonesky would collide.
#
# So GMS comes from MindTheGapps, which is the maintained public equivalent and
# is structured the same way: a vendor tree you inherit one *-vendor.mk from.
# Branch `baklava` = Android 16, which is what LineageOS 23.x is.
#
# ⚠️ THE TWO NAMESPACES BELOW HAVE TO BE DECLARED HERE. MindTheGapps' generated
# arm64-vendor.mk and common-vendor.mk both do
#
#     PRODUCT_SOONG_NAMESPACES += $(LOCAL_PATH)
#
# without ever setting LOCAL_PATH — unlike our own extract-utils output, which
# writes the path literally (vendor/lenovo/malbec/malbec-vendor.mk:6). In a
# product-config context LOCAL_PATH is whatever the last makefile left behind,
# which here is device/lenovo/malbec (device.mk:6), so both lines would export
# the device tree a second time and NEITHER gapps namespace would be exported.
# Every module in them — GmsCore, Phonesky, Velvet, SetupWizard — would then fail
# to resolve. arm64/Android.bp:3 and common/Android.bp:3 are the real
# soong_namespace declarations.
#
# (vendor/gapps/overlay deliberately not listed: it has no Android.bp of its own,
# so the four Gms*Overlay RROs are in the ROOT namespace and need no export.
# common-vendor.mk adds it anyway; that line is theirs, not ours.)
PRODUCT_SOONG_NAMESPACES += \
    vendor/gapps/arm64 \
    vendor/gapps/common

$(call inherit-product, vendor/gapps/arm64/arm64-vendor.mk)

# ⚠️ TWO SETUP WIZARDS SHIP, AND THAT IS CORRECT — DO NOT TRY TO REMOVE ONE.
#
# MindTheGapps installs Google's com.google.android.setupwizard to
# /system_ext/priv-app/SetupWizard, and vendor/lineage/config/common.mk:157 adds
# LineageSetupWizard (org.lineageos.setupwizard) unconditionally for every
# non-automotive product. Neither module's `overrides:` names the other — both
# name AOSP's `Provision` (packages/apps/SetupWizard/Android.bp:17 and
# vendor/gapps/arm64/Android.bp:49) — so both are installed and both declare
# MAIN/HOME.
#
# LineageSetupWizard resolves this itself at runtime.
# SetupWizardApp.onCreate() (:69-71) does
#
#     if (SetupWizardUtils.hasGMS(this)) SetupWizardUtils.disableHome(this);
#
# and hasGMS (SetupWizardUtils.java:144-158) is true only when BOTH
# com.google.android.gms and com.google.android.setupwizard are installed and
# the latter is not disabled. disableHome (:234-241) then sets its own
# MAIN/HOME activity to COMPONENT_ENABLED_STATE_DISABLED, so Google's wizard
# owns the flow from the next resolution onward.
#
# ⚠️ A filter-out here would not work even if it were needed, and it is worth
# writing down why: `inherit-product` does not append values, it appends an
# INHERIT_TAG marker per variable (build/make/core/product.mk), and the real
# lists are only resolved after every product makefile has been read. So
# `PRODUCT_PACKAGES := $(filter-out LineageSetupWizard,$(PRODUCT_PACKAGES))`
# placed here filters a list that does not contain the name yet, silently
# succeeds, and changes nothing. Verified: with that line in place,
# `get_build_var PRODUCT_PACKAGES` still contained LineageSetupWizard.

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
# ⚠️ THE ACCEPTANCE TEST BELOW WAS ALREADY ANSWERED, AND THE ANSWER IS "IT STILL
# DOES". Corrected session 36 — this paragraph had been handing every reader a
# test whose result was recorded in OPEN-ISSUES #57 sessions earlier, so `grep`
# kept returning a falsified claim as the current reasoning.
#
#   Verify after first boot: `adb shell getprop ro.build.fingerprint` should
#   match the value below on every partition  ← ✔ TRUE, verified session 36 on
#   all nine partitions, including ro.bootimage/system_dlkm/vendor_dlkm.
#
#   …and Play Store should not show the uncertified dialog. If it still does,
#   this block is not earning its keep and should go.  ← ✘ WRONG CONCLUSION.
#
# It still does, and that does NOT mean the block should go. #57 measured it:
# Play Protect certification is not earned by the fingerprint at all. It is
# earned by REGISTERING THE GSF ANDROID ID at google.com/android/uncertified,
# which is a per-device, per-`/data`-wipe manual step the owner has to do with
# their own Google account. Official LineageOS builds are identical in this.
#
# ★ What the spoof does buy, per the three bullets above: the device reports a
# fingerprint Google's certified-device list recognises, so once the GSF ID is
# registered the dialog goes away and stays away. Without it, the device reports
# an unknown fingerprint and no registration can help.
#
# ⇒ The block IS earning its keep. Do not delete it because the dialog is
# showing; check whether the GSF ID has been registered since the last wipe.
PRODUCT_SYSTEM_NAME := TB390FU_EEA
PRODUCT_SYSTEM_DEVICE := TB390FU

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildDesc="TB390FU-user 16 BQ2A.250610.001-BP2A.250605.031.A3 18.0.10.335_260618 release-keys" \
    BuildFingerprint=Lenovo/TB390FU_EEA/TB390FU:16/BQ2A.250610.001-BP2A.250605.031.A3/ZUI_18.0.10.335_260618_ROW:user/release-keys \
    DeviceName=$(PRODUCT_SYSTEM_DEVICE) \
    DeviceProduct=$(PRODUCT_SYSTEM_NAME)

PRODUCT_GMS_CLIENTID_BASE := android-lenovo
