#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

from extract_utils.fixups_blob import (
    blob_fixups_user_type,
)
from extract_utils.fixups_lib import (
    lib_fixup_remove,
    lib_fixups,
    lib_fixups_user_type,
)
from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)

namespace_imports = [
    'hardware/qcom-caf/sm8750',
    'hardware/qcom-caf/wlan',
    'vendor/qcom/opensource/commonsys/display',
    'vendor/qcom/opensource/commonsys-intf/display',
    'vendor/qcom/opensource/dataservices',
    'vendor/qcom/opensource/display',
    'device/lenovo/malbec',
]


def lib_fixup_vendor_suffix(lib: str, partition: str, *args, **kwargs):
    return f'{lib}_{partition}' if partition == 'vendor' else None


lib_fixups: lib_fixups_user_type = {
    **lib_fixups,
    # Built from source out of vendor/qcom/opensource, so the blobs must not
    # declare a dependency on a prebuilt copy of them.
    (
        'libagm',
        'libagmclient',
        'libagmmixer',
        'libar-acdb',
        'libar-gsl',
        'libats',
        'liblx-osal',
        'libvui_intf',
    ): lib_fixup_remove,
    # These ship on both partitions; the vendor copy needs the suffix so the
    # two do not collide.
    (
        'vendor.qti.diaghal-V1-ndk',
        'vendor.qti.diaghal@1.0',
        'vendor.qti.hardware.wifidisplaysession_aidl-V1-ndk',
        'vendor.qti.ims.uceaidlservice-V1-ndk',
        'vendor.qti.ImsRtpService-V1-ndk',
        'vendor.qti.qccsyshal_aidl-V1-ndk',
        'vendor.qti.qccvndhal_aidl-V1-ndk',
    ): lib_fixup_vendor_suffix,
}

# Left empty deliberately. Blob fixups are the record of which prebuilts fail
# to link against this tree's libraries, and that list can only be written by
# watching the device fail to boot and reading the linker errors. Adding
# speculative entries copied from another device would hide real breakage.
blob_fixups: blob_fixups_user_type = {}  # fmt: skip

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
