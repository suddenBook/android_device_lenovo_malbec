#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

from extract_utils.fixups_blob import (
    blob_fixup,
    blob_fixups_user_type,
)
from extract_utils.fixups_lib import (
    lib_fixups,
    lib_fixups_user_type,
)
from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)

# Must cover every QTI soong namespace this product enables, otherwise a blob
# that depends on a source module in one of them cannot resolve the name.
# The authoritative list is the PRODUCT_SOONG_NAMESPACES that lunch prints;
# hardware/qcom-caf/wlan/qcwcn in particular is its own namespace, separate from
# hardware/qcom-caf/wlan, and leaving it out made cnss_diag fail to find
# libwifi-hal-ctrl.
namespace_imports = [
    'device/lenovo/malbec',
    'hardware/qcom-caf/bootctrl',
    'hardware/qcom-caf/sm8750',
    'hardware/qcom-caf/sm8750/data-ipa-cfg-mgr',
    'hardware/qcom-caf/thermal',
    'hardware/qcom-caf/wlan',
    'hardware/qcom-caf/wlan/qcwcn',
    'vendor/qcom/opensource/audio-hal/st-hal-ar',
    'vendor/qcom/opensource/commonsys-intf/display',
    'vendor/qcom/opensource/commonsys/display',
    'vendor/qcom/opensource/dataservices',
    'vendor/qcom/opensource/display',
]


# Libraries this vendor image ships under a name the PixelOS tree also builds
# from source. Soong's prebuilt/source replacement would otherwise let the stock
# copy take over the tree's module wholesale (android/prebuilt.go: usePrebuilt ->
# ReplacedByPrebuilt), and it aborts outright when the two disagree about which
# partition they install to:
#
#   error: partition is different: system(X) != vendor(prebuilt_X)
#
# proprietary-files.txt renames the blob with ;MODULE_SUFFIX= so it installs as
# X.so from a module called something else, and the fixup below points every
# vendor-side dependant at that renamed module. The two have to stay in step:
# extract-utils applies lib_fixups to dependency lists only and never renames a
# module, so a name here with no matching MODULE_SUFFIX tag is a dangling
# reference.
#
# Mostly the new name is X_vendor. Two libraries take X_vendor_blob because the
# stock image already ships a real file called X_vendor.so, whose own natural
# module name is X_vendor.
#
# Generated together with proprietary-files.txt by
# work/scripts/12-gen-proprietary-files.py from
# work/analysis/blob-vendor-suffixed.txt. Do not hand-edit.
VENDOR_LIB_RENAMES = {
    'android.frameworks.sensorservice@1.0': 'android.frameworks.sensorservice@1.0_vendor',
    'android.hardware.audio.common@5.0': 'android.hardware.audio.common@5.0_vendor',
    'android.hardware.authsecret@1.0': 'android.hardware.authsecret@1.0_vendor',
    'android.hardware.automotive.vehicle@2.0': 'android.hardware.automotive.vehicle@2.0_vendor',
    'android.hardware.bluetooth.audio@2.0': 'android.hardware.bluetooth.audio@2.0_vendor',
    'android.hardware.bluetooth.audio@2.1': 'android.hardware.bluetooth.audio@2.1_vendor',
    'android.hardware.bluetooth@1.0': 'android.hardware.bluetooth@1.0_vendor',
    'android.hardware.boot@1.0': 'android.hardware.boot@1.0_vendor',
    'android.hardware.boot@1.1': 'android.hardware.boot@1.1_vendor',
    'android.hardware.gatekeeper@1.0': 'android.hardware.gatekeeper@1.0_vendor',
    'android.hardware.graphics.allocator@2.0': 'android.hardware.graphics.allocator@2.0_vendor',
    'android.hardware.graphics.allocator@3.0': 'android.hardware.graphics.allocator@3.0_vendor',
    'android.hardware.graphics.allocator@4.0': 'android.hardware.graphics.allocator@4.0_vendor',
    'android.hardware.graphics.bufferqueue@1.0': 'android.hardware.graphics.bufferqueue@1.0_vendor',
    'android.hardware.graphics.bufferqueue@2.0': 'android.hardware.graphics.bufferqueue@2.0_vendor',
    'android.hardware.graphics.common@1.0': 'android.hardware.graphics.common@1.0_vendor',
    'android.hardware.graphics.common@1.1': 'android.hardware.graphics.common@1.1_vendor',
    'android.hardware.graphics.common@1.2': 'android.hardware.graphics.common@1.2_vendor',
    'android.hardware.graphics.composer@2.1': 'android.hardware.graphics.composer@2.1_vendor',
    'android.hardware.graphics.composer@2.2': 'android.hardware.graphics.composer@2.2_vendor',
    'android.hardware.graphics.composer@2.3': 'android.hardware.graphics.composer@2.3_vendor',
    'android.hardware.graphics.mapper@2.0': 'android.hardware.graphics.mapper@2.0_vendor',
    'android.hardware.graphics.mapper@2.1': 'android.hardware.graphics.mapper@2.1_vendor',
    'android.hardware.graphics.mapper@3.0': 'android.hardware.graphics.mapper@3.0_vendor',
    'android.hardware.graphics.mapper@4.0': 'android.hardware.graphics.mapper@4.0_vendor',
    'android.hardware.health@1.0': 'android.hardware.health@1.0_vendor',
    'android.hardware.health@2.0': 'android.hardware.health@2.0_vendor',
    'android.hardware.health@2.1': 'android.hardware.health@2.1_vendor',
    'android.hardware.keymaster@3.0': 'android.hardware.keymaster@3.0_vendor',
    'android.hardware.keymaster@4.0': 'android.hardware.keymaster@4.0_vendor',
    'android.hardware.keymaster@4.1': 'android.hardware.keymaster@4.1_vendor',
    'android.hardware.media.bufferpool@2.0': 'android.hardware.media.bufferpool@2.0_vendor',
    'android.hardware.media.c2@1.0': 'android.hardware.media.c2@1.0_vendor',
    'android.hardware.media.c2@1.1': 'android.hardware.media.c2@1.1_vendor',
    'android.hardware.media.c2@1.2': 'android.hardware.media.c2@1.2_vendor',
    'android.hardware.media.omx@1.0': 'android.hardware.media.omx@1.0_vendor',
    'android.hardware.media@1.0': 'android.hardware.media@1.0_vendor',
    'android.hardware.power@1.0': 'android.hardware.power@1.0_vendor',
    'android.hardware.power@1.1': 'android.hardware.power@1.1_vendor',
    'android.hardware.power@1.2': 'android.hardware.power@1.2_vendor',
    'android.hardware.radio@1.0': 'android.hardware.radio@1.0_vendor',
    'android.hardware.radio@1.1': 'android.hardware.radio@1.1_vendor',
    'android.hardware.renderscript@1.0': 'android.hardware.renderscript@1.0_vendor',
    'android.hardware.sensors@1.0': 'android.hardware.sensors@1.0_vendor',
    'android.hardware.sensors@2.0': 'android.hardware.sensors@2.0_vendor',
    'android.hardware.sensors@2.0-ScopedWakelock': 'android.hardware.sensors@2.0-ScopedWakelock_vendor',
    'android.hardware.sensors@2.1': 'android.hardware.sensors@2.1_vendor',
    'android.hardware.thermal@1.0': 'android.hardware.thermal@1.0_vendor',
    'android.hardware.thermal@2.0': 'android.hardware.thermal@2.0_vendor',
    'android.hardware.usb.gadget@1.0': 'android.hardware.usb.gadget@1.0_vendor',
    'android.hardware.usb.gadget@1.1': 'android.hardware.usb.gadget@1.1_vendor',
    'android.hidl.allocator@1.0': 'android.hidl.allocator@1.0_vendor',
    'android.hidl.memory.token@1.0': 'android.hidl.memory.token@1.0_vendor',
    'android.hidl.memory@1.0': 'android.hidl.memory@1.0_vendor',
    'android.hidl.safe_union@1.0': 'android.hidl.safe_union@1.0_vendor',
    'android.hidl.token@1.0': 'android.hidl.token@1.0_vendor',
    'android.hidl.token@1.0-utils': 'android.hidl.token@1.0-utils_vendor',
    'android.system.wifi.keystore@1.0': 'android.system.wifi.keystore@1.0_vendor',
    'com.dsi.ant@1.0': 'com.dsi.ant@1.0_vendor',
    'libRSCpuRef': 'libRSCpuRef_vendor',
    'libRSDriver': 'libRSDriver_vendor',
    'libRS_internal': 'libRS_internal_vendor',
    'libaconfig_storage_read_api_cc': 'libaconfig_storage_read_api_cc_vendor',
    'libalsautils': 'libalsautils_vendor',
    'libalsautilsv2': 'libalsautilsv2_vendor',
    'libandroid_runtime_lazy': 'libandroid_runtime_lazy_vendor',
    'libaudio_aidl_conversion_common_ndk': 'libaudio_aidl_conversion_common_ndk_vendor',
    'libaudioaidlcommon': 'libaudioaidlcommon_vendor',
    'libaudioroute': 'libaudioroute_vendor',
    'libavservices_minijail': 'libavservices_minijail_vendor',
    'libbcinfo': 'libbcinfo_vendor',
    'libbinderdebug': 'libbinderdebug_vendor',
    'libblas': 'libblas_vendor',
    'libcamera_metadata': 'libcamera_metadata_vendor',
    'libcap': 'libcap_vendor',
    'libcodec2': 'libcodec2_vendor',
    'libcodec2_aidl': 'libcodec2_aidl_vendor',
    'libcodec2_hal_common': 'libcodec2_hal_common_vendor',
    'libcodec2_hidl@1.0': 'libcodec2_hidl@1.0_vendor',
    'libcodec2_hidl@1.1': 'libcodec2_hidl@1.1_vendor',
    'libcodec2_hidl@1.2': 'libcodec2_hidl@1.2_vendor',
    'libcodec2_soft_common': 'libcodec2_soft_common_vendor',
    'libcodec2_vndk': 'libcodec2_vndk_vendor',
    'libcompiler_rt': 'libcompiler_rt_vendor',
    'libcurl': 'libcurl_vendor',
    'libdmabufheap': 'libdmabufheap_vendor',
    'libdrm': 'libdrm_vendor',
    'libeffectsconfig': 'libeffectsconfig_vendor',
    'libexif': 'libexif_vendor',
    'libexpat': 'libexpat_vendor',
    'libflatbuffers-cpp': 'libflatbuffers-cpp_vendor',
    'libfmq': 'libfmq_vendor',
    'libgatekeeper': 'libgatekeeper_vendor',
    'libgralloctypes': 'libgralloctypes_vendor',
    'libhardware': 'libhardware_vendor',
    'libhardware_legacy': 'libhardware_legacy_vendor',
    'libhidlmemory': 'libhidlmemory_vendor',
    'libhidltransport': 'libhidltransport_vendor',
    'libhwbinder': 'libhwbinder_vendor',
    'libion': 'libion_vendor',
    'libjsoncpp': 'libjsoncpp_vendor',
    'libkeymaster_messages': 'libkeymaster_messages_vendor',
    'liblzma': 'liblzma_vendor',
    'libmedia_helper': 'libmedia_helper_vendor',
    'libmediautils_vendor': 'libmediautils_vendor_vendor',
    'libmemunreachable': 'libmemunreachable_vendor',
    'libminijail': 'libminijail_vendor',
    'libnetutils': 'libnetutils_vendor',
    'libpng': 'libpng_vendor',
    'libpower': 'libpower_vendor',
    'libpsi': 'libpsi_vendor',
    'libqti_vndfwk_detect': 'libqti_vndfwk_detect_vendor_blob',
    'libsfplugin_ccodec_utils': 'libsfplugin_ccodec_utils_vendor',
    'libspeexresampler': 'libspeexresampler_vendor',
    'libsqlite': 'libsqlite_vendor',
    'libstagefright_aidl_bufferpool2': 'libstagefright_aidl_bufferpool2_vendor',
    'libstagefright_bufferpool@2.0.1': 'libstagefright_bufferpool@2.0.1_vendor',
    'libstagefright_bufferqueue_helper': 'libstagefright_bufferqueue_helper_vendor',
    'libstagefright_foundation': 'libstagefright_foundation_vendor',
    'libtensorflowlite_c': 'libtensorflowlite_c_vendor',
    'libtinyalsa': 'libtinyalsa_vendor',
    'libtinyalsav2': 'libtinyalsav2_vendor',
    'libtinyxml2': 'libtinyxml2_vendor',
    'libui': 'libui_vendor',
    'libunwindstack': 'libunwindstack_vendor',
    'libusbhost': 'libusbhost_vendor',
    'libvibratorutils': 'libvibratorutils_vendor',
    'libvndfwk_detect_jni.qti': 'libvndfwk_detect_jni.qti_vendor_blob',
    'libwifi-system-iface': 'libwifi-system-iface_vendor',
    'libxml2': 'libxml2_vendor',
    'server_configurable_flags': 'server_configurable_flags_vendor',
    'vendor.display.config@1.0': 'vendor.display.config@1.0_vendor',
    'vendor.display.config@1.1': 'vendor.display.config@1.1_vendor',
    'vendor.display.config@1.10': 'vendor.display.config@1.10_vendor',
    'vendor.display.config@1.11': 'vendor.display.config@1.11_vendor',
    'vendor.display.config@1.2': 'vendor.display.config@1.2_vendor',
    'vendor.display.config@1.3': 'vendor.display.config@1.3_vendor',
    'vendor.display.config@1.4': 'vendor.display.config@1.4_vendor',
    'vendor.display.config@1.5': 'vendor.display.config@1.5_vendor',
    'vendor.display.config@1.6': 'vendor.display.config@1.6_vendor',
    'vendor.display.config@1.7': 'vendor.display.config@1.7_vendor',
    'vendor.display.config@1.8': 'vendor.display.config@1.8_vendor',
    'vendor.display.config@1.9': 'vendor.display.config@1.9_vendor',
    'vendor.display.config@2.0': 'vendor.display.config@2.0_vendor',
    'vendor.qti.diaghal@1.0': 'vendor.qti.diaghal@1.0_vendor',
    'vendor.qti.hardware.bluetooth_audio@2.0': 'vendor.qti.hardware.bluetooth_audio@2.0_vendor',
    'vendor.qti.hardware.bluetooth_audio@2.1': 'vendor.qti.hardware.bluetooth_audio@2.1_vendor',
    'vendor.qti.hardware.display.allocator@1.0': 'vendor.qti.hardware.display.allocator@1.0_vendor',
    'vendor.qti.hardware.display.allocator@3.0': 'vendor.qti.hardware.display.allocator@3.0_vendor',
    'vendor.qti.hardware.display.allocator@4.0': 'vendor.qti.hardware.display.allocator@4.0_vendor',
    'vendor.qti.hardware.display.composer@1.0': 'vendor.qti.hardware.display.composer@1.0_vendor',
    'vendor.qti.hardware.display.composer@2.0': 'vendor.qti.hardware.display.composer@2.0_vendor',
    'vendor.qti.hardware.display.mapper@1.0': 'vendor.qti.hardware.display.mapper@1.0_vendor',
    'vendor.qti.hardware.display.mapper@1.1': 'vendor.qti.hardware.display.mapper@1.1_vendor',
    'vendor.qti.hardware.display.mapper@2.0': 'vendor.qti.hardware.display.mapper@2.0_vendor',
    'vendor.qti.hardware.display.mapper@3.0': 'vendor.qti.hardware.display.mapper@3.0_vendor',
    'vendor.qti.hardware.display.mapper@4.0': 'vendor.qti.hardware.display.mapper@4.0_vendor',
    'vendor.qti.hardware.display.mapperextensions@1.0': 'vendor.qti.hardware.display.mapperextensions@1.0_vendor',
    'vendor.qti.hardware.display.mapperextensions@1.1': 'vendor.qti.hardware.display.mapperextensions@1.1_vendor',
    'vendor.qti.hardware.display.mapperextensions@1.2': 'vendor.qti.hardware.display.mapperextensions@1.2_vendor',
    'vendor.qti.hardware.display.mapperextensions@1.3': 'vendor.qti.hardware.display.mapperextensions@1.3_vendor',
    'vendor.qti.hardware.perf@2.0': 'vendor.qti.hardware.perf@2.0_vendor',
    'vendor.qti.hardware.perf@2.1': 'vendor.qti.hardware.perf@2.1_vendor',
    'vendor.qti.hardware.perf@2.2': 'vendor.qti.hardware.perf@2.2_vendor',
    'vendor.qti.hardware.servicetracker@1.0': 'vendor.qti.hardware.servicetracker@1.0_vendor',
    'vendor.qti.hardware.servicetracker@1.1': 'vendor.qti.hardware.servicetracker@1.1_vendor',
    'vendor.qti.hardware.systemhelper@1.0': 'vendor.qti.hardware.systemhelper@1.0_vendor',
    'vendor.qti.hardware.wifidisplaysession_aidl-V1-ndk': 'vendor.qti.hardware.wifidisplaysession_aidl-V1-ndk_vendor',
    'vendor.qti.qccsyshal_aidl-V1-ndk': 'vendor.qti.qccsyshal_aidl-V1-ndk_vendor',
    'vendor.qti.qccvndhal_aidl-V1-ndk': 'vendor.qti.qccvndhal_aidl-V1-ndk_vendor',
    'wifi_legacy': 'wifi_legacy_vendor',
}


def lib_fixup_vendor_rename(lib: str, partition: str, *args, **kwargs):
    return VENDOR_LIB_RENAMES[lib] if partition == 'vendor' else None


lib_fixups: lib_fixups_user_type = {
    **lib_fixups,
    tuple(VENDOR_LIB_RENAMES): lib_fixup_vendor_rename,
}

# These are the prebuilts that do not link against this tree as shipped. Each
# one is here because the build said so, not because another device tree had it.
#
# All of them are the same shape: the stock binary was compiled against an older
# frozen version of an AIDL interface than the one this tree currently builds,
# and Soong refuses to put both versions in a single dependency graph:
#
#   module "libqdMetaData": depends on multiple versions of the same
#   aidl_interface: android.hardware.graphics.common-V5-ndk-source,
#   android.hardware.graphics.common-V7-ndk-source
#
# The version to rewrite to is the interface's current one, which is
# nextVersion() = highest frozen + 1 (system/tools/aidl/build/aidl_interface.go).
# graphics.common is frozen through 6 with frozen: false, so 7; drm is frozen
# through 1, so 2.
#
# Worth noting how small this list is. The whole stock vendor image links 175
# AIDL interfaces and only these two are at a version this tree no longer
# builds — onyx needs several dozen replace_needed entries for the same job.
# These are the prebuilts that do not link against this tree as shipped. Every
# one is here because the build said so, not because another device tree had it.
#
# They are all the same shape: the stock binary was compiled against an older
# version of a stable AIDL interface than the one this tree resolves to, and
# Soong refuses to put two versions of one interface in a single dependency
# graph:
#
#   module "libqdMetaData": depends on multiple versions of the same
#   aidl_interface: android.hardware.graphics.common-V5-ndk-source,
#   android.hardware.graphics.common-V7-ndk-source
#
# Which version to rewrite *to* is the part worth writing down, because it is
# not simply "the newest". A dependant resolves to
#
#     max(versions) + (0 if frozen else 1)
#
# — `nextVersion` is always built (system/tools/aidl/build/aidl_interface.go
# creates it unconditionally), but only an interface with `frozen: false` has
# its dependants pointed at it. So graphics.common (frozen: false, through 6)
# resolves to V7 while audio.effect (frozen: true, through 3) resolves to V3.
# Getting this backwards costs a full re-extract per guess.
#
# The one exception is vendor.qti.hardware.display.config: it is frozen through
# 15, but hardware/qcom-caf/sm8750/display/hal/composer/Android.bp pins
# "-V12-ndk" explicitly, so V12 is what the graph actually contains.
#
# The list is generated by reading DT_NEEDED out of the *pristine* dump under
# work/unpacked/parts, not out of vendor/lenovo/malbec/proprietary — the latter
# has already been patched by a previous run, so scanning it silently
# under-reports and the next re-extract regresses.
blob_fixups: blob_fixups_user_type = {
    (
        'system_ext/lib64/libwfddisplayconfig.so',
        'vendor/bin/qguard',
        'vendor/lib64/libaodoptfeature.so',
        'vendor/lib64/libapengine.so',
        'vendor/lib64/libcamerapoweroptfeature.so',
        'vendor/lib64/libgamepoweroptfeature.so',
        'vendor/lib64/liboffscreenpoweroptfeature.so',
        'vendor/lib64/libpsmoptfeature.so',
        'vendor/lib64/libqcodec2_utils.so',
        'vendor/lib64/libqti-perfd.so',
        'vendor/lib64/libvideooptfeature.so',
        'vendor/lib64/libwfddisplayconfig_vendor.so',
    ): blob_fixup()
        .replace_needed(
            'vendor.qti.hardware.display.config-V5-ndk.so',
            'vendor.qti.hardware.display.config-V12-ndk.so',
        ),

    (
        'vendor/bin/TrustedUISampleTAClient',
        'vendor/bin/TrustedUISampleTestAIDL',
        'vendor/bin/trusteduilistener',
        'vendor/lib64/libTrustedUIAIDL.so',
        'vendor/lib64/liboemcrypto.so',
        'vendor/lib64/libops.so',
    ): blob_fixup()
        .replace_needed(
            'vendor.qti.hardware.display.config-V7-ndk.so',
            'vendor.qti.hardware.display.config-V12-ndk.so',
        ),

    'vendor/bin/wfdhdcphalservice': blob_fixup()
        .replace_needed(
            'android.hardware.drm-V1-ndk.so',
            'android.hardware.drm-V2-ndk.so',
        ),

    (
        'vendor/lib64/android.hardware.bluetooth.audio-impl.so',
        'vendor/lib64/btaudio_offload_if.so',
        'vendor/lib64/hw/android.hardware.bluetooth.audio-impl-qti.so',
        'vendor/lib64/hw/audio.bluetooth_qti.default.so',
        'vendor/lib64/libbluetooth_audio_session_aidl_qti.so',
    ): blob_fixup()
        .replace_needed(
            'android.hardware.bluetooth.audio-V4-ndk.so',
            'android.hardware.bluetooth.audio-V5-ndk.so',
        ),

    (
        'vendor/lib64/camera/components/com.qti.node.dewarp.so',
        'vendor/lib64/hw/com.qti.chi.override.so',
        'vendor/lib64/libchifeature2.so',
        'vendor/lib64/vendor.qti.hardware.camera.offlinecamera-service-impl.so',
    ): blob_fixup()
        .replace_needed(
            'android.hardware.graphics.allocator-V1-ndk.so',
            'android.hardware.graphics.allocator-V2-ndk.so',
        ),

    (
        'vendor/lib64/ftm_fm_lib.so',
        'vendor/lib64/libbt-hidlclient.so',
    ): blob_fixup()
        .replace_needed(
            'android.hardware.bluetooth.audio-V3-ndk.so',
            'android.hardware.bluetooth.audio-V5-ndk.so',
        ),

    'vendor/lib64/hw/android.hardware.bluetooth.audio_sw.so': blob_fixup()
        .replace_needed(
            'android.hardware.audio.core-V2-ndk.so',
            'android.hardware.audio.core-V4-ndk.so',
        )
        .replace_needed(
            'android.hardware.bluetooth.audio-V4-ndk.so',
            'android.hardware.bluetooth.audio-V5-ndk.so',
        )
        .replace_needed(
            'android.media.audio.common.types-V3-ndk.so',
            'android.media.audio.common.types-V4-ndk.so',
        ),

    (
        'vendor/lib64/lenovo.hardware.ai-V1-ndk.so',
        'vendor/lib64/libgralloctypes.so',
        'vendor/lib64/libqcodec2_core.so',
        'vendor/lib64/libui.so',
    ): blob_fixup()
        .replace_needed(
            'android.hardware.graphics.common-V5-ndk.so',
            'android.hardware.graphics.common-V7-ndk.so',
        ),

    'vendor/lib64/libaudio_aidl_conversion_common_ndk.so': blob_fixup()
        .replace_needed(
            'android.hardware.audio.common-V3-ndk.so',
            'android.hardware.audio.common-V4-ndk.so',
        )
        .replace_needed(
            'android.media.audio.common.types-V3-ndk.so',
            'android.media.audio.common.types-V4-ndk.so',
        ),

    'vendor/lib64/libcamximageformatutils.so': blob_fixup()
        .replace_needed(
            'android.hardware.graphics.allocator-V1-ndk.so',
            'android.hardware.graphics.allocator-V2-ndk.so',
        )
        .replace_needed(
            'vendor.qti.hardware.display.config-V2-ndk.so',
            'vendor.qti.hardware.display.config-V12-ndk.so',
        ),

    (
        'vendor/lib64/libqtigefar.so',
        'vendor/lib64/libsxrservice.so',
    ): blob_fixup()
        .replace_needed(
            'android.hardware.audio.core-V2-ndk.so',
            'android.hardware.audio.core-V4-ndk.so',
        )
        .replace_needed(
            'android.media.audio.common.types-V3-ndk.so',
            'android.media.audio.common.types-V4-ndk.so',
        ),

    'vendor/lib64/libqvrservice.so': blob_fixup()
        .replace_needed(
            'android.hardware.audio.core-V2-ndk.so',
            'android.hardware.audio.core-V4-ndk.so',
        )
        .replace_needed(
            'android.hardware.graphics.allocator-V1-ndk.so',
            'android.hardware.graphics.allocator-V2-ndk.so',
        )
        .replace_needed(
            'android.media.audio.common.types-V3-ndk.so',
            'android.media.audio.common.types-V4-ndk.so',
        ),

    'vendor/lib64/libwfdmmsrc_proprietary.so': blob_fixup()
        .replace_needed(
            'android.hardware.audio.core-V2-ndk.so',
            'android.hardware.audio.core-V4-ndk.so',
        )
        .replace_needed(
            'android.media.audio.common.types-V2-ndk.so',
            'android.media.audio.common.types-V4-ndk.so',
        ),

    (
        'vendor/lib64/soundfx/libbundleaidl.so',
        'vendor/lib64/soundfx/libdlbvolaidl.so',
        'vendor/lib64/soundfx/libdownmixaidl.so',
        'vendor/lib64/soundfx/libdynamicsprocessingaidl.so',
        'vendor/lib64/soundfx/libloudnessenhanceraidl.so',
        'vendor/lib64/soundfx/libquasar.so',
        'vendor/lib64/soundfx/libreverbaidl.so',
        'vendor/lib64/soundfx/libswdapaidl.so',
        'vendor/lib64/soundfx/libswgamedapaidl.so',
        'vendor/lib64/soundfx/libvisualizeraidl.so',
    ): blob_fixup()
        .replace_needed(
            'android.hardware.audio.effect-V2-ndk.so',
            'android.hardware.audio.effect-V3-ndk.so',
        )
        .replace_needed(
            'android.media.audio.common.types-V3-ndk.so',
            'android.media.audio.common.types-V4-ndk.so',
        ),

}  # fmt: skip

# Firmware images (xbl, tz, abl, modem, hyp, …) are deliberately not shipped
# yet. The factory package names them the Qualcomm way — abl.elf, tz.mbn,
# NON-HLOS.bin — rather than the <partition>.img that extract_utils expects, so
# a firmware list would need a separate renaming step. More importantly, a first
# bringup has no business rewriting the bootloader or TrustZone: the device
# already runs stock firmware, and leaving those partitions alone keeps a
# failed boot recoverable. Revisit once the ROM boots and OTA completeness
# matters.
module = ExtractUtilsModule(
    'malbec',
    'lenovo',
    blob_fixups=blob_fixups,
    lib_fixups=lib_fixups,
    namespace_imports=namespace_imports,
    add_firmware_proprietary_file=False,
)

if __name__ == '__main__':
    utils = ExtractUtils.device(module)
    utils.run()
