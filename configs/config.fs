# Generated from the stock firmware by work/scripts/18-gen-config-fs.py.
#
# AIDs come from vendor/etc/passwd; modes, owners and capabilities come from
# vendor/etc/fs_config_files. Nothing here is copied from another device.
#
# Only files this tree actually installs are listed — entries in the stock
# table for binaries we do not ship are dropped rather than carried over.

[AID_VENDOR_QTI_DIAG]
value:2901

[AID_VENDOR_QDSS]
value:2902

[AID_VENDOR_RFS]
value:2903

[AID_VENDOR_RFS_SHARED]
value:2904

[AID_VENDOR_ADPL_ODL]
value:2905

[AID_VENDOR_QRTR]
value:2906

[AID_VENDOR_THERMAL]
value:2907

[AID_VENDOR_FASTRPC]
value:2908

[AID_VENDOR_QTR]
value:2909

[AID_VENDOR_SSGTZD]
value:2912

[AID_VENDOR_QCC]
value:2914

# ⚠️ Six [system/vendor/bin/...] stanzas were removed in session 12 (cnd,
# ims_rtp_daemon, loc_launcher, pd-mapper, pm-service, slim_daemon).
#
# They were dead. This device has a real vendor partition
# (BoardConfig.mk:419 TARGET_COPY_OUT_VENDOR := vendor), so a `system/vendor/`
# prefix routes the entry into the SYSTEM partition's fs_config table, where no
# file ever matches it. The live entries are the [vendor/bin/...] ones below,
# which is why both spellings were present for the same binaries.
#
# The prefix is only correct on a device where vendor is a directory inside
# system (TARGET_COPY_OUT_VENDOR := system/vendor). Do not re-add them.

# ⚠️ [vendor/bin/lowi-server] with AID_GPS + NET_ADMIN was here and is gone with
# the binary. It was the last AID_GPS entry in this file and the only remaining
# NET_ADMIN grant outside the networking daemons — for a process that no .rc in
# the image ever started (the only stanza, init.target.rc:219, is commented out
# AND names the wrong binary), so it had never once run on this device.
#
# A leftover stanza here is not inert: fs_config_generate bakes it into the
# generated fs_config_files for the vendor image whether or not the path exists,
# so it survives as a capability grant with no owner.

[vendor/bin/pd-mapper]
mode: 0755
user: AID_SYSTEM
group: AID_SYSTEM
caps: NET_BIND_SERVICE

[vendor/bin/pm-service]
mode: 0755
user: AID_SYSTEM
group: AID_SYSTEM
caps: NET_BIND_SERVICE

[vendor/bin/sensors.qti]
mode: 0755
user: AID_SYSTEM
group: AID_SYSTEM
caps: NET_BIND_SERVICE

# ⚠️ [vendor/firmware_mnt/image/*] 0771 AID_SYSTEM was here and is gone. It can
# never match. /vendor/firmware_mnt is a SEPARATE vfat mount
# (rootdir/etc/fstab.qcom:83, uid=1000,gid=1000,dmask=227,fmask=337,
# context=u:object_r:firmware_file:s0) whose mount point is created by the
# vendor_firmware_mnt_mountpoint module, so no file under that path is ever
# inside vendor.img and no fs_config entry for it can ever be consulted --
# vfat carries no per-file ownership at all; the mount options decide.
#
# This is exactly the class the ⚠️ higher up in this file was written about:
# fs_config_generate bakes a stanza into the generated vendor table whether or
# not the path exists, so a leftover survives as a permission grant with no
# owner and no way to fire.

# ⚠️ Six AIDs were removed here: AID_VENDOR_{NXP,THALES}_{STRONGBOX,WEAVER,AUTHSECRET}.
# This file's own header says "only files this tree actually installs are listed",
# and those six exist solely for
#   android.hardware.security.keymint-service.strongbox-{nxp,thales}
#   android.hardware.security.weaver-service.{nxp,thales}
#   android.hardware.authsecret-service.{nxp,thales}-qti
# none of which is built (0 matches under out/.../vendor/bin) and none of which
# could be: this device has no StrongBox. `pm list features` reports
# android.hardware.hardware_keystore=300 and no android.hardware.strongbox_keystore,
# which is also why properties/product.prop:96-99 is written the way it is.
#
# The reverse direction was checked too, and is clean: every `user`/`group` name
# in all shipped vendor .rc files resolves against /vendor/etc/{passwd,group} plus
# the AOSP builtins, so this table is over-complete and never under-complete.
