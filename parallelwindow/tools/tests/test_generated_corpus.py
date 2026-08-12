#!/usr/bin/env python3

# Copyright (C) 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0

"""Independent golden checks for the checked-in HyperOS product corpus."""

from collections import Counter
import hashlib
import json
from pathlib import Path
import unittest


PARALLEL_DIR = Path(__file__).resolve().parents[2]
RELEASE_SHA256 = "cf01225b777ce67e44bfc88a54e6adf46f7a7326c6cb5d5e2c1508cea43f4e09"
RULES_SHA256 = "fa8caafac66c17d4786175e7dbd2b6c9514419a88499adb277dc12002beddf7a"
SETTINGS_SHA256 = "d50e462dfd7e62711b27cb87440e28560cf7ab6c699f9fe96cc8988b8e87144c"
# The second source: Lenovo's own corpus for this tablet, out of the factory ZUI image's
# framework-res.apk. Also an INPUT pin -- moving it means a different firmware.
ZUI_CORPUS_SHA256 = (
    "a4f0a66b95a8adbc1ac9e530e8d3194b7f77c9847b2bd3c889bcf65614eb9a6c")
# ⚠️ The four hashes ABOVE pin the INPUTS and must never move without a new
# source-lock review. The three BELOW pin the OUTPUT and the local layer, and
# the product digest has moved four times, each time deliberately: the
# unguarded-autoPrimary gate (#81); deleting autoPrimary so a bare row went back
# to meaning what upstream released (#87); marking every row centred, which is
# what a bare row is on the list FOR; and adding the local layer, which is what
# stops a re-import silently deleting a hand-verified rule again.
# If a change moves an INPUT hash, stop: that is a different upstream release,
# not a conversion change.
PRODUCT_SHA256 = "e550b668f1ef72c312bb071e5a5d3b5466265973398535eee11fe372cec08757"
AUDIT_SHA256 = "f00f93777a1e471478dc1b18b975982981e0b4a8a88efbd1e5ee0e767a8c31b1"
LOCAL_ADDITIONS_SHA256 = "63071c00d80d2b1547f56fff607879cc791dd2aefc5198b8e27ff9ccb8c2404d"
MAX_DEVICE_RULE_BYTES = 8 * 1024 * 1024

# The whole point of the local layer, expressed as numbers the import cannot move.
IMPORTED_PACKAGES = 6_968
LOCAL_FROM_ZUI = 76
LOCAL_HAND_AUTHORED = 1
SHIPPED_PACKAGES = IMPORTED_PACKAGES + LOCAL_FROM_ZUI + LOCAL_HAND_AUTHORED


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class GeneratedCorpusTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.output_path = PARALLEL_DIR / "embedding_config.json"
        cls.audit_path = PARALLEL_DIR / "hyperos_import_audit.json"
        cls.lock_path = PARALLEL_DIR / "hyperos_source_lock.json"
        cls.output = json.loads(cls.output_path.read_text(encoding="utf-8"))
        cls.audit = json.loads(cls.audit_path.read_text(encoding="utf-8"))
        cls.lock = json.loads(cls.lock_path.read_text(encoding="utf-8"))
        cls.rules_path = (
            PARALLEL_DIR / cls.lock["rendered_xml"]["vendoredPath"])
        cls.settings_path = (
            PARALLEL_DIR
            / cls.lock["rendered_settings_xml"]["vendoredPath"])
        cls.local_lock_path = PARALLEL_DIR / "local_source_lock.json"
        cls.local_lock = json.loads(
            cls.local_lock_path.read_text(encoding="utf-8"))
        cls.local_path = PARALLEL_DIR / "local_additions.json"
        cls.local = json.loads(cls.local_path.read_text(encoding="utf-8"))
        cls.zui_path = (
            PARALLEL_DIR / cls.local_lock["zui_corpus"]["vendoredPath"])

        # ★ Partition the product the way the importer built it. The release counts below are
        # literal pins on what the 3.01.48 conversion produced, and they only stay pins if a
        # second source cannot dilute them -- so every count assertion runs over `imported`.
        # The audit is the authority on which rows are local, and both files are digest-pinned
        # in the same test, so they cannot disagree in a checked-in tree.
        cls.local_names = {
            item["package"]
            for key in ("localAdditions", "localOverrides")
            for item in cls.audit[key]
        }
        cls.packages = cls.output["packages"]
        cls.imported = [rule for rule in cls.packages
                        if rule["name"] not in cls.local_names]
        cls.local_rows = [rule for rule in cls.packages
                          if rule["name"] in cls.local_names]

    def test_vendored_inputs_match_independent_literal_hashes_and_sizes(self):
        self.assertEqual(RULES_SHA256, sha256(self.rules_path))
        self.assertEqual(SETTINGS_SHA256, sha256(self.settings_path))
        self.assertEqual(1_331_074, self.rules_path.stat().st_size)
        self.assertEqual(629_445, self.settings_path.stat().st_size)
        self.assertEqual(RULES_SHA256, self.lock["rendered_xml"]["sha256"])
        self.assertEqual(
            SETTINGS_SHA256,
            self.lock["rendered_settings_xml"]["sha256"],
        )
        self.assertEqual(RELEASE_SHA256, self.lock["release_asset"]["sha256"])
        self.assertEqual(13_844_057, self.lock["release_asset"]["sizeBytes"])
        # The second source is pinned by the same discipline, in its own lock file.
        self.assertEqual(ZUI_CORPUS_SHA256, sha256(self.zui_path))
        self.assertEqual(122_150, self.zui_path.stat().st_size)
        self.assertEqual(
            ZUI_CORPUS_SHA256, self.local_lock["zui_corpus"]["sha256"])
        self.assertEqual(288, self.local_lock["zui_corpus"]["packages"])
        self.assertEqual(
            "93afdef3f3dde729e2b05afbb2fad2995b2ef0e67ad2222c79762526d832a78b",
            self.local_lock["provenance"]["containerSha256"])

    def test_product_has_a_reviewed_golden_digest_and_is_loader_bounded(self):
        self.assertEqual(PRODUCT_SHA256, sha256(self.output_path))
        self.assertEqual(AUDIT_SHA256, sha256(self.audit_path))
        self.assertEqual(LOCAL_ADDITIONS_SHA256, sha256(self.local_path))
        self.assertLess(self.output_path.stat().st_size, MAX_DEVICE_RULE_BYTES)
        generated = self.output["_generated"]
        self.assertEqual(RULES_SHA256, generated["renderedRulesSha256"])
        self.assertEqual(SETTINGS_SHA256, generated["renderedSettingsSha256"])
        self.assertIsNotNone(generated["localLayer"])

    def test_official_only_corpus_has_literal_release_counts(self):
        expected = {
            "rule_rows": 8_046,
            "rule_unique_packages": 8_024,
            "rule_duplicate_rows": 22,
            "setting_rows": 8_396,
            "setting_unique_packages": 8_396,
            "setting_duplicate_rows": 0,
            "source_final_full_rule": 1_056,
            "imported": 6_968,
            # Every row is centred; 4,917 have nothing else at all, which is the
            # population MiddleRule exists for and the reason the corpus is this
            # size. 36 source rows carry an explicit middleRule attribute, which
            # on HyperOS overrides its judgement rather than opting into it.
            "imported_middle": 6_968,
            "imported_middle_only": 4_917,
            "source_middle_rule_rows": 36,
            "imported_explicit_pair_packages": 2_028,
            "imported_pair_relationships": 3_750,
            "imported_placeholder_packages": 184,
            "imported_placeholder_relationships": 185,
            "excluded_full_rule": 1_056,
            "malformed_rule_rows": 0,
            "dropped_malformed_packages": 0,
            "malformed_pair_tokens": 1,
            "recovered_pair_tokens": 2,
            "recovered_pair_relationships": 4,
            "recovered_whitespace_tokens": 4,
            "dropped_primary_wildcard_relations": 1,
            "malformed_placeholder_tokens": 0,
            "malformed_component_tokens": 6,
            "normalized_component_tokens": 3,
            "normalized_activity_rule_tokens": 1,
            "mapped_placeholder_wait_for_content": 1,
            "ignored_rule_defaults": 496,
            "rule_default_fallbacks": 1,
            "unmapped_attribute_values": 11_537,
            "unmapped_setting_attribute_values": 4_700,
            "orphan_setting_rows": 452,
            "unmapped_flag_directives": 48,
            # ★ THESE TWO ARE THE RELEASE'S NUMBERS AGAIN. Between #81 and #87
            # they were the product's: the unguarded-autoPrimary gate
            # default-disabled 4,834 rows on top of upstream's own 16, giving
            # 2,118 / 4,850. Deleting autoPrimary (#87) removed the reason for
            # the gate, so the importer is back to materialising exactly what
            # upstream released and nothing else decides a default.
            "default_enabled": 6_952,
            "default_disabled": 16,
            "split_ratio_0_3": 275,
            "split_ratio_0_4": 8,
            "split_ratio_0_42": 3,
            "split_ratio_0_5": 6_670,
            "split_ratio_0_7": 12,
            "min_width_dp_600": 6_965,
            "min_width_dp_900": 3,
            "min_smallest_width_dp_600": 6_968,
            "clear_top_true": 6_963,
            "clear_top_false": 5,
            "finish_primary_with_secondary_0": 6_968,
            "finish_primary_with_secondary_1": 0,
            "finish_primary_with_secondary_2": 0,
            "finish_secondary_with_primary_0": 10,
            "finish_secondary_with_primary_1": 0,
            "finish_secondary_with_primary_2": 6_958,
            "show_divider_enabled": 5_504,
            "show_divider_disabled": 1_464,
            "divider_dragging_to_fullscreen_allowed": 6_603,
            "divider_dragging_to_fullscreen_disallowed": 365,
            "shown_divider_dragging_to_fullscreen_allowed": 5_480,
            "shown_divider_dragging_to_fullscreen_disallowed": 24,
            "output_packages": 6_968,
        }
        counts = self.audit["counts"]
        for key, value in expected.items():
            with self.subTest(key=key):
                self.assertEqual(value, counts[key])

    def test_packages_are_unique_sorted_and_have_no_curated_seed_layer(self):
        # The SHIPPED list is one sorted, unique list -- the merge must not break that, because
        # the loader and MalbecParts both assume it.
        shipped = [rule["name"] for rule in self.packages]
        self.assertEqual(sorted(shipped), shipped)
        self.assertEqual(len(shipped), len(set(shipped)))
        self.assertEqual(SHIPPED_PACKAGES, len(shipped))

        packages = self.imported
        names = [rule["name"] for rule in packages]
        self.assertEqual(sorted(names), names)
        self.assertEqual(len(names), len(set(names)))
        self.assertEqual(IMPORTED_PACKAGES, len(names))
        # ⚠️ Still no curated SEED layer. The local layer added by this session is a different
        # thing and the distinction is the whole point: a seed row was an unsourced hand-written
        # entry that the real corpus was expected to replace, and three of them were silently
        # replaced. A local row is declared in local_additions.json, digest-pinned, converted or
        # authored in the open, and accounted for in the audit down to the attribute.
        for removed_seed in (
            "com.android.contacts",
            "org.lineageos.etar",
            "org.lineageos.glimpse",
        ):
            self.assertNotIn(removed_seed, shipped)
        # Every shipped row can do something -- the invariant that briefly did
        # not hold between deleting autoPrimary (#87) and shipping MiddleRule.
        # 2,051 rows can route; all 6,968 are centred; none is inert.
        routable = [rule for rule in packages
                    if rule.get("activityPairs") or rule.get("placeholderPairs")]
        self.assertEqual(2_051, len(routable))
        for rule in packages:
            self.assertIs(True, rule.get("middle"), rule["name"])
            # middleRatio has no upstream counterpart, so it is deliberately not
            # materialized -- the parser default of 0.5 is HyperOS's own value.
            self.assertNotIn("middleRatio", rule)
            self.assertFalse(any(key.startswith("_tested") for key in rule))

    def test_official_taobao_rule_preserves_both_placeholders_and_default(self):
        by_name = {rule["name"]: rule for rule in self.imported}
        self.assertEqual({
            "name": "com.taobao.taobao",
            "showEmbeddingDivider": False,
            "dividerDraggingToFullscreenAllowed": False,
            "clearTop": False,
            "finishPrimaryWithSecondary": 0,
            "finishSecondaryWithPrimary": 2,
            "splitRatio": 0.5,
            "minWidthDp": 600,
            "minSmallestWidthDp": 600,
            "middle": True,
            "activityPairs": [
                {"from": "com.taobao.search.sf.MainSearchResultActivity", "to": "*"},
                {"from": "com.taobao.tao.welcome.Welcome", "to": "*"},
                {
                    "from": "com.taobao.android.detail.alittdetail.TTDetailActivity",
                    "to": "*",
                },
                {
                    "from": "com.alibaba.triver.triver_shop.newShop.ShopActivity",
                    "to": "*",
                },
            ],
            "placeholderPairs": [
                {
                    "from": "com.taobao.tao.welcome.Welcome",
                    "to": "com.taobao.tao.MagicWindowActivity",
                    "waitForContent": True,
                },
                {
                    "from": "com.taobao.tao.TBMainActivity",
                    "to": "com.taobao.tao.MagicWindowActivity",
                },
            ],
            "transActivities": [
                "com.taobao.tao.TBMainActivity",
                "com.taobao.android.shop.activity.ShopUrlRouterActivity",
                "com.taobao.linkmanager.afc.TbFlowInActivity",
                "com.taobao.cun.bundle.community.ui.activity.CuntaoRouterActivity",
                "com.taobao.tao.rushpromotion.luaview.activity.LuaOrH5RouterActivity",
                "com.taobao.browser.router.FromH5RouterActivity",
                "com.taobao.android.shop.activity.ShopUrlRouterActivity",
                "com.taobao.taolive.room.TaoLiveRouterActivity",
            ],
            "defaultEnabled": False,
            "suppressRelaunch": True,
        }, by_name["com.taobao.taobao"])

    def test_official_weibo_row_selects_full_mode_and_the_local_layer_restores_it(self):
        # ★ Two halves, and both have to hold, because this is the test that used to encode the
        # deletion as a contract. The upstream exclusion is still correct and still happens; what
        # changed is that it is no longer the last word.
        #
        # Note "final" is gone from the name. com.sina.weibo appears EXACTLY ONCE in all 8,046
        # source rows, so nothing was discarded by last-row-wins and there is no earlier row with
        # a splitPairRule. The old wording implied a predecessor that does not exist.
        self.assertNotIn(
            "com.sina.weibo", {rule["name"] for rule in self.imported})
        exclusions = {
            item["package"]: item
            for item in self.audit["excludedFullRulePackages"]
        }
        self.assertEqual({
            "package": "com.sina.weibo",
            "row": 7793,
            "value": "nra:cr:rcr:nr:uc",
        }, exclusions["com.sina.weibo"])

        # And the local layer puts it back, field for field. This row is the only one in the
        # whole corpus verified against an installed APK rather than declared by a vendor, so it
        # is pinned exactly -- including the provenance the audit carries for it.
        by_name = {rule["name"]: rule for rule in self.packages}
        self.assertEqual({
            "name": "com.sina.weibo",
            "activityPairs": [
                {"from": "com.sina.weibo.MainTabActivity", "to": "*"},
                {"from": "com.sina.weibo.VisitorMainTabActivity", "to": "*"},
            ],
            "transActivities": [
                "com.sina.weibo.SplashActivity",
                "com.sina.weibo.FixedCarshActivity",
                "com.sina.weibo.MainTabActivity",
                "com.sina.weibo.VisitorMainTabActivity",
            ],
            "forceFullscreenPages": [
                "com.sina.weibo.MainTabRestarActivity",
                "com.sina.weibo.composerinde.OriginalComposerActivity",
                "com.sina.weibo.qrcode.CaptureActivity",
                "com.sina.weibo.browser.WeiboBrowser",
                "com.sina.weibo.wboxsdk.page.acts.WBXPageActivity",
            ],
            "showEmbeddingDivider": True,
            "dividerDraggingToFullscreenAllowed": True,
            "splitRatio": 0.5,
            "minWidthDp": 600,
            "minSmallestWidthDp": 600,
            "clearTop": True,
            "finishPrimaryWithSecondary": 0,
            "finishSecondaryWithPrimary": 2,
            "middle": True,
        }, by_name["com.sina.weibo"])
        self.assertEqual([{
            "package": "com.sina.weibo",
            "source": "hand-authored",
            "testedVersionName": "16.8.0",
            "testedVersionCode": 8105,
            "testedApkSha256":
                "77ecb1d7918367c8f8d9543e9526d6c1e5718ce351a63177b932c78525ee1023",
            "replaces": "zui-18.0.10.335 conversion",
        }], self.audit["localOverrides"])
        # No provenance key leaks into the shipped row -- the parser ignores anything starting
        # with an underscore, but 3 MB of rules is not the place for a changelog.
        for rule in self.packages:
            self.assertFalse([key for key in rule if key.startswith("_")],
                             rule["name"])

    def test_local_layer_is_declared_converted_and_accounted_for(self):
        declared = self.local["fromZuiCorpus"]["packages"]
        self.assertEqual(sorted(declared), declared)
        self.assertEqual(len(declared), len(set(declared)))
        # 77 declared: 76 converted from Lenovo's rows plus com.sina.weibo, which is declared
        # here too but authored by hand, so its conversion is skipped rather than overridden.
        self.assertEqual(LOCAL_FROM_ZUI + LOCAL_HAND_AUTHORED, len(declared))
        self.assertEqual(LOCAL_FROM_ZUI, len(self.audit["localAdditions"]))
        self.assertEqual(
            LOCAL_HAND_AUTHORED, len(self.audit["localOverrides"]))
        self.assertEqual(
            LOCAL_FROM_ZUI + LOCAL_HAND_AUTHORED, len(self.local_rows))

        # Nothing in the local layer duplicates the import: where both sources have a package,
        # 3.01.48 wins, and the declaration only names packages no other source has.
        self.assertEqual([], self.audit["localSkippedAlreadyImported"])
        imported_names = {rule["name"] for rule in self.imported}
        self.assertEqual(set(), set(declared) & imported_names)

        zui = json.loads(self.zui_path.read_text(encoding="utf-8"))
        zui_names = {row["name"] for row in zui["packages"]}
        self.assertEqual(288, len(zui_names))
        self.assertTrue(set(declared) <= zui_names)
        # The 211 Lenovo packages the import already covers stay covered by the import.
        self.assertEqual(211, len(zui_names & imported_names))

        # Every converted row carries the same effective presentation values as an imported one,
        # because the runtime parser's own fallbacks are the legacy five-rule ones.
        for rule in self.local_rows:
            self.assertIs(True, rule.get("middle"), rule["name"])
            self.assertNotIn("middleRatio", rule)
            self.assertEqual(0.5, rule["splitRatio"], rule["name"])
            self.assertEqual(600, rule["minWidthDp"], rule["name"])
            self.assertEqual(600, rule["minSmallestWidthDp"], rule["name"])
            self.assertIs(True, rule["clearTop"], rule["name"])
            self.assertEqual(0, rule["finishPrimaryWithSecondary"], rule["name"])
            self.assertEqual(2, rule["finishSecondaryWithPrimary"], rule["name"])
            self.assertIn("showEmbeddingDivider", rule)
            # A local row that could do nothing would be the #87 shape all over again.
            self.assertTrue(rule.get("activityPairs"), rule["name"])

        # The three departures from Lenovo's data, each counted rather than described.
        self.assertEqual(39, len(self.audit["localDividerDefaulted"]))
        self.assertEqual(
            [("com.arivoc.ky", "forceFullScreenPages"),
             ("com.tal.tiku", "forceFullScreenPages")],
            [(item["package"], item["attribute"])
             for item in self.audit["localTypoRecoveries"]])
        self.assertEqual(
            [("com.baidu.searchbox.lite", "skipMultiWindowMode"),
             ("com.cat.readall", "dimOnTaskFragment"),
             ("com.cat.readall", "shouldPausePrimaryActivity"),
             ("com.cat.readall", "skipMultiWindowMode"),
             ("com.jianpian.xiaoxigua", "showSurfaceViewBackground"),
             ("com.jianpian.xiaoxigua", "skipMultiWindowMode"),
             ("com.sup.android.superb", "skipMultiWindowMode"),
             ("com.tencent.tim", "skipMultiWindowMode")],
            sorted((item["package"], item["attribute"])
                   for item in self.audit["localUnmappedAttributes"]))
        # mainPage without defaultRelate is inert for this parser, and every one of them is also
        # an activityPairs source, so dropping it loses nothing. Verified, not assumed.
        self.assertEqual(
            LOCAL_FROM_ZUI, len(self.audit["localIgnoredScalarKeys"]))
        by_name = {row["name"]: row for row in zui["packages"]}
        for item in self.audit["localIgnoredScalarKeys"]:
            self.assertEqual("mainPage", item["attribute"])
            sources = {pair["from"]
                       for pair in by_name[item["package"]]["activityPairs"]}
            self.assertIn(item["value"], sources, item["package"])

    def test_reviewed_whitespace_recoveries_and_primary_wildcard_drop(self):
        reason = "explicitly reviewed HyperOS 3.01.48 whitespace exception"
        self.assertEqual([
            {
                "package": "com.cmcc.cmvideo",
                "row": 3063,
                "attribute": "activityRule",
                "value": (
                    "com.cmvideo.mgvui.PersonalElderModeActivity, "
                    "com.cmcc.cmvideo.main.teeny.TeenModeOpenActivity"),
                "rawToken": (
                    " com.cmcc.cmvideo.main.teeny.TeenModeOpenActivity"),
                "output": (
                    "com.cmcc.cmvideo.main.teeny.TeenModeOpenActivity"),
                "reason": reason,
            },
            {
                "package": "com.ygkj.chelaile.standard",
                "row": 1254,
                "attribute": "transitionRules",
                "value": (
                    "dev.xesam.chelaile.app.module.city.CityGuideActivity, "
                    "dev.xesam.chelaile.app.module.func.SplashActivity"),
                "rawToken": (
                    " dev.xesam.chelaile.app.module.func.SplashActivity"),
                "output": (
                    "dev.xesam.chelaile.app.module.func.SplashActivity"),
                "reason": reason,
            },
            {
                "package": "com.zhongan.ibank",
                "row": 3068,
                "attribute": "splitPairRule",
                "value": (
                    "com.zhongan.beeline.bridge.flutter."
                    "ZABankMainFlutterActivity:*, com.zhongan.stack.activity."
                    "IVRNActivity1:*"),
                "rawToken": " com.zhongan.stack.activity.IVRNActivity1:*",
                "output": {
                    "from": "com.zhongan.stack.activity.IVRNActivity1",
                    "to": "*",
                },
                "reason": reason,
            },
            {
                "package": "dxwt.questionnaire.ui",
                "row": 3501,
                "attribute": "splitPairRule",
                "value": (
                    "com.dxwt.community.activity.main.WeexHomeActivity :*"),
                "rawToken": (
                    "com.dxwt.community.activity.main.WeexHomeActivity :*"),
                "output": {
                    "from": (
                        "com.dxwt.community.activity.main.WeexHomeActivity"),
                    "to": "*",
                },
                "reason": reason,
            },
        ], self.audit["recoveredWhitespaceTokens"])

        wildcard_reason = (
            "HyperOS primary matching treats '*' as a literal substring; "
            "it cannot match an activity class name")
        self.assertEqual([{
            "package": "com.wzsykj.wei",
            "row": 3169,
            "attribute": "splitPairRule",
            "value": (
                "com.zhouyu.music.activities.MainActivity:*,"
                "com.zhouyu.music.activities.List.CommonListActivity:*,"
                "*:com.zhouyu.music.activities.PlayActivity,"
                "com.zhouyu.music.activities.MvVideoActivity:*"),
            "token": "*:com.zhouyu.music.activities.PlayActivity",
            "relation": {
                "from": "*",
                "to": "com.zhouyu.music.activities.PlayActivity",
            },
            "reason": wildcard_reason,
        }], self.audit["droppedPrimaryWildcardRelations"])

        by_name = {rule["name"]: rule for rule in self.imported}
        self.assertEqual([{
            "from": "com.dxwt.community.activity.main.WeexHomeActivity",
            "to": "*",
        }], by_name["dxwt.questionnaire.ui"]["activityPairs"])
        self.assertEqual([
            {"from": "com.zhouyu.music.activities.MainActivity", "to": "*"},
            {
                "from": "com.zhouyu.music.activities.List.CommonListActivity",
                "to": "*",
            },
            {
                "from": "com.zhouyu.music.activities.MvVideoActivity",
                "to": "*",
            },
        ], by_name["com.wzsykj.wei"]["activityPairs"])

    def test_release_ratios_and_all_loss_records_are_explicit(self):
        self.assertEqual(Counter({
            0.3: 275,
            0.4: 8,
            0.42: 3,
            0.5: 6_670,
            0.7: 12,
        }), Counter(
            rule["splitRatio"]
            for rule in self.imported
            if "splitRatio" in rule
        ))
        self.assertEqual(
            Counter({600: 6_965, 900: 3}),
            Counter(rule["minWidthDp"] for rule in self.imported),
        )
        self.assertEqual(
            Counter({600: 6_968}),
            Counter(
                rule["minSmallestWidthDp"]
                for rule in self.imported
            ),
        )
        self.assertEqual(
            Counter({True: 6_963, False: 5}),
            Counter(rule["clearTop"] for rule in self.imported),
        )
        self.assertEqual(
            Counter({0: 6_968}),
            Counter(
                rule["finishPrimaryWithSecondary"]
                for rule in self.imported
            ),
        )
        self.assertEqual(
            Counter({2: 6_958, 0: 10}),
            Counter(
                rule["finishSecondaryWithPrimary"]
                for rule in self.imported
            ),
        )
        self.assertEqual(
            Counter({True: 5_504, False: 1_464}),
            Counter(
                rule["showEmbeddingDivider"]
                for rule in self.imported
            ),
        )
        self.assertEqual(
            Counter({True: 6_603, False: 365}),
            Counter(
                rule["dividerDraggingToFullscreenAllowed"]
                for rule in self.imported
            ),
        )
        self.assertEqual(
            Counter({True: 5_480, False: 24}),
            Counter(
                rule["dividerDraggingToFullscreenAllowed"]
                for rule in self.imported
                if rule["showEmbeddingDivider"]
            ),
        )
        recovered = self.audit["recoveredPairTokens"]
        self.assertEqual(
            ["com.boohee.box", "com.kurogame.kjq"],
            [item["package"] for item in recovered],
        )
        self.assertEqual([
            [
                "com.boohee.module_tools.diet.ui.activity.DietRecordActivity",
                "com.boohee.module_tools.diet.ui.activity.AddFoodActivity",
            ],
            [
                "com.kurogame.kjq.profile.ui.activity.ContainHeadActivity",
                "com.kurogame.kjq.home.ui.activity.HomeDiscuAreaActivity",
            ],
        ], [
            [relation["from"] for relation in item["recovered"]]
            for item in recovered
        ])
        for key in (
            "excludedFullRulePackages",
            "droppedMalformedPackages",
            "malformedPairTokens",
            "recoveredPairTokens",
            "recoveredWhitespaceTokens",
            "droppedPrimaryWildcardRelations",
            "malformedPlaceholderTokens",
            "malformedComponentTokens",
            "mappedPlaceholderConditions",
            "ignoredRuleDefaults",
            "ruleDefaultFallbacks",
            "unmappedAttributes",
            "unmappedSettingAttributes",
            "orphanSettingRows",
            "unmappedFlagDirectives",
        ):
            for item in self.audit[key]:
                with self.subTest(key=key, package=item.get("package")):
                    self.assertTrue(item.get("package"))
                    self.assertIn("value", item)


if __name__ == "__main__":
    unittest.main()
