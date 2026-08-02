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
    vendor.qti.qspa-service \
    libsoundtriggerhal.qti

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
# 选源码的理由：出厂那三个 .so 的 DT_NEEDED 同时链了同一个 AIDL 接口的多个版本
# （audio.common V3+V4、audio.core V2+V4、sounddose V1+V2+V3），
# replace_needed 改不动这种（不是"旧换新"，是一个二进制里本来就有好几个），
# 只能靠 ;DISABLE_DEPS 把它们整个排除在依赖图外 —— 那等于放弃构建期的
# 链接检查。源码路径没有这个问题。
#
# 注意 qcom-effects（libqcompostprocbundle / libqcomvisualizer /
# libqcomvoiceprocessing / libvolumelistener）**仍然走 blob**：那几个源码模块
# 内部版本没对齐（effects defaults 用 latest=V4，而
# primary-hal/configs/audio-generic-modules.mk:3 把
# LATEST_ANDROID_MEDIA_ADUIO_COMMON_TYPES 钉在 V3）。它们不进 PRODUCT_PACKAGES，
# 出厂那份带 ;MODULE_SUFFIX=_vendor 提供，安装路径不变。
PRODUCT_PACKAGES += \
    libaudiocorehal.default \
    libaudiocorehal.qti \
    libaudioeffecthal.qti

# IPA（数据路径加速）
PRODUCT_PACKAGES += \
    ipacm

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
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/keylayout/Vendor_17ef_Product_62b2.kl:$(TARGET_COPY_OUT_VENDOR)/usr/keylayout/Vendor_17ef_Product_62b2.kl \
    $(LOCAL_PATH)/idc/Vendor_17ef_Product_62b2.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/Vendor_17ef_Product_62b2.idc

# Rootdir
# fstab.qcom is the one vendor config this port has to change, so the device
# tree owns it and proprietary-files.txt skips the stock copy.
PRODUCT_PACKAGES += \
    fstab.qcom

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
PRODUCT_PACKAGES += \
    FrameworkOverlayMalbec

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
