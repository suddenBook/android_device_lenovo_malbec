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

            # Exact tag match, not a substring test. `';MODULE_SUFFIX=_vendor'
            # in ';' + tags` also matches a hypothetical ;MODULE_SUFFIX=_vendorfoo.
            if 'MODULE_SUFFIX=_vendor' not in tags.split(';'):
                continue

            # An entry is [-]src[:dst][|hash]. Strip the hash, then take the
            # DESTINATION when the line renames, because the destination is what
            # decides the module name: extract_utils derives it from file.root,
            # i.e. the destination basename (extract_utils/file.py:134,
            # makefiles.py:142-165).
            #
            # This used to take .split(':')[0], i.e. the source. That is right
            # only while no ;MODULE_SUFFIX= entry also carries a :dst rename,
            # which is true today and is why the bug was invisible. The tree
            # already has a renaming entry of the other kind (;FIX_SONAME on
            # libtensorflowlite_c.so:...libtensorflowlite_c_vendor.so), so the
            # shape exists; the first entry combining the two would have
            # silently produced a fixup pointing at a module that does not
            # exist, and lib_fixups fail open rather than erroring.
            head = head.split('|')[0]
            src, sep, dst = head.partition(':')
            head = dst if sep else src

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
    # The stock libaudioserviceexampleimpl.so wants
    # android::audio_utils::mutex_get_enable_flag(), a symbol Android 15's
    # libaudioutils exported and Android 16 removed. It is among stock's 364
    # exports and absent from the 403 this tree builds.
    #
    # This is a missing SYMBOL, not a missing library, and it is FATAL: the blob
    # carries BIND_NOW / FLAGS_1: NOW, so the dynamic linker resolves everything
    # at load time and an unresolved symbol means dlopen fails outright. Its
    # consumer libaudiocorehal.default.so is mandatory="true" in
    # vendor_audio_interfaces.xml, and Service.cpp:53-77 retries a mandatory
    # library ten times and then LOG_ALWAYS_FATALs — so audiohalservice.qti ends
    # up in an init respawn loop.
    #
    # Switching to the tree's own libaudioserviceexampleimpl is not an option:
    # its vendor variant does not compile here (five objects fail, among them
    # StreamAlsa, ModulePrimary and DevicePortProxy), and the 104 symbols its
    # three consumers take from it are all C++ internals of
    # aidl::...::StreamCommonImpl rather than a stable interface, so swapping in
    # an implementation a major version apart is high risk.
    #
    # Extracting stock's libaudioutils.so alongside it does not work either: the
    # vendor-side libaudioutils has about 30 consumers (24 blobs, 6 tree-built),
    # while the tree's libaudioutils.vendor is pulled in transitively by
    # libalsautilsv2.vendor and friends. The two collide on one path — measured:
    # "partition is different: system(libaudioutils) != vendor(prebuilt_libaudioutils)".
    #
    # The right answer is a one-line shim upstream already ships:
    # hardware/lineage/compat/libaudioutils/mutex.cpp is exactly
    # `bool mutex_get_enable_flag() { return mutex::kDefaultPriorityInheritance; }`.
    # This is the generic problem of an Android 16 port carrying Android 15 audio
    # blobs, not something specific to this device.
    ('vendor/lib64/libaudioserviceexampleimpl.so',): blob_fixup()
        .add_needed('libaudioutils_shim.so'),

    # Miracast (WiFi Display). Exactly the same shape as the line above, and the
    # shim is likewise already upstream — it just was not noticed the first time.
    #
    # libwfdnative.so is WfdService.apk's JNI library. It was compiled against an
    # Android 15 frameworks/native, where MotionEvent::initialize took `int flags`;
    # Android 16 changed that parameter to ftl::Flags<MotionFlag>, so the mangled
    # name it imports no longer exists. Of its 138 undefined symbols, resolved
    # against all 1086 shared libraries this build installs under /system,
    # /system_ext and /apex, that one symbol is the *only* miss.
    #
    # hardware/lineage/compat/libinput/Input.cpp:29-42 defines precisely that old
    # mangled name and forwards to the new one, wrapping the int as
    # ftl::Flags<MotionFlag>(flags). Module `libinput_shim`
    # (hardware/lineage/compat/Android.bp:355-371) is system_ext_specific and
    # 64-bit, i.e. the same partition and linker namespace as libwfdnative.so.
    #
    # ⚠️ Do NOT reach for ;DISABLE_CHECKELF here instead. That silences the build
    # check without providing the symbol, so the library still fails to load at
    # runtime — an installed, permanently broken Miracast, and silently so. The
    # shim actually defines the symbol, which is why this is a fix and that is not.
    #
    # Without these two blobs the whole native WFD stack we already ship (47
    # entries, plus wfd-system-ext-privapp-permissions-qti.xml allowlisting a
    # package that was not installed) is dead weight: wfdservice.rc only starts
    # wfdservice64 `on property:vendor.wfdservice64=enable`, and the APK is what
    # sets that property.
    ('system_ext/lib64/libwfdnative.so',): blob_fixup()
        .add_needed('libinput_shim.so'),

    # ⚠️ libaodoptfeature / libcamerapoweroptfeature / libgamepoweroptfeature /
    # liboffscreenpoweroptfeature / libpsmoptfeature / libvideooptfeature used to
    # be in this tuple. Session 14 removed the whole poweropt cluster (its only
    # loader, vendor/bin/poweropt-service, went in session 11), so those six
    # fixups had nothing left to patch.
    (
        'system_ext/lib64/libwfddisplayconfig.so',
        'vendor/bin/qguard',
        'vendor/lib64/libapengine.so',
        'vendor/lib64/libqcodec2_utils.so',
        'vendor/lib64/libqti-perfd.so',
        'vendor/lib64/libwfddisplayconfig_vendor.so',
    ): blob_fixup()
        .replace_needed(
            'vendor.qti.hardware.display.config-V5-ndk.so',
            'vendor.qti.hardware.display.config-V12-ndk.so',
        ),

    # ⚠️ vendor/bin/TrustedUISampleTAClient used to be here too. Session 14
    # removed it: it is a *sample* client with no .rc stanza and no reference
    # anywhere in the image. trusteduilistener is the real service and stays.
    (
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
    # fixup for a file that is not shipped. The guard is
    # work/scripts/26-orphan-blobs.py, which imports this file, flattens
    # blob_fixups and fails on any key proprietary-files.txt no longer names.
    # ⚠️ These three comments used to cite `19-verify-device-tree.py`, a script
    # that has never existed in this repo — so for five sessions the class they
    # warn about at length had no running check (OPEN-ISSUES.md #76).
    (
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

    # ⚠️ A LAYOUT break, not a version break. Point these at Qualcomm's own
    # private tinyxml2 copy instead of the platform one.
    #
    # AOSP e26130265cbfe70dd26a22648b1c719406ce2c9e "Upgrade tinyxml2 to 11.0.0"
    # turned DynArray's and MemPoolT's `int` members into `size_t`:
    # sizeof(tinyxml2::XMLDocument) went 776 -> 880. Measured twice, two ways:
    # compiling both header revisions, and diffing the member offsets emitted in
    # XMLDocument::~XMLDocument (factory 0x108..0x300, ours 0x110..0x368).
    # The exported symbol sets are identical (231 = 231) because everything that
    # moved is private, so check_elf_file and 33-blob-linkcheck.py cannot see it.
    #
    # These four are Android-15 blobs that construct an XMLDocument BY VALUE
    # (criterion: `llvm-nm -D -u` shows _ZN8tinyxml211XMLDocumentC[12]E), so each
    # construction writes 104 bytes past the space they reserved.
    #
    # OPEN-ISSUES #10 called this unfixable because Android 16 blocks all three
    # ways of overriding the platform libtinyxml2.so. That asked the wrong
    # question: we do not need to override it. Qualcomm ships libtinyxml2_1.so —
    # a privately renamed Android-15 copy, present in the factory image, extracted
    # here byte-identical — precisely to solve this, and ten display blobs already
    # link it. Verified before writing this: all tinyxml2 symbols these four
    # import (5 / 14 / 6 / 10 respectively) are defined by libtinyxml2_1.so, none
    # missing. Also verified: the camera provider process never loads the platform
    # copy (`readelf -d vendor.qti.camera.provider-service_64 | grep tinyxml` is
    # empty), so there is nothing to interpose on it.
    #
    # ⚠️ vendor/lib64/soundfx/libquasar.so is DELIBERATELY NOT in this list even
    # though it has the same defect. It loads into the audio HAL process, and both
    # audiohalservice.qti and libaudioeffecthal.qti.so link libtinyxml2.so
    # directly, so the v11 symbols sit in that process's global lookup scope and
    # win regardless of what libquasar's DT_NEEDED says. replace_needed would look
    # like a fix and not be one. Tracked in OPEN-ISSUES as unresolved.
    (
        'vendor/lib64/libapengine.so',
        'vendor/lib64/libcamxcoreutils.so',
        'vendor/lib64/libcamxods.so',
        'vendor/lib64/liblearningmodule.so',
        # ⚠️ These two came in when session 12 moved the display cluster to blobs,
        # and libsdmclient bit immediately — the composer crashed on every boot
        # with SIGILL / ILL_ILLOPN, esr 0x72000000 (PAC Exception), lr=0, x29=0,
        # one frame, in sdm::SDMDisplayResolutionExtn::GetExtendedDisplayResolutions.
        #
        # That register signature is a STACK smash, not heap corruption, and the
        # disassembly says exactly why:
        #
        #   98fcc: sub sp, sp, #0x360        864-byte frame
        #   99054: add x0, sp, #0x48         XMLDocument at sp+0x48 -> 0x318 = 792 free
        #   99058: bl  memset
        #   99068: bl  tinyxml2::XMLDocument::XMLDocument(bool, Whitespace)
        #
        # 792 bytes reserved. The Android-15 XMLDocument is 776 and fits; the
        # Android-16 one is 880 and runs 88 bytes past the frame into the saved
        # x29/x30 stored just above it, so autiasp at return fails PAC.
        #
        # Same shape as libaudioeffecthal.qti.so in session 11. Note it is a
        # BY-VALUE construction, never `new` — which is exactly the blind spot
        # of an allocation-size comparison, so that analysis did NOT and could
        # not have caught this one. It was found by reading the tombstone.
        # (⚠️ the analysis was a one-off; this line used to cite
        # work/scripts/37-layout-skew.py, which does not exist — #31.)
        #
        # The composer-service binary is in the list even though it uses ZERO
        # tinyxml2 symbols (`llvm-nm -D -u` is empty for them): it is the only
        # other thing in that process pulling libtinyxml2.so in, and while the
        # A16 copy is loaded it wins symbol lookup over the A15 one regardless of
        # what libsdmclient's own DT_NEEDED says. Fixing only libsdmclient would
        # look like a fix and not be one. Verified: those two are the ONLY members
        # of the composer's 76-library closure that reference libtinyxml2.so.
        'vendor/lib64/libsdmclient.so',
        'vendor/bin/hw/vendor.qti.hardware.display.composer-service',
    ): blob_fixup()
        .replace_needed(
            'libtinyxml2.so',
            'libtinyxml2_1.so',
        ),

    # The tree builds libtensorflowlite_c from external/tensorflow and installs it
    # to /vendor/lib64, but AOSP builds TFLite without the XNNPack delegate, so
    # that copy does not export TfLiteXNNPackDelegate{Create,Delete,OptionsDefault}.
    # Three stock blobs need them, and check_elf_file fails the build on the
    # unresolved symbols.
    #
    # Both copies are needed, so the factory one is extracted under a renamed
    # destination with ;FIX_SONAME (see proprietary-files.txt) and its consumers
    # are pointed at the new soname. This is the one collision shape that
    # neither dropping a side nor MAKE_COPY_RULE_ONLY can solve. onyx does the
    # same for its two consumers; libVoiceSdk is additional on this device.
    #
    # The consumer list was derived by reading DT_NEEDED off the FACTORY files,
    # not off proprietary/ -- the latter has already been rewritten by fixups,
    # so scanning it under-reports (PROGRESS.md section 3).
    (
        'vendor/lib64/libVoiceSdk.so',
        'vendor/lib64/libcapiv2uvvendor.so',
        'vendor/lib64/liblistensoundmodel2vendor.so',
    ): blob_fixup()
        .replace_needed(
            'libtensorflowlite_c.so',
            'libtensorflowlite_c_vendor.so',
        ),

    # host_init_verifier fails the build on every stock service block that has no
    # `user` line:
    #
    #   init.qcom.rc: 461: No user specified for service 'vendor.ssr_setup',
    #                      so it would have been root.
    #
    # It is not asking us to drop privileges -- it is asking us to say so. On the
    # host build the gate is service_parser.cpp:59-63,
    #   kAlwaysErrorUserRoot = BUILD_SHIPPING_API_LEVEL > __ANDROID_API_V__
    # and BUILD_SHIPPING_API_LEVEL comes from PRODUCT_SHIPPING_API_LEVEL, which is
    # 36 here and is correct (the device really does report
    # ro.product.first_api_level=36). Lowering it to silence this would misdeclare
    # the device, so the .rc files get the explicit `user root` instead.
    #
    # This is the same defect class as the runtime one handled by
    # ro.board.api_level=202404 in properties/vendor.prop -- but that property
    # only governs init on the device (service_parser.cpp:685 reads
    # ro.vendor.api_level). The host verifier never sees it, so both are needed.
    #
    # Behaviour is unchanged: these services ran as root before and still do.
    # ⚠️ The `(?:[^\n]*\\\n)*` is load-bearing: a service command can be split
    # across lines with a trailing backslash. vendor.wigig_supplicant is:
    #
    #   service vendor.wigig_supplicant /vendor/bin/hw/wpa_supplicant \
    #       -iwigig0 -Dnl80211 -c/data/vendor/wifi/wigig_supplicant.conf \
    #       ... -S wigigsvc
    #
    # A regex that stops at the first newline inserts `user root` in the middle
    # of the continued command, and init then reports
    # `Invalid keyword '-iwigig0'`. Consume the continuations first.
    #
    # Second edit: drop the import of init.qcom.factory.rc. That file is no
    # longer extracted (311 lines of FFBM/QMMI factory-test plumbing: three
    # `disabled` services whose binaries -- fastmmi, vendor.mmid, mmi_diag --
    # are not in this image either, the rest gated on ro.bootmode=ffbm-* or
    # vendor.sys.boot_mode=ffbm|qmmi, plus 30 `disabled` vendor.audio_tc*
    # stanzas that invoke mm-audio-ftm, also dropped). init treats a missing
    # import as a parse error and logs it on every boot, so the import has to go
    # with the file. Nothing else in the vendor image imports it -- checked
    # against the factory init tree, this line is its only referrer.
    ('vendor/etc/init/hw/init.qcom.rc',): blob_fixup()
        .regex_replace(
            r'(?m)^(service (?:vendor\.ssr_setup|vendor\.wigig_supplicant'
            r'|dhcpcd_wlan0|dhcpcd_bond0|dhcpcd_p2p|dhcpcd_wigig0|dhcpcd_bt-pan'
            r'|iprenew_wlan0|iprenew_bond0|iprenew_p2p|iprenew_wigig0|iprenew_bt-pan'
            r'|wifi-sdio-on|qlogd|vendor\.power_off_alarm|bugreport)\s'
            r'(?:[^\n]*\\\n)*[^\n]*\n)',
            r'\1    user root\n',
        )
        .regex_replace(
            r'(?m)^import /vendor/etc/init/hw/init\.qcom\.factory\.rc\n',
            '',
        )
        # mlid, disabled because its binary is no longer extracted.
        #
        # ⚠️ This one is not the same shape as the `user root` list above and is
        # the only stanza in all 222 shipped .rc files that the location-stack
        # removal would have broken. init.qcom.rc:791-795 is
        #
        #     service mlid /vendor/bin/mlid
        #         class late_start
        #         user gps
        #         group gps
        #         socket mlid stream 0666 gps gps
        #
        # `class late_start` with NO `disabled`, so class_start late_start calls
        # Service::Start(), which stats argv[0] and fails. Every other service
        # this tree has stopped extracting either already carried `disabled` or
        # got one here; this one had neither, so dropping the binary without
        # this line would have added an exec failure to every boot — the exact
        # thing the loc_launcher fixup below this was written to remove.
        #
        # mlid is the Measurement Link daemon: it serves Discovery and
        # RangingScan to lowi-server over /dev/socket/mlid. lowi-server was
        # already unreachable (no .rc in the image starts it; the only stanza,
        # init.target.rc:219, is commented out AND names the wrong binary) and
        # is now gone, so mlid has no possible client. Measured before removing
        # it: running for an hour with utime=0 stime=0 and its socket in
        # /proc/net/unix listening with zero peers.
        .regex_replace(
            r'(?m)^(service mlid\s(?:[^\n]*\\\n)*[^\n]*\n(?:[ \t]+[^\n]*\n)*)',
            r'\1    disabled\n',
        )
        # ★ thermal-switch-engine loses `oneshot`, because on this device it is
        # THE thermal daemon and `oneshot` means init will not restart it.
        #
        # init.qcom.rc:516-526 is a `disabled` + `oneshot` stanza started only by
        # `on property:vendor.thermal.mode=*`, and the sibling stanza
        # (init_thermal-engine-v2.rc:7, `service thermal-engine`, no config file)
        # is deliberately stopped at boot by the fixup further down this file, to
        # reproduce stock's end state. So the ONLY live userspace thermal daemon
        # is the oneshot one -- measured on the device: init.svc.thermal-engine
        # = stopped, init.svc.thermal-switch-engine = running, and the live
        # process is `thermal-engine-v2 -c .../thermal-engine-malbec-normal.conf`.
        #
        # ⚠️ Measured, not reasoned. `kill -9` on that pid left
        # init.svc.thermal-switch-engine = stopped and NOTHING restarted it, 21 s
        # later or ever: the only writer of vendor.thermal.mode on this ROM is
        # init.malbec.rc:289/294, driven by a user changing Device mode in
        # MalbecParts. So a single crash silently ends userspace thermal
        # mitigation for the rest of the boot. The AIDL HAL keeps reporting
        # temperatures, so nothing looks wrong.
        #
        # Safe because the daemon does not fork: init's tracked pid IS the live
        # process (init.svc.* reads `running`, and the pid in `ps` matches), so
        # removing `oneshot` cannot produce a respawn loop from a normal exit.
        # The `stop`/`start` pair at :525-526 is unaffected -- an explicit `stop`
        # suppresses the restart either way.
        #
        # ⚠️ Stock ships the same defect. This is one of the places the port is
        # deliberately better than stock rather than identical to it.
        # OPEN-ISSUES.md #58.
        .regex_replace(
            r'(?m)^(service thermal-switch-engine[^\n]*\n(?:[ \t]+[^\n]*\n)*?)'
            r'[ \t]*oneshot[ \t]*\n',
            r'\1',
        ),

    # Two edits to init.target.rc:
    #
    #  * vendor.mdm_launcher gets `user root` AND `disabled`.
    #
    #    `user root` is not optional and is not a privilege grant: it is what
    #    host_init_verifier demands. Without it the build fails outright with
    #      "No user specified for service 'vendor.mdm_launcher', so it would
    #       have been root."
    #    i.e. the line only writes down what init would do anyway. Session 16
    #    tried replacing it with `disabled` alone and got exactly that error.
    #
    #    `disabled` is the part that matters. This service exists to exec
    #    /vendor/bin/init.mdm.sh, and session 15 stopped extracting that script,
    #    so on this modem-less device (ro.baseband=apq) it could only ever fail
    #    exec, once per boot, with a log line. Not starting it is the right
    #    answer; the `user root` above just keeps the verifier happy while it
    #    sits there switched off.
    #
    #    `disabled` rather than deleting the stanza, for the same reason as
    #    vendor.cnss_diag below: the stanza is inside the shared init.target.rc,
    #    and a regex that deletes a multi-line service block is far easier to get
    #    subtly wrong than one that appends a line to it.
    #
    #  * vendor.cnss_diag gets `disabled`. It is Qualcomm's WLAN firmware DIAG
    #    log decoder, and on this build it burns ~2% of one core continuously
    #    and throws every byte away. Measured on the running device: 1m07s of
    #    CPU in 67 min of uptime, and `ls /proc/<pid>/fd` shows stdout/stderr on
    #    /dev/null with no regular file open at all -- its hardcoded output
    #    directory /data/vendor/newlog/wlan_logs never gets created. It is also
    #    the single chattiest process in the main log buffer. Stock ships the
    #    identical binary and .rc and does start it, but stock is not a reason
    #    to burn battery for logs nobody can read. `disabled` only removes it
    #    from `class_start main`; `start vendor.cnss_diag` still works if the
    #    logs are ever wanted for WLAN firmware debugging. This is diagnostics
    #    only -- WLAN itself is driven by the separate cnss-daemon, which is
    #    untouched.
    #
    #  * The four `mkdir /data/goodix_sensor*` lines go. This unit's
    #    accelerometer and gyroscope are ST LSM6DSVETR and its ambient-light
    #    sensor is a GS6155 -- read straight off the running device from
    #    /sys/devices/platform/product-device-info/info_{gsensor,gyro,lsensor}.
    #    There is no Goodix part anywhere in this machine (info_fingerprint is
    #    empty, and the touch controller is Novatek over SPI); the stanza is
    #    another SKU's, carried on a shared vendor partition. All it did here was
    #    create four empty directories under /data and log an AVC denial for
    #    vendor_init on system_data_file every boot.
    ('vendor/etc/init/hw/init.target.rc',): blob_fixup()
        .regex_replace(
            r'(?m)^(service vendor\.mdm_launcher\s(?:[^\n]*\\\n)*[^\n]*\n)',
            r'\1    user root\n    disabled\n',
        )
        .regex_replace(
            r'(?m)^(service vendor\.cnss_diag\s(?:[^\n]*\\\n)*[^\n]*\n(?:[ \t]+[^\n]*\n)*)',
            r'\1   disabled\n',
        )
        .regex_replace(
            r'(?m)^[ \t]*mkdir /data/goodix_sensor[^\n]*\n',
            '',
        ),

    # ★ Input boost hold time: 500 ms -> 100 ms.
    #
    # This is the single largest deliberate divergence Lenovo made from
    # Qualcomm's own tuning on this silicon, and it is the best explanation
    # found for "the tablet runs hot".
    #
    # init.kernel.post_boot-tuna_default_2_3_2_1.sh:126-129 (the variant that
    # actually runs here -- init.kernel.post_boot.sh:42-44 dispatches on
    # soc_id 694, and the 2/3/2/1 topology string picks this file) carries:
    #
    #   # TN Begin modified by keji.sun 20251030 MALBECW-1193(input and scroll boost)
    #   echo 1516800 1516800 2073600 2073600 2073600 2073600 2073600 1920000 \
    #        > /proc/sys/walt/input_boost/input_boost_freq
    #   echo 500 > /proc/sys/walt/input_boost/input_boost_ms
    #
    # Every other variant this device ships, and onyx (the official PixelOS
    # device on the SAME SoC), use Qualcomm's baseline instead:
    #
    #   echo 1075200 0 0 0 0 0 0 0 > .../input_boost_freq     # silvers only
    #   echo 100 (onyx: 40)        > .../input_boost_ms
    #
    # kernel/msm-6.6 input-boost.c:73,143,159: the vector is applied as a
    # freq_qos MINIMUM on every listed CPU and released input_boost_ms after the
    # LAST input event. So Lenovo's line pins all eight cores at 60-75% of their
    # maximum for half a second past the end of every scroll or tap. On a tablet
    # that is close to a permanent all-cluster frequency floor.
    #
    # Only the hold time is changed here, and deliberately only that:
    #
    #  * During a scroll the boost is re-armed by every touch sample (120-360 Hz
    #    on this digitiser), so the hold time is irrelevant while your finger is
    #    down -- what it buys is the TAIL after you lift. Half a second of eight
    #    cores at 2 GHz per flick, for nothing.
    #  * The frequency vector, by contrast, is what Lenovo was actually buying
    #    (scroll smoothness), and cutting it is a user-visible trade. 100 ms is
    #    still Qualcomm's own baseline hold, so this is a return to the platform
    #    default rather than an invention.
    #  * Changing both at once would make the result unattributable. The vector
    #    is I-5 in work/s15/reports/I-cpu-sched-power.md and is explicitly
    #    gated on an on-battery measurement that has never been taken.
    #
    # To revert: the stock line is `echo 500 > ...input_boost_ms`.
    ('vendor/bin/init.kernel.post_boot-tuna_default_2_3_2_1.sh',): blob_fixup()
        .regex_replace(
            r'(?m)^(\s*)echo 500 > /proc/sys/walt/input_boost/input_boost_ms$',
            r'\g<1>echo 100 > /proc/sys/walt/input_boost/input_boost_ms',
        ),

    # ⚠️ The blob_fixups for vendor.qsap.location.rc and loc-launcher.rc were
    # here and are GONE, because both FILES are gone. A fixup key naming a file
    # that is no longer extracted is a stale key, which is what
    # work/scripts/26-orphan-blobs.py fails on.
    #
    # Both were `disabled` patches on services whose binaries this tree had
    # already stopped shipping. Removing the files instead is strictly better —
    # a stanza that does not exist cannot be started — and is only safe now
    # because the whole location stack went with them:
    #
    #   * loc-launcher.rc was kept for one reason, written down at the time: its
    #     `on post-fs-data` creates /data/vendor/location{,/mq,/xtwifi,/hmac},
    #     "and those serve the LOWI Wi-Fi-location stack we deliberately keep".
    #     That premise is now false in both halves. LOWI is gone, and it was
    #     never reachable to begin with: no .rc in the image ever started
    #     lowi-server (the only stanza, init.target.rc:219, is commented out and
    #     names the wrong binary), and its Wi-Fi plugin liblowi_wifihal.so was
    #     mapped in no process with Wi-Fi fully up. Nothing else wants those
    #     directories either — every remaining namer of /data/vendor/location
    #     (xtra-daemon, xtwifi-client, libgnss, libizat_core, libcdfw,
    #     libengineplugin) is absent from this image, checked one by one.
    #   * qsap_location is Qualcomm's QESDK precise-positioning service, and on
    #     the running device init respawned it every 5.00 s forever (12/min,
    #     ~17k/day), each instance dying in ~12 ms on SIGSYS — libminijail
    #     rejecting `sched_get_priority_min` against an ARM32-era policy applied
    #     to ARM64. (OPEN-ISSUES.md #11 named `rseq`; the log line says
    #     sched_get_priority_min, so that entry is wrong.)
    #
    # Neither can do anything on this tablet in any case: no GNSS receiver,
    # ro.boot.vendor.qspa.nav=disabled, ro.baseband=apq, and no
    # android.hardware.location.gps feature. Network location, which the device
    # does have, is served by GMS's fused provider.
    #
    # vendor/etc/qspa/nav_disabled.rc STAYS and becomes load-bearing: this unit
    # reports ro.boot.vendor.qspa.nav=disabled, qspa_vendor.rc imports
    # nav_${ro.boot.vendor.qspa.nav}.rc, and that file carries an
    # `override`+`disabled` loc_launcher stanza. It is now the only declaration
    # of that service left. (Its sibling nsp_disabled.rc is NOT imported —
    # this unit reports nsp=enabled — and has been dropped.)

    # ★ Strip the Dolby effects from the audio effects config.
    #
    # The three Dolby effect libraries are no longer extracted, so their
    # declarations have to go with them: the AIDL effect factory reads this file
    # at audioserver start and a <library> naming a missing .so is a load failure
    # on every boot, plus an effect the framework advertises and cannot create.
    #
    # WHY the libraries went, recorded here because "Dolby was removed" invites
    # someone to put it back:
    #
    #   Music playback stuttered continuously with Dolby Atmos on, and was clean
    #   the instant it was switched off. Measured on the device, same track, same
    #   volume, same thread (AudioOut_15, deep buffer -> speaker, 40 ms period):
    #
    #                      process time      jitter min/max    delayed   underruns
    #     Dolby on         2.91 ms / 18.0    -36.6 / +23.4        0          0
    #     Dolby off        0.48 ms /  6.1    -37.1 / +26.2        0          0
    #
    #   Dolby costs 6x the processing, but NOTHING is late: zero delayed writes,
    #   zero underruns, and the jitter in the bad case is the same as in the good
    #   one. So the DAP is corrupting the stream in place rather than arriving
    #   late, which is why every counter stayed clean while it was audibly broken.
    #
    #   Four hypotheses were tested and all four are dead, listed so they are not
    #   re-run: CPU starvation (all four cpufreq policies at full scaling_max_freq,
    #   quiet-therm 34-36 C); video decode contention (the AV1 decoder ran at the
    #   same rate in every capture including screen-locked, and it still stuttered
    #   with a local MP3 and zero AV1 activity); the tinyxml2 sizeof break that
    #   killed libquasar (llvm-nm shows the Dolby libraries import no tinyxml2
    #   symbols at all); and the app re-writing effect parameters
    #   (DolbyController.kt (removed with the Dolby app)-39 is level-triggered on onPlaybackConfigChanged —
    #   it looked exactly right, and measured ZERO calls during steady playback,
    #   with the logging path validated first by provoking 48 lines from the same
    #   tags). The speaker amplifiers were cleared too: disabling the aw882xx
    #   monitor on all four changed nothing.
    #
    #   Root cause inside libswdapaidl.so is NOT identified. The owner's decision
    #   after the evidence was to remove it rather than keep hunting, which also
    #   follows this tree's standing rule that shipping a feature guaranteed to
    #   fail is worse than not shipping it.
    #
    # The AC3 / E-AC3 / AC4 DECODERS are deliberately NOT touched by this. They
    # are a separate stack that happens to share libdmshal.so with the effects,
    # and AOSP ships no replacement for them — removing them would cost every
    # AC3/E-AC3 soundtrack, which has nothing to do with the bug.
    #
    # ⚠️ This also drops the `spatializer` declaration, which is a fix rather
    # than collateral: it names libswspatializeraidl.so, and that file does not
    # exist in our image OR in the factory image. Lenovo over-declared it.
    ('vendor/etc/audio/sku_tuna/audio_effects_config.xml',): blob_fixup()
        .regex_replace(r'(?s)[ \t]*<!--DOLBY DAP-->.*?<!--DOLBY END-->\n', '')
        .regex_replace(r'(?m)^[ \t]*<apply effect="dlb_music_listener"/>\n', ''),

    # ★ Strip the 8K camcorder profiles. Six blocks, three cameras, one sensor
    # that is 4208 pixels wide.
    #
    # This file only started mattering in session 25. Until then nothing selected
    # it: MediaProfiles.cpp reads `ro.media.xml_variant.profiles`, Qualcomm's rc
    # files set only the `codecs` and `codecs_performance` siblings, and the
    # profiles variant fell back to the generic 1080p-capped
    # media_profiles_V1_0.xml. rootdir/etc/init.malbec.rc now sets the third
    # property, which is what unlocks 4K recording — and which also makes every
    # over-declaration in here real for the first time.
    #
    #   cameraId 0   27 profiles, max width 7680   <- rear, sensor is 4208x3120
    #   cameraId 1   21 profiles, max width 1920   <- front, sensor is 3264x2448
    #   cameraId 2   31 profiles, max width 7680   } no such camera; dumpsys
    #   cameraId 3   31 profiles, max width 7680   } media.camera reports 2
    #   cameraId 4   25 profiles, max width 4096   }
    #
    # 7680x4320 cannot be produced by a 4208-wide sensor and cannot be encoded by
    # this SoC (`performance-point-3840x2160-range 60-60` is the ceiling the codec
    # advertises). CamcorderProfile.hasProfile(0, QUALITY_8KUHD) would return TRUE
    # and CamcorderProfile.get() would hand back 7680x4320, so a MediaRecorder app
    # that checks before it asks — i.e. a well-written one — gets a configuration
    # that fails at start(). CameraX/Aperture is safe because it filters against
    # StreamConfigurationMap, but "safe in the one app we ship" is not the bar.
    #
    # ★ The vendor agrees, one revision later: media_profiles_tuna_v1.xml, which
    # ships alongside and is selected on units whose sku_version reads 1, contains
    # ZERO 8kuhd rows. This removes rows Qualcomm themselves removed next time.
    #
    # cameraId 2/3/4 are left alone deliberately. They are unreachable —
    # Camera.getNumberOfCameras() returns 2, so nothing can ask for a profile on
    # them — and deleting three whole <CamcorderProfiles> blocks is a much larger
    # regex against a 54 KB file for no behavioural gain. 4kdci (4096x2160) stays
    # on all of them because the rear sensor can genuinely crop to it.
    #
    # Verified before shipping: 6 blocks removed, `8kuhd` count 0 afterwards, and
    # BOTH the before and after parse under ElementTree — a mangled profiles file
    # would leave the device with no camcorder profiles at all, which is worse
    # than the bug being fixed.
    ('vendor/etc/media_profiles_tuna_v0.xml',): blob_fixup()
        .regex_replace(
            r'(?s)[ \t]*<EncoderProfile quality="(?:8kuhd|timelapse8kuhd)".*?'
            r'</EncoderProfile>\n', ''),

    # Thermal: stop the second, config-less daemon.
    #
    # Lenovo's MALBECW-799 patch (init.qcom.rc:516-528) adds
    # `thermal-switch-engine`, which is the same binary but with
    # `-c /vendor/etc/thermal-engine-malbec-${vendor.thermal.mode}.conf`, and
    # switches to it whenever vendor.thermal.mode is written:
    #
    #     on property:vendor.thermal.mode=*
    #        stop thermal-engine
    #        stop thermal-switch-engine
    #        start thermal-switch-engine
    #
    # On stock that property is written from system_ext AFTER boot, so the
    # ordering works out and stock settles with thermal-engine STOPPED and
    # thermal-switch-engine RUNNING. We set vendor.thermal.mode=normal as a
    # static build property (vendor.prop), so init queues that trigger before
    # `class_start main` has started anything: all three commands are no-ops
    # except the last, class main then starts the config-less `thermal-engine`
    # anyway, and this file's `restart thermal-engine` at boot_completed makes
    # sure it stays up. Measured on the running device -- two daemons:
    #     1887  thermal-engine-v2 -c /vendor/etc/thermal-engine-malbec-normal.conf
    #     4890  thermal-engine-v2                       <- no config at all
    #
    # Turning `restart` into `stop` reproduces stock's end state exactly, and is
    # deliberately preferred over marking thermal-engine `disabled`:
    #   - `disabled` would not help by itself, because an explicit `restart`
    #     starts a disabled service anyway.
    #   - thermal-engine is the service that DECLARES the four
    #     /dev/socket/thermal-* sockets the thermal HAL talks over, and init
    #     creates those in Service::Start(). Never starting it would mean the
    #     sockets are never created. Letting it start, then stopping it, leaves
    #     the sockets in place -- which is precisely what stock does.
    ('vendor/etc/init/init_thermal-engine-v2.rc',): blob_fixup()
        .regex_replace(
            r'(?m)^(on property:sys\.boot_completed=1\n)\s*restart thermal-engine\n',
            r'\1\tstop thermal-engine\n',
        ),

    # Same class, found by running host_init_verifier over all 134 shipped .rc
    # files instead of waiting for the build to surface them one per cycle.
    # Only files under etc/init/ are verified by the build -- vendor/etc/ueventd.rc
    # and vendor/etc/qspa/*.rc also fail a standalone run, but they use different
    # syntax and are never fed to the verifier, so they are left alone.
    # ⚠️ qms.rc and vendor.dpmd.rc used to get `user root` here for the same
    # reason. Session 13 removed both services outright -- QMS is Qualcomm's
    # telemetry/upload pipeline and DPM is the cellular data power manager, and
    # this is a Wi-Fi-only tablet -- so the fixups had nothing left to patch.
    # work/scripts/26-orphan-blobs.py is what would catch them today.

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
