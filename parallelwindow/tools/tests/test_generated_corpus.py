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
# ⚠️ The three hashes ABOVE pin the INPUT and must never move without a new
# source-lock review. The two BELOW pin the OUTPUT and moved once, deliberately,
# when the unguarded-autoPrimary gate landed (#81). If a change moves an input
# hash, stop -- that is a different upstream release, not a conversion change.
PRODUCT_SHA256 = "08fedbdc94943fd34074a574ec8712fd70bff571db3de0379a36562cb8526c2b"
AUDIT_SHA256 = "0827d284420b702fc916dc911ad4a6d3557ecc22ea39ab71d92331e9fb7db0ea"
MAX_DEVICE_RULE_BYTES = 8 * 1024 * 1024


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

    def test_product_has_a_reviewed_golden_digest_and_is_loader_bounded(self):
        self.assertEqual(PRODUCT_SHA256, sha256(self.output_path))
        self.assertEqual(AUDIT_SHA256, sha256(self.audit_path))
        self.assertLess(self.output_path.stat().st_size, MAX_DEVICE_RULE_BYTES)
        generated = self.output["_generated"]
        self.assertEqual(RULES_SHA256, generated["renderedRulesSha256"])
        self.assertEqual(SETTINGS_SHA256, generated["renderedSettingsSha256"])

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
            "imported_auto_primary": 4_940,
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
            # ★ THESE TWO ARE THE PRODUCT'S NUMBERS, AND SINCE #81 THEY ARE NO
            # LONGER THE RELEASE'S. The release ships 6,952 enabled / 16
            # disabled. The unguarded-autoPrimary gate default-disables 4,834
            # more -- rows where upstream declared a package embeddable and
            # said nothing about how, so the primary is this importer's
            # invention and must not also inherit upstream's FINISH_ADJACENT.
            #
            # The release numbers stay recoverable and therefore still pinned:
            #   default_disabled - imported_auto_primary_default_disabled
            #     = 4850 - 4834 = 16
            # and the gate count below is asserted independently, so neither
            # number can drift without the other being wrong too.
            "default_enabled": 2_118,
            "default_disabled": 4_850,
            "imported_auto_primary_default_disabled": 4_834,
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
        packages = self.output["packages"]
        names = [rule["name"] for rule in packages]
        self.assertEqual(sorted(names), names)
        self.assertEqual(len(names), len(set(names)))
        self.assertEqual(6_968, len(names))
        for removed_seed in (
            "com.android.contacts",
            "org.lineageos.etar",
            "org.lineageos.glimpse",
        ):
            self.assertNotIn(removed_seed, names)
        for rule in packages:
            self.assertTrue(
                rule.get("autoPrimary") is True
                or bool(rule.get("activityPairs"))
                or bool(rule.get("placeholderPairs")),
                rule["name"],
            )
            self.assertFalse(any(key.startswith("_tested") for key in rule))

    def test_official_taobao_rule_preserves_both_placeholders_and_default(self):
        by_name = {rule["name"]: rule for rule in self.output["packages"]}
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

    def test_official_final_weibo_row_selects_full_mode_and_is_excluded(self):
        names = {rule["name"] for rule in self.output["packages"]}
        self.assertNotIn("com.sina.weibo", names)
        exclusions = {
            item["package"]: item
            for item in self.audit["excludedFullRulePackages"]
        }
        self.assertEqual({
            "package": "com.sina.weibo",
            "row": 7793,
            "value": "nra:cr:rcr:nr:uc",
        }, exclusions["com.sina.weibo"])

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

        by_name = {rule["name"]: rule for rule in self.output["packages"]}
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
            for rule in self.output["packages"]
            if "splitRatio" in rule
        ))
        self.assertEqual(
            Counter({600: 6_965, 900: 3}),
            Counter(rule["minWidthDp"] for rule in self.output["packages"]),
        )
        self.assertEqual(
            Counter({600: 6_968}),
            Counter(
                rule["minSmallestWidthDp"]
                for rule in self.output["packages"]
            ),
        )
        self.assertEqual(
            Counter({True: 6_963, False: 5}),
            Counter(rule["clearTop"] for rule in self.output["packages"]),
        )
        self.assertEqual(
            Counter({0: 6_968}),
            Counter(
                rule["finishPrimaryWithSecondary"]
                for rule in self.output["packages"]
            ),
        )
        self.assertEqual(
            Counter({2: 6_958, 0: 10}),
            Counter(
                rule["finishSecondaryWithPrimary"]
                for rule in self.output["packages"]
            ),
        )
        self.assertEqual(
            Counter({True: 5_504, False: 1_464}),
            Counter(
                rule["showEmbeddingDivider"]
                for rule in self.output["packages"]
            ),
        )
        self.assertEqual(
            Counter({True: 6_603, False: 365}),
            Counter(
                rule["dividerDraggingToFullscreenAllowed"]
                for rule in self.output["packages"]
            ),
        )
        self.assertEqual(
            Counter({True: 5_480, False: 24}),
            Counter(
                rule["dividerDraggingToFullscreenAllowed"]
                for rule in self.output["packages"]
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
