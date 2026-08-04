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
    # 出厂 libaudioserviceexampleimpl.so 要 android::audio_utils::mutex_get_enable_flag()，
    # 那是 Android 15 的 libaudioutils 导出的符号，Android 16 把它删了（出厂 364 个
    # 导出符号里有，我们树构建的 403 个里没有）。
    #
    # 这条不是「少个库」而是「少个符号」，而且**致命**：该 blob 带 BIND_NOW /
    # FLAGS_1: NOW，动态链接器在加载期就要解析全部符号，解析不了就 dlopen 失败。
    # 它的消费者 libaudiocorehal.default.so 在 vendor_audio_interfaces.xml 里是
    # mandatory="true"，而 Service.cpp:53-77 对 mandatory 的库重试 10 次后
    # LOG_ALWAYS_FATAL —— audiohalservice.qti 会被 init 无限重启。
    #
    # 不能改用树的 libaudioserviceexampleimpl：它的 vendor 变体在本树编不过
    # （StreamAlsa/ModulePrimary/DevicePortProxy 等 5 个 .o 报错），而且三个消费者
    # 从它取用的 104 个符号全是 aidl::...::StreamCommonImpl 的 C++ 内部方法，
    # 不是稳定接口，换一个大版本的实现风险很高。
    #
    # 也不能把出厂 libaudioutils.so 一起提取：vendor 侧的 libaudioutils 有约 30 个
    # 消费者（24 个是 blob，6 个是树构建），而树的 libaudioutils.vendor 由
    # libalsautilsv2.vendor 等传递拉入，两者会在同一路径上撞车 —— 实测报
    # "partition is different: system(libaudioutils) != vendor(prebuilt_libaudioutils)"。
    #
    # 正解是上游早就备好的一行 shim：hardware/lineage/compat/libaudioutils/mutex.cpp
    # 就是 `bool mutex_get_enable_flag() { return mutex::kDefaultPriorityInheritance; }`。
    # 这是 Android 16 移植带 Android 15 音频 blob 的通用问题，不是本机特有的。
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
        # documented in work/scripts/37-layout-skew.py, so that gate did NOT and
        # cannot catch this one. It was found by reading the tombstone.
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
    ('vendor/etc/init/hw/init.qcom.rc',): blob_fixup()
        .regex_replace(
            r'(?m)^(service (?:vendor\.ssr_setup|vendor\.wigig_supplicant'
            r'|dhcpcd_wlan0|dhcpcd_bond0|dhcpcd_p2p|dhcpcd_wigig0|dhcpcd_bt-pan'
            r'|iprenew_wlan0|iprenew_bond0|iprenew_p2p|iprenew_wigig0|iprenew_bt-pan'
            r'|wifi-sdio-on|qlogd|vendor\.power_off_alarm|bugreport)\s'
            r'(?:[^\n]*\\\n)*[^\n]*\n)',
            r'\1    user root\n',
        ),

    # Two edits to init.target.rc:
    #
    #  * vendor.mdm_launcher gets `user root`, same class of problem as above.
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
    ('vendor/etc/init/hw/init.target.rc',): blob_fixup()
        .regex_replace(
            r'(?m)^(service vendor\.mdm_launcher\s(?:[^\n]*\\\n)*[^\n]*\n)',
            r'\1    user root\n',
        )
        .regex_replace(
            r'(?m)^(service vendor\.cnss_diag\s(?:[^\n]*\\\n)*[^\n]*\n(?:[ \t]+[^\n]*\n)*)',
            r'\1   disabled\n',
        ),

    # qsap_location is Qualcomm's QESDK precise-positioning service. It cannot
    # work here and it never stops trying: measured on the running device, init
    # respawns it every 5.00 s forever (12/min, ~17k/day), each instance dying
    # in ~12 ms on SIGSYS. libminijail rejects it on `sched_get_priority_min`,
    # and the seccomp policy it is handed is visibly an ARM32-era file being
    # applied to ARM64 (it also warns that chown/lchown/mmap2/fstat64/fstatat64/
    # _llseek are "nonexistent syscall").
    #
    # ⚠️ OPEN-ISSUES.md #11 named `rseq` as the blocked syscall. That was wrong;
    # the log line says sched_get_priority_min.
    #
    # We disable rather than fix the policy because there is nothing for it to
    # do: this tablet has no GNSS receiver. ro.boot.vendor.qspa.nav=disabled,
    # ro.baseband=apq, and `pm list features` has no
    # android.hardware.location.gps. Network location (which the device does
    # have) is served by GMS's fused provider, not by this.
    #
    # The cost of leaving it is not the ~0.5% CPU, it is that 12 forced wakeups
    # a minute keep the SoC out of deep idle, and it makes uid gps the single
    # chattiest uid in the log buffer.
    ('vendor/etc/init/vendor.qsap.location.rc',): blob_fixup()
        .regex_replace(
            r'(?m)^(service vendor\.qsap\.location\s(?:[^\n]*\\\n)*[^\n]*\n(?:[ \t]+[^\n]*\n)*)',
            r'\1    disabled\n',
        ),

    # loc-launcher.rc declares a service whose binary we do not ship.
    #
    # Session 13 removed /vendor/bin/loc_launcher (commit 20acb4f, which also
    # dropped its configs/config.fs stanza) but left the stanza:
    #
    #     service loc_launcher /vendor/bin/loc_launcher
    #         class late_start
    #         user gps
    #         group gps
    #
    # `class late_start` with no `disabled` means class_start late_start calls
    # Service::Start(), which stats argv[0], fails, logs
    # "Cannot find '/vendor/bin/loc_launcher'" and sets SVC_DISABLED. One error
    # per boot rather than a respawn loop — but a self-inflicted one, and the
    # only one of its kind left. Measured: a sweep of every `service` stanza in
    # all 222 shipped .rc files finds 55 whose binary is absent from our image,
    # of which 50 are absent from the factory image too (stock boilerplate, and
    # never started there either). That leaves FIVE we actually dropped —
    # qsap_location, mmi, mmi_diag, and BOTH copies of loc_launcher — and four
    # of those five already carry `disabled`. This one is the exception.
    #
    # The FILE must stay. Its `on post-fs-data` block is what creates
    # /data/vendor/location{,/mq,/xtwifi,/hmac}, and those serve the LOWI
    # Wi-Fi-location stack we deliberately keep (lowi-server is still in
    # config.fs and still installed). Dropping the file to kill the stanza would
    # take the mkdirs with it.
    #
    # Note vendor/etc/qspa/nav_disabled.rc already has the identical stanza with
    # `override` + `disabled`, so this only closes the gap between the two copies.
    ('vendor/etc/init/loc-launcher.rc',): blob_fixup()
        .regex_replace(
            r'(?m)^(service loc_launcher\s(?:[^\n]*\\\n)*[^\n]*\n(?:[ \t]+[^\n]*\n)*)',
            r'\1    disabled\n',
        ),

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
    # 19-verify-device-tree.py item 11 is what caught them.

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
