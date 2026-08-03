#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

LOCAL_PATH := device/lenovo/malbec

# Soong namespaces
# vendor/lenovo/malbec/Android.bp imports device/lenovo/malbec, so the namespace
# has to be declared here and made visible to the product.
PRODUCT_SOONG_NAMESPACES += \
    $(LOCAL_PATH)

# Proprietary blobs
$(call inherit-product, vendor/lenovo/malbec/malbec-vendor.mk)

# Symlinks that point at runtime locations instead of shipped files, so
# extract-utils cannot generate them. See Android.bp for what each one is for.
PRODUCT_PACKAGES += \
    malbec_wlanmdsp_otaupdate_symlink \
    malbec_wlan_cfg_kiwi_v2_symlink \
    malbec_wlan_cfg_peach_symlink \
    malbec_wlan_cfg_peach_v2_symlink \
    malbec_wlan_cfg_qca6750_symlink \
    malbec_wlan_cfg_wcn7750_symlink \
    malbec_wlan_mac_kiwi_v2_symlink \
    malbec_wlan_mac_peach_symlink \
    malbec_wlan_mac_peach_v2_symlink \
    malbec_wlan_mac_qca6750_symlink \
    malbec_wlan_mac_wcn7750_symlink \

# CneApp expects libvndfwk_detect_jni.qti_vendor.so under its own lib/arm64/,
# where stock puts a symlink to /vendor/lib64. Without this the app hits an
# UnsatisfiedLinkError. The module also carries required: on the library itself,
# which is not otherwise installed here.
PRODUCT_PACKAGES += \
    CneApp.libvndfwk_detect_jni.qti_vendor_symlink

# Generic ramdisk allow list
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_ramdisk.mk)

# Project ID Quota
$(call inherit-product, $(SRC_TARGET_DIR)/product/emulated_storage.mk)

# Virtual A/B
# Stock enables compression, userspace snapshots, batch writes, io_uring and
# XOR (ro.virtual_ab.compression.xor.enabled=true), so inherit the XOR variant.
# It chains through compression.mk to launch_with_vendor_ramdisk.mk, which is
# where PRODUCT_VIRTUAL_AB_OTA and the vendor ramdisk fsck tools come from.
$(call inherit-product, $(SRC_TARGET_DIR)/product/virtual_ab_ota/compression_with_xor.mk)

# Dalvik vm configs
# 6144 is the largest profile AOSP ships and is the right one for this 8 GB device.
# The "phone" in the name is historical; the profiles are keyed on RAM and density.
$(call inherit-product, frameworks/native/build/phone-xhdpi-6144-dalvik-heap.mk)

# AVF
# Stock ships com.android.virt, so keep parity. Note that VmTerminalApp is
# deliberately NOT included: the hypervisor here is Gunyah, not KVM, /dev/kvm
# does not exist and only protected VMs are supported, so the Terminal's
# non-protected Debian VM cannot start. See PROGRESS.md.
$(call inherit-product, packages/modules/Virtualization/apex/product_packages.mk)

# Qualcomm
$(call soong_config_set,rfs,mpss_firmware_symlink_target,modem_firmware)
$(call inherit-product, hardware/qcom-caf/common/common.mk)

# QTI HALs built from source rather than carried as blobs, matching onyx.
# The sources are in this tree (hardware/qcom-caf, vendor/qcom/opensource), so
# the stock binaries would only shadow them — and being compiled against an
# older AIDL version, the resulting module ends up depending on two versions of
# the same interface at once, which Soong rejects:
#
#   android.hardware.health-service.qti: depends on multiple versions of the
#   same aidl_interface: android.hardware.health-V3-ndk-source,
#   android.hardware.health-V4-ndk-source
#
# proprietary-files.txt excludes all of them.
# ⚠️ 这份名单曾经漏了一大半，而漏掉的方式很隐蔽，值得写下来：
# 生成 proprietary-files.txt 的分类器有一条规则是「模块名定义在启用的 QTI
# soong 命名空间里 -> 这个 blob 不用提取」。规则本身没错，错在它从没核对过
# 那个源码模块**是否真的进了 PRODUCT_PACKAGES**。定义 != 构建。
# 于是 composer / boot control / thermal / 音频 HAL 实现库两头落空：
# blob 删了、源码也没人装。没有 composer，SurfaceFlinger 起不来，设备是黑屏的。
# 现在由 work/scripts/24-blob-reconcile.py 做反向对账（出厂有、镜像里没有 =
# 缺口），这一类不会再无声无息地漏掉。
PRODUCT_PACKAGES += \
    android.hardware.health-service.qti \
    android.hardware.thermal-service.qti \
    android.hardware.usb-service.qti \
    android.hardware.usb.gadget-service.qti \
    audiohalservice.qti \
    vendor.qti.hardware.display.allocator-service \
    vendor.qti.hardware.display.demura-service \
    vendor.qti.qspa-service

# Display composer
# 整条显示栈本树都是从源码构建的：libsdmutils / libqdMetaData / libgralloccore /
# libdisplaydebug / libsdedrm / libhistogram 全部来自 hardware/qcom-caf/sm8750，
# blob 一个都没有。composer 必须跟着从源码走 —— 出厂那个 composer 二进制要配
# 出厂的 libsdmcore.so，而我们不提取它，混用只会两头不着。
#
# 这一条是与 onyx 的有意分歧：onyx 把 composer-service 当 blob 提取
# （proprietary-files.txt:2261），但它的显示栈组合与本树不同。判据是本树实际
# 装了什么，不是 onyx 装了什么。
#
# composer-service 的 required: 会带上它自己的 .rc 和 VINTF 片段，片段由
# soong_config_variable("qtidisplay", "composer_version") 选择；
# hardware/qcom-caf/common/BoardConfigQcom.mk:198 的默认值就是 v3_3，
# 对应 composer-service3_v3.xml，与出厂一致，不需要另外设。
PRODUCT_PACKAGES += \
    vendor.qti.hardware.display.composer-service \
    vendor.qti.hardware.display.snapalloc-impl \
    android.hardware.graphics.mapper@4.0-impl-qti-display \
    init.qti.display_boot.rc \
    init.qti.display_boot.sh

# Audio HAL 实现库 —— 从源码构建。
# audiohalservice.qti 是个壳，真正的实现是它 dlopen 的这三个 .so。dlopen 不产生
# 构建依赖，所以必须显式列出来，否则服务起来了也没有 HAL。它们的 required: 会
# 带上 manifest_audiocorehal_default.xml / audioeffectservice_qti.xml 两个 VINTF
# 片段 —— 没有那两个，framework 的 FactoryHal 靠 AServiceManager_isDeclared 找
# HAL，会认为设备根本没有音频 HAL。
#
# ⚠️ 必须在这里显式钉死走源码还是走 blob，不能交给收敛循环去发现。
# 两条路各自自洽，但混在一起会来回震荡：blob 生成的 malbec-vendor.mk 会把
# libaudiocorehal.default 放进 PRODUCT_PACKAGES，不改名时这个名字解析到树内
# 源码模块，于是树装了这个路径、判据就认为 blob 多余把它删掉；删掉之后又没人
# 提供，下一轮再放回来。实测来回了三轮。
#
# ⚠️ 上一版在这里选了「从源码构建」，理由是出厂那三个 .so 的 DT_NEEDED 同时链了
# 同一个 AIDL 接口的多个版本，replace_needed 改不动。那个观察是对的，结论是错的
# —— 正解是 ;DISABLE_DEPS 加并装旧版接口，onyx 就是这么做的。走源码有两个它没看
# 到的代价：
#
#   1. 丢掉 Awinic 智能功放。出厂 libar-pal.so 导出 44 个 aw_ar_dsp_* /
#      aw_ar_kmsg_* / aw_audioreach_* 符号，CAF 源码里 awinic 相关代码是 0。
#      本机的功放就是它：设备上 aw882xx_dlkm 已加载（refcount 4），而本树还在发
#      校准数据 vendor/firmware/aw882xx_acf.bin 和校准工具 vendor/bin/aw882xx_cali
#      —— 数据和工具都在，消费它们的 API 没了。丢的不只是音质：excursion 和温度
#      保护也在这套 API 里。
#      把这个检查推广到全部 330 个「本树从源码构建且出厂也有」的库，扫 OEM 补丁
#      特征符号，只有 libar-pal.so 中招 —— 所以只有音频要回退，显示栈是干净的。
#
#   2. 它根本编不过。hardware/qcom-caf/sm8750/audio/pal 在本树里有 8 个 .o 因为
#      -Wformat 报错（Bluetooth.cpp、ResourceManager.cpp、SessionAlsa*.cpp、
#      SoundTriggerEngineGsl.cpp、StreamHaptics.cpp、HapticsDevProtection.cpp）。
#      m nothing 看不到这一层，上一轮的 mka bacon 在 9% 就死了，还没走到这里。
#
# 所以 PAL / AGM / graphservices / 三个 HAL 实现库 / st-hal 全部回到 blob，由
# proprietary-files.txt 按「树不装这个路径」自动纳入，冲突用 ;DISABLE_DEPS 处理
# （见 work/scripts/12-gen-proprietary-files.py 的 ENTRY_TAGS）。
#
# audiohalservice.qti 留在源码：它只是个壳，dlopen 那三个实现库，不链 PAL，实测
# 编得过，而且它的 init_rc: 会带出 vendor/etc/init/audiohalservice_qti.rc。
# libsoundtriggerhal.qti 则必须走 blob —— st-hal-ar/Android.bp:37 明确链 libar-pal。
# onyx 的分法完全相同。

# ;DISABLE_DEPS 让 blob 不进 Soong 的依赖图，但运行时它们仍然要在 /vendor/lib64
# 里找到自己链的那个接口版本。本树解析到的是更新的版本，所以旧版必须显式并装。
# 版本号来自对出厂二进制逐个 readelf -d 的结果，不是猜的。
# 多版本共存本来就是支持的，本树已经有先例：android.media.audio.common.types
# 的 V2 和 V4 现在就同时装着。
PRODUCT_PACKAGES += \
    android.hardware.audio.common-V3-ndk.vendor \
    android.hardware.audio.core-V2-ndk.vendor \
    android.hardware.audio.core.sounddose-V1-ndk.vendor \
    android.hardware.audio.core.sounddose-V2-ndk.vendor \
    android.hardware.audio.effect-V2-ndk.vendor \
    android.hardware.bluetooth.audio-V3-ndk.vendor \
    android.hardware.bluetooth.audio-V4-ndk.vendor \
    android.hardware.drm-V1-ndk.vendor \
    android.hardware.health-V1-ndk.vendor \
    android.media.audio.common.types-V3-ndk.vendor \
    vendor.qti.hardware.paleventnotifier-V2-ndk.vendor

# 出厂音频 blob 依赖的 AOSP 支撑库的 vendor 变体。onyx 列的是同一批。
PRODUCT_PACKAGES += \
    libalsautilsv2.vendor \
    libaudioaidlcommon.vendor \
    libmediautils_vendor.vendor \
    libmemunreachable.vendor \
    libaudioutils_shim

# ── 带 ;DISABLE_DEPS 的 blob 所需、但全树无人提供的 soname ─────────────────
#
# 这一整类构建期是**查不出来**的：`;DISABLE_DEPS` 同时关掉 shared_libs 生成和
# check_elf_file，于是 Soong 既不去构建这个依赖也不报错，`m nothing` 和
# `m pixelos` 全绿，开机后 dlopen 失败。
#
# 第七个 session 用一次完整扫描定位（3370 个 ELF / 27346 条 DT_NEEDED，按**链接器
# 命名空间**求解而不是按分区求并集），实测 20 个真缺口。其中 7 个是新的一类：
# **文件在镜像里，但跨不过 vendor/system 命名空间边界** —— Android 16 没有 VNDK，
# /vendor 的二进制只看得到 /odm/lib64、/vendor/lib64{,/hw,/egl} 加上
# system/etc/llndk.libraries.txt 里那 26 个。所以 android.hardware.health@1.0.so
# 之类「/system/lib64 里明明有」的库，对 vendor 消费者等于不存在，必须装 .vendor 变体。
#
# 分法（判据是**谁在消费**，不是这个库长什么样）：
#   · 冻结的稳定接口（HIDL @x.y / AIDL -Vn-ndk）-> 走树。ABI 由冻结的接口定义，
#     树的构建与出厂逐符号等价，这一批已逐个对过消费者的未定义符号与提供者导出。
#   · 不是接口的普通 C++ 库、而消费者是出厂 blob -> 走 blob（在 proprietary-files.txt
#     里），因为那是 Android 15 的二进制，跨一个大版本的内部 ABI 不保证。
#     libaudioserviceexampleimpl / libaudioplatformconverter.qti / libnbaio_mono /
#     qti-audio-types-aidl-V1-ndk / 六个 soundfx AIDL 效果库都属于这一类。
PRODUCT_PACKAGES += \
    android.hardware.bluetooth.audio@2.0.vendor \
    android.hardware.bluetooth.audio@2.1.vendor \
    android.hardware.health@1.0.vendor \
    android.hardware.health@2.0.vendor \
    android.hardware.health@2.1.vendor \
    android.hardware.power@1.0.vendor \
    android.hardware.power@1.1.vendor \
    android.hardware.power@1.2.vendor \
    android.hardware.soundtrigger3-V1-ndk.vendor \
    android.hardware.thermal@1.0.vendor \
    android.hardware.thermal@2.0.vendor \
    android.media.soundtrigger.types-V1-ndk.vendor \
    com.dsi.ant@1.0.vendor \
    libaudio_aidl_conversion_common_ndk.vendor \
    libflatbuffers-cpp.vendor \
    libusbhost.vendor \
    libwfdaac_vendor \
    qti-audio-types-aidl-V1-ndk.vendor \
    vendor.qti.hardware.bluetooth.audio-V1-ndk.vendor \
    vendor.qti.hardware.display.allocator@4.0.vendor

# 这两个 VINTF 片段走源码而不是 blob。它们本来是 libaudiocorehal.default /
# libaudioeffecthal.qti 的 required:，实现库改回 blob 之后就没人带它们了，而出厂
# 那份提取出来会与源码那份重名（都是有名字的 prebuilt_etc，且
# ;MODULE_SUFFIX= 对 prebuilt_etc 静默无效）：
#   module "audioeffectservice_qti.xml": found in multiple
#   namespaces(hardware/qcom-caf/sm8750 and vendor/lenovo/malbec)
# 两份内容逐字段比对过，声明完全一致，只有注释不同，所以用哪份都行 ——
# 用源码那份可以避开命名冲突。
PRODUCT_PACKAGES += \
    manifest_audiocorehal_default.xml \
    audioeffectservice_qti.xml

# IPA（数据路径加速）
#
# 两个配置文件必须显式列出来。ipacm 的 Android.bp 没有 required: 带上它们，
# 而「模块定义了」≠「本产品会构建」—— 这条教训 PROGRESS 第三节记过。
# 上一次完整构建装了 /vendor/bin/ipacm 却没有任何 IPACM_*.xml，
# 而 ipacm.rc:42 会 `copy /vendor/etc/IPACM_Filter_cfg.xml`。
#
# ⚠️ 走树而不是提取出厂那两份，理由是实测出来的：出厂的 IPACM_cfg.xml **没有**
# wlan0/wlan1/wlan2/wlan3/wigig0 这几个 Iface 条目，树里 sm8750 那份有
# （25 行差异全在这里）。这是一台 **WiFi-only** 平板，IPA offload 要用的恰恰
# 是 wlan 接口 —— 出厂那份是 Lenovo 按自己的 SKU 裁过的。
# IPACM_Filter_cfg.xml 两边逐字节相同，跟着走树保持一对。
# 而且 ipacm 二进制本身就是从 hardware/qcom-caf/sm8750 编的，配置跟二进制同源。
PRODUCT_PACKAGES += \
    ipacm \
    IPACM_cfg.xml \
    IPACM_Filter_cfg.xml

# Sensors
# The multihal service and its NDK bridge are AOSP's; only the sub-HALs listed
# in the stock /vendor/etc/sensors/hals.conf are device specific, and those stay
# as blobs. onyx does the same — it extracts no sensors service blob at all.
PRODUCT_PACKAGES += \
    android.hardware.sensors-service.multihal \
    sensors.dynamic_sensor_hal

# DRM
# clearkey is AOSP's reference plugin, not a device blob.
PRODUCT_PACKAGES += \
    android.hardware.drm-service.clearkey

# Device characteristics
# Not set anywhere in the inherited tablet config, but stock reports
# ro.build.characteristics=tablet and resource selection depends on it.
PRODUCT_CHARACTERISTICS := tablet

# Partitions
# BoardConfig.mk describes the super partition and its group, but the product
# side has to opt in as well: PRODUCT_BUILD_SUPER_PARTITION defaults to this, and
# without it no super.img is assembled.
PRODUCT_USE_DYNAMIC_PARTITIONS := true

# fstab.qcom mounts /vendor/firmware_mnt, /vendor/dsp and /vendor/bt_firmware.
# Stock ships those three as empty directories and proprietary-files.txt cannot
# carry an empty directory, so nothing would create them and all three mounts
# would fail. These modules exist for exactly that
# (hardware/qcom-caf/common/Android.bp); onyx pulls in the same set.
PRODUCT_PACKAGES += \
    vendor_bt_firmware_mountpoint \
    vendor_dsp_mountpoint \
    vendor_firmware_mnt_mountpoint

# A/B
# AB_OTA_UPDATER is set in BoardConfig.mk, but the updater itself is a product
# package. Without these three the build still produces an OTA zip and the device
# has nothing able to apply it.
#
# android.hardware.boot-service.qti 同样是必需的，而且比上面三个更早出问题：
# update_engine 拿不到 IBootControl 就无法把当前 slot 标记成 successful，
# bootloader 的重试计数耗尽后会回滚到另一个 slot。
# `android.hardware.boot` 在 compatibility_matrix.202404.xml 里没有
# optional="true"，是强制项。recovery 变体给 sideload 用。
PRODUCT_PACKAGES += \
    android.hardware.boot-service.qti \
    android.hardware.boot-service.qti.recovery \
    update_engine \
    update_engine_sideload \
    update_verifier

AB_OTA_POSTINSTALL_CONFIG += \
    RUN_POSTINSTALL_system=true \
    POSTINSTALL_PATH_system=system/bin/otapreopt_script \
    FILESYSTEM_TYPE_system=ext4 \
    POSTINSTALL_OPTIONAL_system=true

AB_OTA_POSTINSTALL_CONFIG += \
    RUN_POSTINSTALL_vendor=true \
    POSTINSTALL_PATH_vendor=bin/checkpoint_gc \
    FILESYSTEM_TYPE_vendor=ext4 \
    POSTINSTALL_OPTIONAL_vendor=true

PRODUCT_PACKAGES += \
    otapreopt_script \
    checkpoint_gc

# API
# Both values are what the device actually shipped with, read off the stock
# vendor build.prop: ro.board.first_api_level / ro.board.api_level = 202404 and
# ro.product.first_api_level = 36.
#
# BOARD_SHIPPING_API_LEVEL is the one that matters for booting. It is the vendor
# API surface this device froze at (Android 15, VINTF target-level 202404) while
# the framework side is Android 16, which is the Treble GRF arrangement this
# port depends on. Declare it too low or too high and the VINTF compatibility
# check between the framework matrix and the vendor manifest fails.
#
# PRODUCT_SHIPPING_API_LEVEL is 36 rather than onyx's 35 simply because this
# tablet launched on Android 16 and onyx launched on 15.
#
# BOARD_API_LEVEL is deliberately absent: the release config sets it from
# RELEASE_BOARD_API_LEVEL and build/make/core/board_config.mk errors out if a
# device tree assigns it.
BOARD_SHIPPING_API_LEVEL := 202404
PRODUCT_SHIPPING_API_LEVEL := 36

# Input
# The Lenovo folio keyboard and touchpad both enumerate as 17ef:62b2 on bus
# 0x0019. The stylus digitizer (NVTCapacitivePen) needs no configuration at all:
# it sits on SPI with a zero VID/PID, so it matches no Vendor_*.idc, and AOSP
# classifies it as a STYLUS purely from its evdev capabilities
# (BTN_TOOL_PEN, BTN_STYLUS, BTN_STYLUS2, ABS_PRESSURE, ABS_TILT_X/Y).
#
# 17ef:617f is the *other* half of the pen: its Bluetooth HID side, which
# carries the side-button gestures. That is a different device from the SPI
# digitizer above and needs its own keylayout — without one the side button
# does nothing at all. DEVICE-FACTS.md has documented since session 1 that the
# stock system loads Vendor_17ef_Product_617f.kl for it, but the file was only
# ever staged under work/analysis/ and never wired in here. See the header of
# our keylayout for why the stock file cannot be copied verbatim (six Lenovo
# labels that KeyLayoutMap rejects, taking the whole file down with them) and
# why the stock .idc is deliberately not carried.
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/keylayout/Vendor_17ef_Product_62b2.kl:$(TARGET_COPY_OUT_VENDOR)/usr/keylayout/Vendor_17ef_Product_62b2.kl \
    $(LOCAL_PATH)/keylayout/Vendor_17ef_Product_617f.kl:$(TARGET_COPY_OUT_VENDOR)/usr/keylayout/Vendor_17ef_Product_617f.kl \
    $(LOCAL_PATH)/idc/Vendor_17ef_Product_62b2.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/Vendor_17ef_Product_62b2.idc

# Rootdir
# fstab.qcom is the one vendor config this port has to change, so the device
# tree owns it and proprietary-files.txt skips the stock copy.
#
# init.recovery.qcom.rc is the recovery-side counterpart. Recovery never mounts
# /vendor, so the stock init.target.rc -- which is what creates
# /dev/block/bootdevice on a normal boot -- is not read there. Without this
# module recovery has no bootdevice symlink (most of fstab.qcom then fails) and
# no USB, i.e. no `adb sideload` and no fastbootd, which is exactly the tooling
# needed to recover from a bad flash. See rootdir/Android.bp.
PRODUCT_PACKAGES += \
    fstab.qcom \
    init.recovery.qcom.rc

# ⚠️ 上面那个 prebuilt_etc 只产出 /vendor/etc/fstab.qcom，**开不了机**。
# 第一阶段挂载的时候 /vendor 正是还没挂上的那个分区，fs_mgr 的 GetFstabPath()
# （system/core/fs_mgr/libfstab/fstab.cpp）在这一刻只可能读到 ramdisk 里那份。
# 出厂的 vendor_boot ramdisk 里确实带着 first_stage_ramdisk/fstab.qcom，
# 而本树之前两个位置都没装 —— ReadDefaultFstab() 失败，init panic。
# 真机上 /proc/device-tree/firmware/android/ 不存在，所以也没有 DT fstab 那条退路。
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/rootdir/etc/fstab.qcom:$(TARGET_COPY_OUT_VENDOR_RAMDISK)/first_stage_ramdisk/fstab.qcom

# Overlays
# Framework RRO. Values are measured from the stock ROM's dumpsys display, not
# copied from another device — see the comments in its config.xml.
#
# WifiOverlayMalbec is not optional decoration on a Wi-Fi-only tablet. PixelOS
# builds the AOSP ServiceWifiResources.apk, and its defaults answer "no" to most
# capability questions: config_wifi5ghzSupport, config_wifi6ghzSupport,
# config_wifiSoftap*, config_wifiSaeH2eSupported and MAC randomisation are all
# false out of the box. Stock supplies them from four vendor RROs that live on
# partitions this port replaces. See the overlay's AndroidManifest for why one
# overlay covers what stock does with four, and which stock values are
# deliberately left out because their RROs are gated on vendor.sku=sun.
PRODUCT_PACKAGES += \
    FrameworkOverlayMalbec \
    WifiOverlayMalbec

# Screen
TARGET_SCREEN_HEIGHT := 3504
TARGET_SCREEN_WIDTH := 2190

# WiFi
# Built from source, not carried as blobs — see the WiFi section of
# BoardConfig.mk for why. The per-device tuning files (WCNSS_qcom_cfg.ini for
# each chip directory, vendor_cmd.xml, the supplicant overlays and
# etc/hostapd/*) stay as blobs, since they are this board's configuration and
# do not exist in AOSP.
PRODUCT_PACKAGES += \
    android.hardware.wifi-service \
    hostapd \
    hostapd_cli \
    libwifi-hal-qcom \
    wpa_cli \
    wpa_supplicant \
    wpa_supplicant.conf
