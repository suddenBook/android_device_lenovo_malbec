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

[AID_VENDOR_NXP_STRONGBOX]
value:2910

[AID_VENDOR_NXP_WEAVER]
value:2911

[AID_VENDOR_SSGTZD]
value:2912

[AID_VENDOR_THALES_STRONGBOX]
value:2913

[AID_VENDOR_QCC]
value:2914

[AID_VENDOR_NXP_AUTHSECRET]
value:2915

[AID_VENDOR_THALES_WEAVER]
value:2916

[AID_VENDOR_THALES_AUTHSECRET]
value:2917







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

[vendor/bin/cnd]
mode: 0755
user: AID_SYSTEM
group: AID_SYSTEM
caps: NET_BIND_SERVICE NET_ADMIN BLOCK_SUSPEND

[vendor/bin/ims_rtp_daemon]
mode: 0755
user: AID_RADIO
group: AID_RADIO
caps: NET_BIND_SERVICE

[vendor/bin/imsdaemon]
mode: 0755
user: AID_RADIO
group: AID_RADIO
caps: NET_BIND_SERVICE WAKE_ALARM BLOCK_SUSPEND

[vendor/bin/loc_launcher]
mode: 0755
user: AID_GPS
group: AID_GPS
caps: SETGID SETUID

[vendor/bin/lowi-server]
mode: 0755
user: AID_GPS
group: AID_GPS
caps: NET_ADMIN

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

[vendor/bin/slim_daemon]
mode: 0755
user: AID_GPS
group: AID_GPS
caps: NET_BIND_SERVICE

[vendor/bin/xtwifi-client]
mode: 0755
user: AID_GPS
group: AID_GPS
caps: 0

[vendor/firmware_mnt/image/*]
mode: 0771
user: AID_SYSTEM
group: AID_SYSTEM
caps: 0
