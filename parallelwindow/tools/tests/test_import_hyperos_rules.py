#!/usr/bin/env python3

# Copyright (C) 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0

"""Unit contracts for the pinned HyperOS 3.01.48 importer.

The XML literals in this file are deliberately independent of the importer.  They
pin the vendor's last-row-wins policy and every lossy conversion boundary before
the production corpus is regenerated.
"""

import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from xml.sax.saxutils import quoteattr


TOOLS_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS_DIR))

import import_hyperos_rules as importer


def xml_element(tag: str, **attributes: str) -> str:
    rendered = " ".join(
        f"{key}={quoteattr(value)}" for key, value in attributes.items())
    return f"  <{tag} {rendered}/>"


class HyperOsRuleImporterTest(unittest.TestCase):
    def generate(self, package_rows, setting_rows=()):
        rules = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                 "<package_config>\n"
                 + "\n".join(package_rows)
                 + "\n</package_config>\n").encode()
        settings = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    "<setting_rule>\n"
                    + "\n".join(setting_rows)
                    + "\n</setting_rule>\n").encode()
        lock = {
            "upstream": {
                "repository": "https://github.com/sothx/mipad-magic-window",
                "tag": "test",
                "commit": "0" * 40,
            },
            "rendered_xml": {"sha256": hashlib.sha256(rules).hexdigest()},
            "rendered_settings_xml": {
                "sha256": hashlib.sha256(settings).hexdigest(),
            },
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            rules_path = root / "rules.xml"
            settings_path = root / "settings.xml"
            lock_path = root / "lock.json"
            rules_path.write_bytes(rules)
            settings_path.write_bytes(settings)
            lock_path.write_text(json.dumps(lock), encoding="utf-8")
            return importer.generate(rules_path, settings_path, lock_path)

    def test_official_last_row_wins_even_when_the_final_row_is_malformed(self):
        document, audit = self.generate([
            xml_element(
                "package", name="com.example.duplicate",
                splitPairRule=".First:*"),
            xml_element(
                "package", name="com.example.duplicate",
                splitPairRule=":"),
        ])

        self.assertNotIn(
            "com.example.duplicate",
            {rule["name"] for rule in document["packages"]},
        )
        self.assertEqual(1, audit["counts"]["dropped_malformed_packages"])
        self.assertEqual(
            [{
                "package": "com.example.duplicate",
                "row": 2,
                "attribute": "splitPairRule",
                "value": ":",
                "reason": "no valid activity pair",
                "outcome": "package dropped",
            }],
            audit["malformedRuleRows"],
        )
        self.assertEqual([{
            "package": "com.example.duplicate",
            "row": 2,
            "attribute": "splitPairRule",
            "value": ":",
            "reason": "no valid activity pair",
        }], audit["droppedMalformedPackages"])

    def test_full_mode_is_excluded_but_a_setting_flag_does_not_erase_embedding(self):
        document, audit = self.generate([
            xml_element("package", name="com.example.full", fullRule="nra"),
            xml_element("package", name="com.example.fixed"),
            xml_element("package", name="com.example.embedded"),
        ], [
            xml_element(
                "setting", name="com.example.fixed",
                embeddedEnable="false", fixedOrientationEnable="true"),
            xml_element(
                "setting", name="com.example.embedded",
                embeddedEnable="true", fixedOrientationEnable="false"),
        ])

        self.assertEqual(
            ["com.example.embedded", "com.example.fixed"],
            [rule["name"] for rule in document["packages"]],
        )
        self.assertEqual(
            [{"package": "com.example.full", "row": 1, "value": "nra"}],
            audit["excludedFullRulePackages"],
        )
        fixed = document["packages"][1]
        self.assertFalse(fixed["defaultEnabled"])
        self.assertIn({
            "package": "com.example.fixed",
            "row": 1,
            "attribute": "fixedOrientationEnable",
            "value": "true",
        }, audit["unmappedSettingAttributes"])

    def test_every_valid_placeholder_relation_is_preserved(self):
        document, audit = self.generate([
            xml_element(
                "package",
                name="com.taobao.taobao",
                placeholder=(
                    "com.taobao.tao.welcome.Welcome:"
                    "com.taobao.tao.MagicWindowActivity,"
                    "com.taobao.tao.TBMainActivity:"
                    "com.taobao.tao.MagicWindowActivity"
                ),
            ),
        ])

        rule = document["packages"][0]
        self.assertEqual([
            {
                "from": "com.taobao.tao.welcome.Welcome",
                "to": "com.taobao.tao.MagicWindowActivity",
                "waitForContent": True,
            },
            {
                "from": "com.taobao.tao.TBMainActivity",
                "to": "com.taobao.tao.MagicWindowActivity",
            },
        ], rule["placeholderPairs"])
        self.assertEqual([], audit["malformedPlaceholderTokens"])
        self.assertEqual([{
            "package": "com.taobao.taobao",
            "row": 1,
            "attribute": "placeholder",
            "value": (
                "com.taobao.tao.welcome.Welcome:"
                "com.taobao.tao.MagicWindowActivity,"
                "com.taobao.tao.TBMainActivity:"
                "com.taobao.tao.MagicWindowActivity"
            ),
            "from": "com.taobao.tao.welcome.Welcome",
            "to": "com.taobao.tao.MagicWindowActivity",
            "output": "waitForContent=true",
            "reason": "HyperOS TB_WELCOME content-view predicate",
        }], audit["mappedPlaceholderConditions"])

    def test_rule_fields_and_all_three_flattened_component_forms_are_mapped(self):
        document, audit = self.generate([
            xml_element(
                "package",
                name="com.example.app",
                splitPairRule=(
                    "/com.vendor.Main:*,"
                    "com.example.app/.Home:*,"
                    "com.example.app/com.other.Detail:*"
                ),
                placeholder="com.example.app/.Home:/com.vendor.Placeholder",
                activityRule="com.example.app/.Login,.Camera",
                transitionRules="com.example.app/com.other.Router,.Splash",
                isShowDivider="true",
                splitRatio="0.4",
                splitMinWidth="900",
                clearTop="false",
                relaunch="true",
            ),
        ], [
            xml_element(
                "setting", name="com.example.app", embeddedEnable="false",
                fixedOrientationEnable="false"),
        ])

        rule = document["packages"][0]
        self.assertEqual([
            {"from": "com.vendor.Main", "to": "*"},
            {"from": "com.example.app.Home", "to": "*"},
            {"from": "com.other.Detail", "to": "*"},
        ], rule["activityPairs"])
        self.assertEqual([{
            "from": "com.example.app.Home",
            "to": "com.vendor.Placeholder",
        }], rule["placeholderPairs"])
        self.assertEqual(
            ["com.example.app.Login", ".Camera"],
            rule["forceFullscreenPages"],
        )
        self.assertEqual(
            ["com.other.Router", ".Splash"], rule["transActivities"])
        self.assertTrue(rule["showEmbeddingDivider"])
        self.assertFalse(rule["dividerDraggingToFullscreenAllowed"])
        self.assertEqual(0.4, rule["splitRatio"])
        self.assertEqual(900, rule["minWidthDp"])
        self.assertEqual(600, rule["minSmallestWidthDp"])
        self.assertFalse(rule["clearTop"])
        self.assertFalse(rule["suppressRelaunch"])
        self.assertFalse(rule["defaultEnabled"])
        self.assertEqual(7, audit["counts"]["normalized_component_tokens"])

    def test_divider_fields_materialize_hyperos_effective_semantics(self):
        document, audit = self.generate([
            xml_element(
                "package", name="com.example.scale",
                isShowDivider="true", scaleMode="1",
                supportFullSize="true"),
            xml_element(
                "package", name="com.example.normal",
                isShowDivider="true", scaleMode="0"),
        ])

        by_name = {rule["name"]: rule for rule in document["packages"]}
        scaled = by_name["com.example.scale"]
        self.assertFalse(scaled["showEmbeddingDivider"])
        self.assertTrue(scaled["dividerDraggingToFullscreenAllowed"])
        normal = by_name["com.example.normal"]
        self.assertTrue(normal["showEmbeddingDivider"])
        self.assertFalse(normal["dividerDraggingToFullscreenAllowed"])

        scale_audit = next(
            item for item in audit["unmappedAttributes"]
            if item["package"] == "com.example.scale"
            and item["attribute"] == "scaleMode"
        )
        self.assertEqual(
            "mapped into showEmbeddingDivider; remaining scale semantics unsupported",
            scale_audit["mappedSideEffect"],
        )
        self.assertFalse(any(
            item["attribute"] == "supportFullSize"
            for item in audit["unmappedAttributes"]
        ))

    def test_vendor_effective_finish_and_clear_defaults_are_materialized(self):
        document, _ = self.generate([
            xml_element("package", name="com.example.defaults"),
            xml_element(
                "package", name="com.example.overrides",
                clearTop="false",
                finishPrimaryWithSecondary="2",
                finishSecondaryWithPrimary="0",
            ),
        ])

        by_name = {rule["name"]: rule for rule in document["packages"]}
        defaults = by_name["com.example.defaults"]
        self.assertTrue(defaults["clearTop"])
        self.assertEqual(0.5, defaults["splitRatio"])
        self.assertEqual(600, defaults["minWidthDp"])
        self.assertEqual(600, defaults["minSmallestWidthDp"])
        self.assertEqual(0, defaults["finishPrimaryWithSecondary"])
        self.assertEqual(2, defaults["finishSecondaryWithPrimary"])

        overrides = by_name["com.example.overrides"]
        self.assertFalse(overrides["clearTop"])
        self.assertEqual(2, overrides["finishPrimaryWithSecondary"])
        self.assertEqual(0, overrides["finishSecondaryWithPrimary"])

    def test_rule_default_is_fallback_only_when_no_setting_row_exists(self):
        # Every fixture carries an explicit splitPairRule. That was once load-
        # bearing: while the unguarded-autoPrimary gate existed (#81), a bare
        # row would have been default-disabled by the gate instead of by the
        # defaultSettings/embeddedEnable resolution this test is about, which is
        # exactly what happened when the gate landed. The gate is gone with
        # autoPrimary (#87), so the splitPairRule is now only keeping the
        # fixtures realistic. The subject here is WHERE the default comes from,
        # and it should stay that.
        document, audit = self.generate([
            xml_element(
                "package", name="com.example.fallback_off",
                splitPairRule=".First:*", defaultSettings="false"),
            xml_element(
                "package", name="com.example.fallback_on",
                splitPairRule=".First:*", defaultSettings="true"),
            xml_element(
                "package", name="com.example.setting_wins_on",
                splitPairRule=".First:*", defaultSettings="false"),
            xml_element(
                "package", name="com.example.setting_wins_off",
                splitPairRule=".First:*", defaultSettings="true"),
            xml_element(
                "package", name="com.example.setting_missing_key",
                splitPairRule=".First:*", defaultSettings="true"),
        ], [
            xml_element(
                "setting", name="com.example.setting_wins_on",
                embeddedEnable="true"),
            xml_element(
                "setting", name="com.example.setting_wins_off",
                embeddedEnable="false"),
            xml_element(
                "setting", name="com.example.setting_missing_key",
                fixedOrientationEnable="false"),
        ])

        by_name = {rule["name"]: rule for rule in document["packages"]}
        self.assertFalse(by_name["com.example.fallback_off"]["defaultEnabled"])
        self.assertNotIn("defaultEnabled", by_name["com.example.fallback_on"])
        self.assertNotIn("defaultEnabled", by_name["com.example.setting_wins_on"])
        self.assertFalse(
            by_name["com.example.setting_wins_off"]["defaultEnabled"])
        self.assertFalse(
            by_name["com.example.setting_missing_key"]["defaultEnabled"])
        self.assertEqual(2, len(audit["ruleDefaultFallbacks"]))
        self.assertEqual(3, len(audit["ignoredRuleDefaults"]))

    def test_row_without_split_pair_rule_gets_no_routing_and_keeps_its_default(
            self):
        """#87. A bare row means "on the list", not "split with defaults".

        HyperOS's split predicate requires a splitPairRule and 1,305 of the
        firmware's 1,946 rows carry only a package name, so there are no engine
        defaults for a bare row to inherit. This importer used to synthesise
        `autoPrimary` for exactly these rows and then default-disable the
        dangerous ones (#81); both the synthesis and the gate are gone, and a
        bare row now converts to presentation attributes and nothing else.
        """
        document, audit = self.generate([
            xml_element("package", name="com.example.bare"),
            xml_element(
                "package", name="com.example.trans",
                transitionRules=".Splash"),
            xml_element(
                "package", name="com.example.explicit",
                splitPairRule=".First:*"),
            xml_element(
                "package", name="com.example.already_off",
                defaultSettings="false"),
        ], [])

        by_name = {rule["name"]: rule for rule in document["packages"]}
        for bare in ("com.example.bare", "com.example.trans"):
            self.assertNotIn("activityPairs", by_name[bare], bare)
            self.assertNotIn("autoPrimary", by_name[bare], bare)
        self.assertEqual(
            [{"from": ".First", "to": "*"}],
            by_name["com.example.explicit"]["activityPairs"])

        # Nothing in the conversion decides a default any more; only upstream's
        # own defaultSettings/embeddedEnable resolution does.
        for untouched in (
            "com.example.bare",
            "com.example.trans",
            "com.example.explicit",
        ):
            self.assertNotIn(
                "defaultEnabled", by_name[untouched],
                f"{untouched} must keep upstream's default")
        self.assertIs(
            False, by_name["com.example.already_off"]["defaultEnabled"])
        self.assertEqual(3, audit["counts"]["default_enabled"])
        self.assertEqual(1, audit["counts"]["default_disabled"])

    def test_malformed_and_unmapped_values_are_auditable_not_just_counted(self):
        document, audit = self.generate([
            xml_element(
                "package",
                name="com.example.lossy",
                splitPairRule=".Main:*,,broken,.List:.Detail",
                scaleMode="1",
                skipSelfAdaptive="true",
            ),
        ])

        self.assertEqual([
            {"from": ".Main", "to": "*"},
            {"from": ".List", "to": ".Detail"},
        ], document["packages"][0]["activityPairs"])
        self.assertEqual([
            {
                "package": "com.example.lossy",
                "row": 1,
                "attribute": "splitPairRule",
                "value": ".Main:*,,broken,.List:.Detail",
                "token": "",
            },
            {
                "package": "com.example.lossy",
                "row": 1,
                "attribute": "splitPairRule",
                "value": ".Main:*,,broken,.List:.Detail",
                "token": "broken",
            },
        ], audit["malformedPairTokens"])
        self.assertEqual([
            {
                "package": "com.example.lossy", "row": 1,
                "attribute": "scaleMode", "value": "1",
                "mappedSideEffect": (
                    "mapped into showEmbeddingDivider; remaining scale "
                    "semantics unsupported"),
            },
            {
                "package": "com.example.lossy", "row": 1,
                "attribute": "skipSelfAdaptive", "value": "true",
            },
        ], audit["unmappedAttributes"])

    def test_unknown_whitespace_pair_is_dropped_without_silent_repair(self):
        source_value = ".Main:*, .Secondary:*"
        document, audit = self.generate([
            xml_element(
                "package", name="com.example.whitespace",
                splitPairRule=source_value),
        ])

        self.assertEqual(
            [{"from": ".Main", "to": "*"}],
            document["packages"][0]["activityPairs"],
        )
        self.assertEqual([{
            "package": "com.example.whitespace",
            "row": 1,
            "attribute": "splitPairRule",
            "value": source_value,
            "token": " .Secondary:*",
            "reason": "unapproved whitespace",
        }], audit["malformedPairTokens"])
        self.assertEqual([], audit["recoveredWhitespaceTokens"])

    def test_reviewed_whitespace_tokens_are_recovered_and_audited(self):
        document, audit = self.generate([
            xml_element(
                "package", name="com.zhongan.ibank",
                splitPairRule=(
                    "com.zhongan.beeline.bridge.flutter."
                    "ZABankMainFlutterActivity:*, com.zhongan.stack.activity."
                    "IVRNActivity1:*")),
            xml_element(
                "package", name="dxwt.questionnaire.ui",
                splitPairRule=(
                    "com.dxwt.community.activity.main.WeexHomeActivity :*")),
            xml_element(
                "package", name="com.cmcc.cmvideo",
                activityRule=(
                    "com.cmvideo.mgvui.PersonalElderModeActivity, "
                    "com.cmcc.cmvideo.main.teeny.TeenModeOpenActivity")),
            xml_element(
                "package", name="com.ygkj.chelaile.standard",
                transitionRules=(
                    "dev.xesam.chelaile.app.module.city.CityGuideActivity, "
                    "dev.xesam.chelaile.app.module.func.SplashActivity")),
        ])

        by_name = {rule["name"]: rule for rule in document["packages"]}
        self.assertEqual([
            {
                "from": (
                    "com.zhongan.beeline.bridge.flutter."
                    "ZABankMainFlutterActivity"),
                "to": "*",
            },
            {"from": "com.zhongan.stack.activity.IVRNActivity1", "to": "*"},
        ], by_name["com.zhongan.ibank"]["activityPairs"])
        self.assertEqual([{
            "from": "com.dxwt.community.activity.main.WeexHomeActivity",
            "to": "*",
        }], by_name["dxwt.questionnaire.ui"]["activityPairs"])
        self.assertEqual([
            "com.cmvideo.mgvui.PersonalElderModeActivity",
            "com.cmcc.cmvideo.main.teeny.TeenModeOpenActivity",
        ], by_name["com.cmcc.cmvideo"]["forceFullscreenPages"])
        self.assertEqual([
            "dev.xesam.chelaile.app.module.city.CityGuideActivity",
            "dev.xesam.chelaile.app.module.func.SplashActivity",
        ], by_name["com.ygkj.chelaile.standard"]["transActivities"])

        recovered = {
            item["package"]: item
            for item in audit["recoveredWhitespaceTokens"]
        }
        self.assertEqual({
            "com.zhongan.ibank",
            "dxwt.questionnaire.ui",
            "com.cmcc.cmvideo",
            "com.ygkj.chelaile.standard",
        }, set(recovered))
        self.assertEqual(
            " com.zhongan.stack.activity.IVRNActivity1:*",
            recovered["com.zhongan.ibank"]["rawToken"],
        )
        self.assertEqual(
            {
                "from": "com.zhongan.stack.activity.IVRNActivity1",
                "to": "*",
            },
            recovered["com.zhongan.ibank"]["output"],
        )
        self.assertEqual(
            "com.dxwt.community.activity.main.WeexHomeActivity :*",
            recovered["dxwt.questionnaire.ui"]["rawToken"],
        )
        self.assertEqual(
            " com.cmcc.cmvideo.main.teeny.TeenModeOpenActivity",
            recovered["com.cmcc.cmvideo"]["rawToken"],
        )
        self.assertEqual(
            " dev.xesam.chelaile.app.module.func.SplashActivity",
            recovered["com.ygkj.chelaile.standard"]["rawToken"],
        )
        self.assertTrue(all(
            item["reason"] == (
                "explicitly reviewed HyperOS 3.01.48 whitespace exception")
            for item in recovered.values()
        ))

    def test_primary_wildcard_relation_is_dropped_as_vendor_unmatchable(self):
        source_value = ".Main:*,*:.Detail,.Other:*"
        document, audit = self.generate([
            xml_element(
                "package", name="com.example.primary_wildcard",
                splitPairRule=source_value),
        ])

        self.assertEqual([
            {"from": ".Main", "to": "*"},
            {"from": ".Other", "to": "*"},
        ], document["packages"][0]["activityPairs"])
        self.assertEqual([{
            "package": "com.example.primary_wildcard",
            "row": 1,
            "attribute": "splitPairRule",
            "value": source_value,
            "token": "*:.Detail",
            "relation": {"from": "*", "to": ".Detail"},
            "reason": (
                "HyperOS primary matching treats '*' as a literal substring; "
                "it cannot match an activity class name"),
        }], audit["droppedPrimaryWildcardRelations"])

    def test_unambiguous_missing_comma_pair_is_recovered_and_audited(self):
        source_value = ".Main:*,com.example.A:*com.example.B:*"
        document, audit = self.generate([
            xml_element(
                "package", name="com.example.recovery",
                splitPairRule=source_value),
        ])

        self.assertEqual([
            {"from": ".Main", "to": "*"},
            {"from": "com.example.A", "to": "*"},
            {"from": "com.example.B", "to": "*"},
        ], document["packages"][0]["activityPairs"])
        self.assertEqual([], audit["malformedPairTokens"])
        self.assertEqual([{
            "package": "com.example.recovery",
            "row": 1,
            "attribute": "splitPairRule",
            "value": source_value,
            "token": "com.example.A:*com.example.B:*",
            "recovered": [
                {"from": "com.example.A", "to": "*"},
                {"from": "com.example.B", "to": "*"},
            ],
            "reason": "unambiguous missing comma between wildcard pairs",
        }], audit["recoveredPairTokens"])

    def test_input_hashes_are_mandatory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            rules = root / "rules.xml"
            settings = root / "settings.xml"
            lock = root / "lock.json"
            rules.write_bytes(b"<package_config/>")
            settings.write_bytes(b"<setting_rule/>")
            lock.write_text(json.dumps({
                "rendered_xml": {"sha256": "f" * 64},
                "rendered_settings_xml": {"sha256": "e" * 64},
            }), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "SHA-256"):
                importer.generate(rules, settings, lock)


if __name__ == "__main__":
    unittest.main()
