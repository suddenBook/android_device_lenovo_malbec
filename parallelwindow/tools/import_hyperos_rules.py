#!/usr/bin/env python3

# Copyright (C) 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0

"""Convert the pinned sothx/mipad-magic-window 3.01.48 corpus into Malbec rules.

⚠️ THAT IS A COMMUNITY COMPATIBILITY MODULE, NOT A HYPEROS FIRMWARE DUMP. This
docstring used to say "the pinned HyperOS release corpus", which overstated it.
A real /product/etc/embedded_rules_list.xml (HyperOS 3.1, Xiaomi Pad 8) has
1,946 rows and 89 splitPairRule; this release has 8,046 and 2,040, and carries
skipSelfAdaptive on every row where the firmware carries it once. It IS a
faithful superset of the firmware's package list (1,942 of 1,946) and keeps
1,686 of its splitPairRule values verbatim, so it is defensible to ship -- it is
just not Xiaomi's file, and the firmware is newer. Measurements:
work/notes/parallel-window-corpus-provenance.md.

The two XML inputs are vendored because the owner's cross-licensing
agreement permits AI-assisted processing and ROM redistribution.  Every run
verifies both files against ``hyperos_source_lock.json`` before parsing them.

Conversion follows the release's final row for each package. HyperOS inserts
every parsed row into its package map unconditionally, so a later malformed row
does not fall back to an earlier value. ``fullRule`` selects a vendor mode other
than activity embedding and is therefore excluded.
The separate fixed-orientation corpus is not an input; a setting row's
``fixedOrientationEnable`` does not erase a non-full embedded rule.  Every loss,
malformed token, and unsupported attribute is recorded with its package and
source value in the machine-readable audit.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
from typing import Any
import xml.etree.ElementTree as ET


DEFAULT_SPLIT_RATIO = 0.5
DEFAULT_MIN_WIDTH_DP = 600
DEFAULT_MIN_SMALLEST_WIDTH_DP = 600
DEFAULT_CLEAR_TOP = True
DEFAULT_FINISH_PRIMARY_WITH_SECONDARY = 0
DEFAULT_FINISH_SECONDARY_WITH_PRIMARY = 2
VALID_FINISH_BEHAVIORS = {0, 1, 2}
WAIT_FOR_CONTENT_PLACEHOLDER = (
    "com.taobao.taobao",
    "com.taobao.tao.welcome.Welcome",
    "com.taobao.tao.MagicWindowActivity",
)
MISSING_COMMA_WILDCARD_PAIRS = re.compile(
    r"^([^:,\s]+):\*([^:,\s]+):\*$")
REVIEWED_WHITESPACE_TOKENS = {
    (
        "com.zhongan.ibank",
        "splitPairRule",
        " com.zhongan.stack.activity.IVRNActivity1:*",
    ): "com.zhongan.stack.activity.IVRNActivity1:*",
    (
        "dxwt.questionnaire.ui",
        "splitPairRule",
        "com.dxwt.community.activity.main.WeexHomeActivity :*",
    ): "com.dxwt.community.activity.main.WeexHomeActivity:*",
    (
        "com.cmcc.cmvideo",
        "activityRule",
        " com.cmcc.cmvideo.main.teeny.TeenModeOpenActivity",
    ): "com.cmcc.cmvideo.main.teeny.TeenModeOpenActivity",
    (
        "com.ygkj.chelaile.standard",
        "transitionRules",
        " dev.xesam.chelaile.app.module.func.SplashActivity",
    ): "dev.xesam.chelaile.app.module.func.SplashActivity",
}
WHITESPACE_RECOVERY_REASON = (
    "explicitly reviewed HyperOS 3.01.48 whitespace exception")
PRIMARY_WILDCARD_DROP_REASON = (
    "HyperOS primary matching treats '*' as a literal substring; "
    "it cannot match an activity class name")

# Attributes that either affect the output or have an explicit classification.
# Everything else is copied to ``unmappedAttributes`` in the audit.
CLASSIFIED_RULE_ATTRIBUTES = {
    "name",
    "fullRule",
    "splitPairRule",
    "placeholder",
    "activityRule",
    "transitionRules",
    "isShowDivider",
    "supportFullSize",
    "defaultSettings",
    "splitRatio",
    "splitMinWidth",
    "clearTop",
    "finishPrimaryWithSecondary",
    "finishSecondaryWithPrimary",
    "relaunch",
    "flags",
}


class EntryError(ValueError):
    """A final source row cannot be converted without inventing semantics."""

    def __init__(self, attribute: str, value: str | None, reason: str):
        super().__init__(reason)
        self.attribute = attribute
        self.value = value
        self.reason = reason


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _read_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path}: JSON root must be an object")
    return value


def _verify_hash(path: Path, lock: dict[str, Any], lock_key: str) -> bytes:
    data = path.read_bytes()
    expected = lock.get(lock_key, {}).get("sha256")
    actual = _sha256(data)
    if not isinstance(expected, str) or actual != expected.lower():
        raise ValueError(
            f"{path}: SHA-256 {actual} does not match locked {expected!r}")
    return data


def _parse_xml(data: bytes, expected_root: str, source_name: str) -> ET.Element:
    try:
        root = ET.fromstring(data)
    except ET.ParseError as error:
        raise ValueError(f"{source_name}: invalid XML: {error}") from error
    if root.tag != expected_root:
        raise ValueError(
            f"{source_name}: expected <{expected_root}>, found <{root.tag}>")
    return root


def _strict_bool(value: str, field: str) -> bool:
    lowered = value.strip().lower()
    if lowered == "true":
        return True
    if lowered == "false":
        return False
    raise EntryError(field, value, "expected literal true or false")


def _optional_bool(attributes: dict[str, str], field: str) -> bool | None:
    value = attributes.get(field)
    return None if value is None else _strict_bool(value, field)


def _parse_float(value: str, field: str) -> float:
    try:
        parsed = float(value)
    except ValueError as error:
        raise EntryError(field, value, "not numeric") from error
    if not 0.0 < parsed < 1.0:
        raise EntryError(field, value, "must be between zero and one")
    return parsed


def _parse_non_negative_int(value: str, field: str) -> int:
    try:
        parsed = int(value)
    except ValueError as error:
        raise EntryError(field, value, "not an integer") from error
    if parsed < 0:
        raise EntryError(field, value, "must not be negative")
    return parsed


def _parse_finish_behavior(value: str, field: str) -> int:
    parsed = _parse_non_negative_int(value, field)
    if parsed not in VALID_FINISH_BEHAVIORS:
        raise EntryError(field, value, "must be 0, 1, or 2")
    return parsed


def _new_audit() -> dict[str, Any]:
    return {
        "counts": {},
        "duplicateRuleRows": [],
        "duplicateSettingRows": [],
        "invalidRuleRows": [],
        "invalidSettingRows": [],
        "excludedFullRulePackages": [],
        "malformedRuleRows": [],
        "droppedMalformedPackages": [],
        "malformedPairTokens": [],
        "recoveredPairTokens": [],
        "recoveredWhitespaceTokens": [],
        "droppedPrimaryWildcardRelations": [],
        "malformedPlaceholderTokens": [],
        "malformedComponentTokens": [],
        "normalizedComponents": [],
        "normalizedActivityRuleTokens": [],
        "mappedPlaceholderConditions": [],
        "ignoredRuleDefaults": [],
        "ruleDefaultFallbacks": [],
        "unmappedAttributes": [],
        "unmappedSettingAttributes": [],
        "orphanSettingRows": [],
        "unmappedFlagDirectives": [],
    }


def _normalization_event(
    audit: dict[str, Any], package: str, row: int, attribute: str,
    value: str, token: str, normalized: str, reason: str,
) -> None:
    audit["normalizedComponents"].append({
        "package": package,
        "row": row,
        "attribute": attribute,
        "value": value,
        "token": token,
        "normalized": normalized,
        "reason": reason,
    })


def _normalize_component(
    token: str,
    *,
    package: str,
    row: int,
    attribute: str,
    source_value: str,
    audit: dict[str, Any],
) -> str:
    value = token.strip()
    normalized = value
    reason: str | None = None
    if value.startswith("/"):
        normalized = value[1:]
        reason = "leading slash"
    elif "/" in value:
        flattened_package, class_name = value.split("/", 1)
        if class_name.startswith("."):
            normalized = flattened_package + class_name
            reason = "flattened relative class"
        else:
            normalized = class_name
            reason = "flattened qualified class"
    if reason is not None:
        _normalization_event(
            audit, package, row, attribute, source_value, value, normalized, reason)
    return normalized


def _malformed_token(
    audit: dict[str, Any], key: str, package: str, row: int,
    attribute: str, value: str, token: str, reason: str | None = None,
) -> None:
    item = {
        "package": package,
        "row": row,
        "attribute": attribute,
        "value": value,
        "token": token,
    }
    if reason is not None:
        item["reason"] = reason
    audit[key].append(item)


def _reviewed_whitespace_recovery(
    package: str, attribute: str, raw_token: str,
) -> str | None:
    if not any(character.isspace() for character in raw_token):
        return raw_token
    return REVIEWED_WHITESPACE_TOKENS.get((package, attribute, raw_token))


def _record_whitespace_recovery(
    audit: dict[str, Any], package: str, row: int, attribute: str,
    value: str, raw_token: str, output: Any,
) -> None:
    audit["recoveredWhitespaceTokens"].append({
        "package": package,
        "row": row,
        "attribute": attribute,
        "value": value,
        "rawToken": raw_token,
        "output": output,
        "reason": WHITESPACE_RECOVERY_REASON,
    })


def _parse_relations(
    value: str,
    *,
    package: str,
    row: int,
    attribute: str,
    audit_key: str,
    audit: dict[str, Any],
) -> list[dict[str, Any]]:
    relations: list[dict[str, Any]] = []
    for raw_token in value.split(","):
        token = _reviewed_whitespace_recovery(
            package, attribute, raw_token)
        if token is None:
            _malformed_token(
                audit, audit_key, package, row, attribute, value, raw_token,
                "unapproved whitespace")
            continue
        recovered_whitespace = token != raw_token
        if token.count(":") != 1:
            recovered_match = (
                MISSING_COMMA_WILDCARD_PAIRS.fullmatch(token)
                if attribute == "splitPairRule" else None)
            if recovered_match is not None:
                recovered = [
                    {
                        "from": _normalize_component(
                            component,
                            package=package,
                            row=row,
                            attribute=attribute,
                            source_value=value,
                            audit=audit,
                        ),
                        "to": "*",
                    }
                    for component in recovered_match.groups()
                ]
                audit["recoveredPairTokens"].append({
                    "package": package,
                    "row": row,
                    "attribute": attribute,
                    "value": value,
                    "token": token,
                    "recovered": recovered,
                    "reason": (
                        "unambiguous missing comma between wildcard pairs"),
                })
                relations.extend(recovered)
                continue
            _malformed_token(
                audit, audit_key, package, row, attribute, value, token)
            continue
        primary, secondary = (part.strip() for part in token.split(":", 1))
        if not primary or not secondary:
            _malformed_token(
                audit, audit_key, package, row, attribute, value, token)
            continue
        normalized_primary = _normalize_component(
            primary, package=package, row=row, attribute=attribute,
            source_value=value, audit=audit)
        normalized_secondary = _normalize_component(
            secondary, package=package, row=row, attribute=attribute,
            source_value=value, audit=audit)
        relation: dict[str, Any] = {
            "from": normalized_primary,
            "to": normalized_secondary,
        }
        if normalized_primary == "*":
            audit["droppedPrimaryWildcardRelations"].append({
                "package": package,
                "row": row,
                "attribute": attribute,
                "value": value,
                "token": raw_token,
                "relation": relation,
                "reason": PRIMARY_WILDCARD_DROP_REASON,
            })
            continue
        if (
            attribute == "placeholder"
            and (package, normalized_primary, normalized_secondary)
            == WAIT_FOR_CONTENT_PLACEHOLDER
        ):
            relation["waitForContent"] = True
            audit["mappedPlaceholderConditions"].append({
                "package": package,
                "row": row,
                "attribute": attribute,
                "value": value,
                "from": normalized_primary,
                "to": normalized_secondary,
                "output": "waitForContent=true",
                "reason": "HyperOS TB_WELCOME content-view predicate",
            })
        if recovered_whitespace:
            _record_whitespace_recovery(
                audit, package, row, attribute, value, raw_token,
                dict(relation))
        relations.append(relation)
    return relations


def _parse_component_list(
    value: str,
    *,
    package: str,
    row: int,
    attribute: str,
    allow_pair_wildcard_suffix: bool,
    audit: dict[str, Any],
) -> list[str]:
    components: list[str] = []
    for raw_token in value.split(","):
        token = _reviewed_whitespace_recovery(
            package, attribute, raw_token)
        if token is None:
            _malformed_token(
                audit, "malformedComponentTokens", package, row,
                attribute, value, raw_token, "unapproved whitespace")
            continue
        recovered_whitespace = token != raw_token
        if not token:
            _malformed_token(
                audit, "malformedComponentTokens", package, row,
                attribute, value, token)
            continue
        if ":" in token:
            activity, separator, suffix = token.partition(":")
            if allow_pair_wildcard_suffix and separator and suffix == "*" and activity:
                audit["normalizedActivityRuleTokens"].append({
                    "package": package,
                    "row": row,
                    "attribute": attribute,
                    "value": value,
                    "token": token,
                    "normalized": activity,
                })
                token = activity
            else:
                _malformed_token(
                    audit, "malformedComponentTokens", package, row,
                    attribute, value, token)
                continue
        normalized = _normalize_component(
            token, package=package, row=row, attribute=attribute,
            source_value=value, audit=audit)
        if recovered_whitespace:
            _record_whitespace_recovery(
                audit, package, row, attribute, value, raw_token, normalized)
        components.append(normalized)
    return components


def _settings_last_wins(
    root: ET.Element, audit: dict[str, Any]
) -> dict[str, tuple[int, dict[str, str]]]:
    final: dict[str, tuple[int, dict[str, str]]] = {}
    for row, element in enumerate(root, 1):
        if element.tag != "setting":
            audit["invalidSettingRows"].append({
                "row": row, "tag": element.tag,
                "value": dict(element.attrib), "reason": "unexpected element",
            })
            continue
        attributes = dict(element.attrib)
        package = attributes.get("name", "").strip()
        if not package:
            audit["invalidSettingRows"].append({
                "row": row, "tag": element.tag,
                "value": attributes, "reason": "missing package name",
            })
            continue
        previous = final.get(package)
        if previous is not None:
            audit["duplicateSettingRows"].append({
                "package": package,
                "replacedRow": previous[0],
                "winningRow": row,
                "replacedValue": previous[1],
                "winningValue": attributes,
            })
        final[package] = (row, attributes)
    return final


def _rule_histories(
    root: ET.Element, audit: dict[str, Any]
) -> dict[str, list[tuple[int, dict[str, str]]]]:
    histories: dict[str, list[tuple[int, dict[str, str]]]] = {}
    for row, element in enumerate(root, 1):
        if element.tag != "package":
            audit["invalidRuleRows"].append({
                "row": row, "tag": element.tag,
                "value": dict(element.attrib), "reason": "unexpected element",
            })
            continue
        attributes = dict(element.attrib)
        package = attributes.get("name", "").strip()
        if not package:
            audit["invalidRuleRows"].append({
                "row": row, "tag": element.tag,
                "value": attributes, "reason": "missing package name",
            })
            continue
        history = histories.setdefault(package, [])
        if history:
            previous = history[-1]
            audit["duplicateRuleRows"].append({
                "package": package,
                "replacedRow": previous[0],
                "winningRow": row,
                "replacedValue": previous[1],
                "winningValue": attributes,
            })
        history.append((row, attributes))
    return histories


def _merge_conversion_audit(
    destination: dict[str, Any], source: dict[str, Any]
) -> None:
    for key in (
        "malformedPairTokens",
        "recoveredPairTokens",
        "recoveredWhitespaceTokens",
        "droppedPrimaryWildcardRelations",
        "malformedPlaceholderTokens",
        "malformedComponentTokens",
        "normalizedComponents",
        "normalizedActivityRuleTokens",
        "mappedPlaceholderConditions",
        "ignoredRuleDefaults",
        "ruleDefaultFallbacks",
        "unmappedAttributes",
        "unmappedFlagDirectives",
    ):
        destination[key].extend(source[key])


def _setting_default(
    setting: tuple[int, dict[str, str]] | None,
    rule_attributes: dict[str, str],
) -> bool:
    if setting is None:
        rule_default = rule_attributes.get("defaultSettings")
        return True if rule_default is None else _strict_bool(
            rule_default, "defaultSettings")
    _, attributes = setting
    embedded = attributes.get("embeddedEnable")
    # Stock getDefaultSetting only falls back to rule.defaultSettings when no
    # setting row exists. Once a row exists, only literal true enables AE.
    return False if embedded is None else _strict_bool(
        embedded, "embeddedEnable")


def _convert_entry(
    package: str,
    row: int,
    attributes: dict[str, str],
    default_enabled: bool,
    has_setting: bool,
    audit: dict[str, Any],
) -> dict[str, Any]:
    scale_value = attributes.get("scaleMode")
    scale_mode = 0 if scale_value is None else _parse_non_negative_int(
        scale_value, "scaleMode")
    source_show_divider = (
        _optional_bool(attributes, "isShowDivider") is True)
    support_full_size = (
        _optional_bool(attributes, "supportFullSize") is True)
    output: dict[str, Any] = {
        "name": package,
        # HyperOS suppresses the divider while scale mode is active, even when
        # isShowDivider is true. Both source booleans default false.
        "showEmbeddingDivider": source_show_divider and scale_mode == 0,
        "dividerDraggingToFullscreenAllowed": support_full_size,
        # HyperOS app-side SplitRuleUtils applies these defaults when the
        # server Bundle omits a key. Materialize them so the product document
        # remains semantically stable across target-parser revisions.
        "clearTop": DEFAULT_CLEAR_TOP,
        "finishPrimaryWithSecondary": (
            DEFAULT_FINISH_PRIMARY_WITH_SECONDARY),
        "finishSecondaryWithPrimary": (
            DEFAULT_FINISH_SECONDARY_WITH_PRIMARY),
        "splitRatio": DEFAULT_SPLIT_RATIO,
        "minWidthDp": DEFAULT_MIN_WIDTH_DP,
        "minSmallestWidthDp": DEFAULT_MIN_SMALLEST_WIDTH_DP,
    }

    # ★ A ROW WITH NO `splitPairRule` GETS NO ROUTING RULE. (#87)
    #
    # This importer used to emit `autoPrimary: true` here -- "the first activity
    # the process creates is the primary, forever" -- described in the tree as
    # our substitute for HyperOS engine defaults. Unpacking a real HyperOS
    # firmware settled that there are no such defaults to substitute for: their
    # split predicate REQUIRES a splitPairRule, and 1,305 of the firmware's
    # 1,946 rows carry only a package name. A bare row says the package is on
    # the list. It says nothing whatsoever about how to split it.
    #
    # The invention cost #81, measured on hardware: cn.com.sina.finance is a
    # bare row whose splash was latched as the permanent primary, and with the
    # FINISH_ADJACENT every row materialises, finishing the splash finished the
    # secondary with it -- the app closed itself 1.07 s after launch. Shipping
    # those rows default-off treated the symptom.
    #
    # So a bare row now produces presentation attributes and nothing else,
    # exactly as upstream released it.
    pair_value = attributes.get("splitPairRule")
    if pair_value is not None:
        pairs = _parse_relations(
            pair_value, package=package, row=row, attribute="splitPairRule",
            audit_key="malformedPairTokens", audit=audit)
        if not pairs:
            raise EntryError(
                "splitPairRule", pair_value, "no valid activity pair")
        output["activityPairs"] = pairs

    placeholder = attributes.get("placeholder")
    if placeholder is not None:
        pairs = _parse_relations(
            placeholder, package=package, row=row, attribute="placeholder",
            audit_key="malformedPlaceholderTokens", audit=audit)
        if pairs:
            output["placeholderPairs"] = pairs

    activity_rule = attributes.get("activityRule")
    if activity_rule is not None:
        activities = _parse_component_list(
            activity_rule, package=package, row=row, attribute="activityRule",
            allow_pair_wildcard_suffix=True, audit=audit)
        if activities:
            output["forceFullscreenPages"] = activities

    transitions = attributes.get("transitionRules")
    if transitions is not None:
        activities = _parse_component_list(
            transitions, package=package, row=row,
            attribute="transitionRules", allow_pair_wildcard_suffix=False,
            audit=audit)
        if activities:
            output["transActivities"] = activities

    if not default_enabled:
        output["defaultEnabled"] = False

    ratio_value = attributes.get("splitRatio")
    if ratio_value is not None:
        output["splitRatio"] = _parse_float(ratio_value, "splitRatio")

    minimum_width = attributes.get("splitMinWidth")
    if minimum_width is not None:
        output["minWidthDp"] = _parse_non_negative_int(
            minimum_width, "splitMinWidth")

    clear_top = _optional_bool(attributes, "clearTop")
    if clear_top is not None:
        output["clearTop"] = clear_top

    for field in ("finishPrimaryWithSecondary", "finishSecondaryWithPrimary"):
        value = attributes.get(field)
        if value is not None:
            output[field] = _parse_finish_behavior(value, field)

    relaunch = _optional_bool(attributes, "relaunch")
    if relaunch is not None:
        output["suppressRelaunch"] = not relaunch

    flags = attributes.get("flags")
    if flags is not None:
        for raw_directive in flags.split(";"):
            directive = raw_directive.strip()
            if not directive:
                continue
            name, separator, payload = directive.partition(":")
            if name == "forceRelaunchActivity" and separator:
                activities = _parse_component_list(
                    payload, package=package, row=row, attribute="flags",
                    allow_pair_wildcard_suffix=False, audit=audit)
                if activities:
                    output["forceRelaunch"] = activities
                continue
            audit["unmappedFlagDirectives"].append({
                "package": package,
                "row": row,
                "attribute": "flags",
                "value": flags,
                "directive": directive,
                "name": name or None,
                "payload": payload if separator else None,
            })

    rule_default = attributes.get("defaultSettings")
    if rule_default is not None:
        item = {
            "package": package,
            "row": row,
            "attribute": "defaultSettings",
            "value": rule_default,
            "resolvedDefaultEnabled": default_enabled,
        }
        if has_setting:
            audit["ignoredRuleDefaults"].append({
                **item,
                "reason": (
                    "rendered setting row embeddedEnable is authoritative"),
            })
        else:
            audit["ruleDefaultFallbacks"].append({
                **item,
                "reason": "no rendered setting row; rule default is fallback",
            })

    for attribute in sorted(attributes):
        if attribute not in CLASSIFIED_RULE_ATTRIBUTES:
            item = {
                "package": package,
                "row": row,
                "attribute": attribute,
                "value": attributes[attribute],
            }
            if attribute == "scaleMode":
                item["mappedSideEffect"] = (
                    "mapped into showEmbeddingDivider; remaining scale "
                    "semantics unsupported")
            audit["unmappedAttributes"].append(item)

    return output


def _finalize_counts(
    audit: dict[str, Any],
    *,
    rule_root: ET.Element,
    setting_root: ET.Element,
    final_rules: dict[str, tuple[int, dict[str, str]]],
    final_settings: dict[str, tuple[int, dict[str, str]]],
    packages: list[dict[str, Any]],
) -> None:
    full_rule_names = {
        name for name, (_, attributes) in final_rules.items()
        if attributes.get("fullRule", "").strip()
    }
    explicit = [rule for rule in packages if "activityPairs" in rule]
    placeholder = [rule for rule in packages if "placeholderPairs" in rule]
    counts = {
        "rule_rows": len(rule_root),
        "rule_unique_packages": len(final_rules),
        "rule_duplicate_rows": len(audit["duplicateRuleRows"]),
        "setting_rows": len(setting_root),
        "setting_unique_packages": len(final_settings),
        "setting_duplicate_rows": len(audit["duplicateSettingRows"]),
        "source_final_full_rule": len(full_rule_names),
        "imported": len(packages),
        "imported_explicit_pair_packages": len(explicit),
        "imported_pair_relationships": sum(
            len(rule["activityPairs"]) for rule in explicit),
        "imported_placeholder_packages": len(placeholder),
        "imported_placeholder_relationships": sum(
            len(rule["placeholderPairs"]) for rule in placeholder),
        "excluded_full_rule": len(audit["excludedFullRulePackages"]),
        "malformed_rule_rows": len(audit["malformedRuleRows"]),
        "dropped_malformed_packages": len(audit["droppedMalformedPackages"]),
        "malformed_pair_tokens": len(audit["malformedPairTokens"]),
        "recovered_pair_tokens": len(audit["recoveredPairTokens"]),
        "recovered_pair_relationships": sum(
            len(item["recovered"]) for item in audit["recoveredPairTokens"]),
        "recovered_whitespace_tokens": len(
            audit["recoveredWhitespaceTokens"]),
        "dropped_primary_wildcard_relations": len(
            audit["droppedPrimaryWildcardRelations"]),
        "malformed_placeholder_tokens": len(
            audit["malformedPlaceholderTokens"]),
        "malformed_component_tokens": len(audit["malformedComponentTokens"]),
        "normalized_component_tokens": len(audit["normalizedComponents"]),
        "normalized_activity_rule_tokens": len(
            audit["normalizedActivityRuleTokens"]),
        "mapped_placeholder_wait_for_content": len(
            audit["mappedPlaceholderConditions"]),
        "ignored_rule_defaults": len(audit["ignoredRuleDefaults"]),
        "rule_default_fallbacks": len(audit["ruleDefaultFallbacks"]),
        "unmapped_attribute_values": len(audit["unmappedAttributes"]),
        "unmapped_setting_attribute_values": len(
            audit["unmappedSettingAttributes"]),
        "orphan_setting_rows": len(audit["orphanSettingRows"]),
        "unmapped_flag_directives": len(audit["unmappedFlagDirectives"]),
        "default_enabled": sum(
            rule.get("defaultEnabled") is not False for rule in packages),
        "default_disabled": sum(
            rule.get("defaultEnabled") is False for rule in packages),
        "split_ratio_0_3": sum(
            rule["splitRatio"] == 0.3 for rule in packages),
        "split_ratio_0_4": sum(
            rule["splitRatio"] == 0.4 for rule in packages),
        "split_ratio_0_42": sum(
            rule["splitRatio"] == 0.42 for rule in packages),
        "split_ratio_0_5": sum(
            rule["splitRatio"] == 0.5 for rule in packages),
        "split_ratio_0_7": sum(
            rule["splitRatio"] == 0.7 for rule in packages),
        "min_width_dp_600": sum(
            rule["minWidthDp"] == 600 for rule in packages),
        "min_width_dp_900": sum(
            rule["minWidthDp"] == 900 for rule in packages),
        "min_smallest_width_dp_600": sum(
            rule["minSmallestWidthDp"] == 600 for rule in packages),
        "clear_top_true": sum(
            rule["clearTop"] is True for rule in packages),
        "clear_top_false": sum(
            rule["clearTop"] is False for rule in packages),
        "finish_primary_with_secondary_0": sum(
            rule["finishPrimaryWithSecondary"] == 0 for rule in packages),
        "finish_primary_with_secondary_1": sum(
            rule["finishPrimaryWithSecondary"] == 1 for rule in packages),
        "finish_primary_with_secondary_2": sum(
            rule["finishPrimaryWithSecondary"] == 2 for rule in packages),
        "finish_secondary_with_primary_0": sum(
            rule["finishSecondaryWithPrimary"] == 0 for rule in packages),
        "finish_secondary_with_primary_1": sum(
            rule["finishSecondaryWithPrimary"] == 1 for rule in packages),
        "finish_secondary_with_primary_2": sum(
            rule["finishSecondaryWithPrimary"] == 2 for rule in packages),
        "show_divider_enabled": sum(
            rule["showEmbeddingDivider"] is True for rule in packages),
        "show_divider_disabled": sum(
            rule["showEmbeddingDivider"] is False for rule in packages),
        "divider_dragging_to_fullscreen_allowed": sum(
            rule["dividerDraggingToFullscreenAllowed"] is True
            for rule in packages),
        "divider_dragging_to_fullscreen_disallowed": sum(
            rule["dividerDraggingToFullscreenAllowed"] is False
            for rule in packages),
        "shown_divider_dragging_to_fullscreen_allowed": sum(
            rule["showEmbeddingDivider"] is True
            and rule["dividerDraggingToFullscreenAllowed"] is True
            for rule in packages),
        "shown_divider_dragging_to_fullscreen_disallowed": sum(
            rule["showEmbeddingDivider"] is True
            and rule["dividerDraggingToFullscreenAllowed"] is False
            for rule in packages),
        "output_packages": len(packages),
    }
    audit["counts"] = counts


def generate(
    source_path: Path,
    settings_path: Path,
    lock_path: Path,
) -> tuple[dict[str, Any], dict[str, Any]]:
    """Return a deterministic product document and exhaustive conversion audit."""
    lock = _read_json(lock_path)
    source_data = _verify_hash(source_path, lock, "rendered_xml")
    settings_data = _verify_hash(
        settings_path, lock, "rendered_settings_xml")
    source_root = _parse_xml(
        source_data, "package_config", str(source_path))
    settings_root = _parse_xml(
        settings_data, "setting_rule", str(settings_path))

    audit = _new_audit()
    rule_histories = _rule_histories(source_root, audit)
    final_rules = {
        package: history[-1]
        for package, history in rule_histories.items()
    }
    final_settings = _settings_last_wins(settings_root, audit)
    packages: list[dict[str, Any]] = []

    for package in sorted(final_settings):
        setting_row, setting_value = final_settings[package]
        if package not in final_rules:
            audit["orphanSettingRows"].append({
                "package": package,
                "row": setting_row,
                "value": setting_value,
                "reason": "no package in the locked embedding rules",
            })
        for attribute in sorted(setting_value):
            if attribute not in {"name", "embeddedEnable"}:
                audit["unmappedSettingAttributes"].append({
                    "package": package,
                    "row": setting_row,
                    "attribute": attribute,
                    "value": setting_value[attribute],
                })

    for package in sorted(rule_histories):
        row, attributes = rule_histories[package][-1]
        full_rule = attributes.get("fullRule", "").strip()
        if full_rule:
            audit["excludedFullRulePackages"].append({
                "package": package, "row": row, "value": full_rule,
            })
            continue

        setting = final_settings.get(package)
        try:
            default_enabled = _setting_default(setting, attributes)
        except EntryError as error:
            setting_row, setting_value = setting or (None, {})
            audit["droppedMalformedPackages"].append({
                "package": package,
                "row": setting_row,
                "attribute": error.attribute,
                "value": error.value,
                "reason": error.reason,
                "source": "settings",
                "setting": setting_value,
            })
            continue

        conversion_audit = _new_audit()
        try:
            converted = _convert_entry(
                package, row, attributes, default_enabled,
                setting is not None,
                conversion_audit)
        except EntryError as error:
            _merge_conversion_audit(audit, conversion_audit)
            malformed = {
                "package": package,
                "row": row,
                "attribute": error.attribute,
                "value": error.value,
                "reason": error.reason,
            }
            audit["malformedRuleRows"].append({
                **malformed, "outcome": "package dropped",
            })
            audit["droppedMalformedPackages"].append(malformed)
            continue

        _merge_conversion_audit(audit, conversion_audit)
        packages.append(converted)

    _finalize_counts(
        audit,
        rule_root=source_root,
        setting_root=settings_root,
        final_rules=final_rules,
        final_settings=final_settings,
        packages=packages,
    )

    upstream = lock.get("upstream", {})
    document = {
        "_generated": {
            "tool": "parallelwindow/tools/import_hyperos_rules.py",
            "upstreamRepository": upstream.get("repository"),
            "upstreamTag": upstream.get("tag"),
            "upstreamCommit": upstream.get("commit"),
            "renderedRulesSha256": lock["rendered_xml"]["sha256"],
            "renderedSettingsSha256": lock["rendered_settings_xml"]["sha256"],
            "policy": "official release last row wins; fullRule mode excluded",
            "authorization": "See parallelwindow/AUTHORIZATION.md",
        },
        "EmbeddingConfigVersion": "1.0.0",
        "blocklist": [],
        "packages": packages,
    }
    return document, audit


def _serialized(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2) + "\n"


def _write_or_check(path: Path, content: str, check: bool) -> None:
    if check:
        if not path.is_file() or path.read_text(encoding="utf-8") != content:
            raise SystemExit(f"generated file is stale: {path}")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def main() -> None:
    parallel_dir = Path(__file__).resolve().parent.parent
    upstream_dir = parallel_dir / "upstream" / "3.01.48"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source", type=Path,
        default=upstream_dir / "embedded_rules_list.xml")
    parser.add_argument(
        "--settings", type=Path,
        default=upstream_dir / "embedded_setting_config.xml")
    parser.add_argument(
        "--lock", type=Path,
        default=parallel_dir / "hyperos_source_lock.json")
    parser.add_argument(
        "--output", type=Path,
        default=parallel_dir / "embedding_config.json")
    parser.add_argument(
        "--audit-output", type=Path,
        default=parallel_dir / "hyperos_import_audit.json")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    document, audit = generate(args.source, args.settings, args.lock)
    _write_or_check(args.output, _serialized(document), args.check)
    _write_or_check(args.audit_output, _serialized(audit), args.check)


if __name__ == "__main__":
    main()
