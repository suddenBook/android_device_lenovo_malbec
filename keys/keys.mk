#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

# Picked up by vendor/lineage/config/common.mk:304 (`-include`), so this file
# only has to exist to take effect.
#
# Setting this to anything other than build/make/target/product/security/testkey
# is what flips BUILD_KEYS from test-keys to release-keys
# (build/make/core/config.mk:1364-1367), which is what lands in ro.build.tags.
#
# The sibling keys in this directory — platform, shared, media, networkstack,
# nfc, bluetooth, sdk_sandbox — are NOT named here on purpose. A module that
# says `LOCAL_CERTIFICATE := platform` is rewritten to
# $(dir $(DEFAULT_SYSTEM_DEV_CERTIFICATE))platform
# (build/make/core/package_internal.mk:466), so pointing this one variable at
# this directory redirects all of them. Naming them again would be redundant
# and would drift the day one of them is renamed upstream.
PRODUCT_DEFAULT_DEV_CERTIFICATE := vendor/lineage-priv/keys/releasekey
