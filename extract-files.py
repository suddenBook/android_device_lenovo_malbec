#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

import os

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


def _renamed_vendor_libs():
    """Module names that proprietary-files.txt tags ;MODULE_SUFFIX=_vendor.

    Read straight out of the list instead of being restated here. The two have
    to agree exactly — this fixup rewrites other blobs' dependencies on X to
    X_vendor, and the tag is what makes X_vendor exist — and keeping one copy
    means they cannot drift. A hand-maintained second copy is how the previous
    revision ended up with three dangling references (diaghal-V1-ndk,
    uceaidlservice, ImsRtpService) that resolved to nothing.
    """
    names = set()
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        'proprietary-files.txt')
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            head, _, tags = line.lstrip('-').partition(';')
            if ';MODULE_SUFFIX=_vendor' not in ';' + tags:
                continue
            head = head.split('|')[0].split(':')[0]
            if not head.startswith('vendor/') or not head.endswith('.so'):
                continue
            names.add(os.path.basename(head)[:-len('.so')])
    return tuple(sorted(names))


def lib_fixup_vendor_suffix(lib: str, partition: str, *args, **kwargs):
    return f'{lib}_{partition}' if partition == 'vendor' else None


# These are libraries the stock image ships under a name this tree also defines
# a source module for, but which the tree does not actually install to that
# path (nothing puts them in PRODUCT_PACKAGES). The blob has to provide the
# file, so proprietary-files.txt renames its module with ;MODULE_SUFFIX=_vendor
# and this points vendor-side dependants at the renamed module.
#
# Renaming is only correct *because* the tree installs nothing there. Where the
# tree does install the same path, the blob is dropped instead — see
# work/analysis/tree-installed-paths.txt. An earlier revision renamed both
# cases alike, and the 107 entries in the second group spent the whole time
# fighting the tree for one install path while Make quietly picked a winner.
#
# The partition guard matters: run_libs_fixup (extract_utils/makefiles.py:231)
# passes the *depending* file's partition, so system_ext copies of the same
# library keep resolving to the bare name.
lib_fixups: lib_fixups_user_type = {
    **lib_fixups,
    _renamed_vendor_libs(): lib_fixup_vendor_suffix,
}


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
#
# ⚠️ replace_needed is NOT the default answer, and this list used to treat it as
# if it were. Rewriting a stock binary's DT_NEEDED to a newer version of a
# stable AIDL interface is only safe when that binary is a *client*: for the NDK
# backend, parcelable read/writeFromParcel are emitted out of line into the
# -V<n>-ndk.so (system/tools/aidl/generate_ndk.cpp), while sizeof and field
# offsets are baked into the caller. Appending interface methods keeps client
# vtable indices stable.
#
# It is NOT safe when the blob *implements* the interface (Bn* classes), or when
# the version bump grew a parcelable or a union: the new marshalling code then
# reads and writes past the end of an old-shaped object, and a missing method
# leaves an unfilled vtable slot. Six blobs were being rewritten across exactly
# that boundary — the four soundfx Dolby/Quasar BnEffect libraries
# (audio.effect V3 added Eraser to the Parameter/Descriptor unions),
# android.hardware.bluetooth.audio_sw.so (BnModule/BnStreamIn/BnStreamOut) and
# wfdhdcphalservice (BnDrmFactory/BnDrmPlugin/BnCryptoPlugin; drm V2 adds
# ICryptoPlugin::getKeyHandle).
#
# The correct fix is to leave the binary alone and co-install the interface
# version it was built against — several versions of one aidl_interface may be
# installed side by side, and this tree already does it
# (android.media.audio.common.types V2 and V4 are both installed today).
# device.mk lists those; onyx uses the same approach, and
# vendor/qcom/opensource/commonsys-intf/display installs all fifteen
# display.config versions the same way.
#
# What is left below is only the client-side rewrites, where the bump is safe
# and the alternative would mean co-installing a long tail of display.config and
# graphics.allocator versions for no benefit.
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

    # libgralloctypes / libui / libaudio_aidl_conversion_common_ndk used to be
    # here too. They are no longer extracted — the tree installs its own vendor
    # variant at those paths — so a fixup for them would match nothing.
    # extract_utils does not warn about a blob_fixup that matches no file, so
    # dead entries accumulate silently and eventually someone reasons from a
    # fixup for a file that is not shipped. 19-verify-device-tree.py now checks
    # that every blob_fixups key is still in proprietary-files.txt.
    (
        'vendor/lib64/lenovo.hardware.ai-V1-ndk.so',
        'vendor/lib64/libqcodec2_core.so',
    ): blob_fixup()
        .replace_needed(
            'android.hardware.graphics.common-V5-ndk.so',
            'android.hardware.graphics.common-V7-ndk.so',
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

    'vendor/lib64/libqvrservice.so': blob_fixup()
        .replace_needed(
            'android.hardware.graphics.allocator-V1-ndk.so',
            'android.hardware.graphics.allocator-V2-ndk.so',
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
