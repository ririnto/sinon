#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Require UTF-8 encoding marker on shebang scripts rule.
"""

import sys

from collections.abc import Iterable

from rules.harness_check_rule import Finding, FindingEdit, FindingFix, FixSafety, HarnessCheckRule
from core.rule_context import RuleContext, relative

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class ShebangEncodingMarkerRule(HarnessCheckRule):
    """Validate shebangEncodingMarker check."""

    category = "shebangEncodingMarker"

    def applies(self, ctx: RuleContext) -> bool:
        """
        Check if this rule applies to the context.

        :param ctx: rule execution context.
        :returns: ``True`` when the rule should run.
        """
        section = ctx.manifest.raw.get(self.category)
        if not isinstance(section, dict):
            return False
        if section.get("enabled", True) is False:
            return False
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return False
        roots = params.get("sourceRoots", [])
        return isinstance(roots, list) and len(roots) > 0

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """
        Validate shebangEncodingMarker check.

        :param ctx: rule execution context.
        :returns: iterable of findings; empty when no issues are found.
        """
        section = ctx.manifest.raw.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        markers = params.get("markers", {})
        if not isinstance(markers, dict):
            return []
        require_shebang = params.get("requireShebang", True)
        extra_prefixes = params.get("additionalShebangPrefixes", [])
        if not isinstance(extra_prefixes, list):
            extra_prefixes = []
        shebang_prefixes = tuple(["#!"] + [p for p in extra_prefixes if isinstance(p, str)])
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            messages = {}
        default_template = messages.get(
            "default",
            "{file}:2: shebang script missing UTF-8 encoding marker; expected `{expected}` on line 2",
        )
        missing_line_template = messages.get(
            "missingLine",
            "{file}: shebang script has no line 2; expected `{expected}`",
        )
        wrong_marker_template = messages.get(
            "wrongMarker",
            "{file}:2: shebang script declares wrong encoding marker; found `{actual}`, expected `{expected}`",
        )
        severity = ctx.severity_of(self.category)
        result: list[Finding] = []
        for path in ctx.stack_sources(self.category):
            extension = path.suffix.lstrip(".")
            expected = markers.get(extension)
            if not isinstance(expected, str) or not expected:
                continue
            text = ctx.read(path.relative_to(ctx.root).as_posix())
            if not text:
                continue
            lines = text.splitlines()
            first = lines[0] if lines else ""
            has_shebang = any(first.startswith(prefix) for prefix in shebang_prefixes)
            if not has_shebang and require_shebang:
                continue
            relative_path = relative(path, ctx.root)
            if len(lines) < 2:
                result.append(
                    Finding(
                        severity,
                        self.category,
                        missing_line_template.format(file=relative_path, expected=expected),
                        file=relative_path,
                        start_line=2,
                        start_column=1,
                        end_line=2,
                        end_column=1,
                        fix=FindingFix(
                            description=f"insert `{expected}` as line 2",
                            safety=FixSafety.SAFE,
                            edits=(
                                FindingEdit(
                                    file=relative_path,
                                    start_line=2,
                                    start_column=1,
                                    end_line=2,
                                    end_column=1,
                                    replacement=f"{expected}\n",
                                ),
                            ),
                        ),
                    )
                )
                continue
            actual = lines[1]
            if actual == expected:
                continue
            result.append(
                Finding(
                    severity,
                    self.category,
                    (
                        wrong_marker_template.format(
                            file=relative_path, actual=actual, expected=expected
                        )
                        if actual.strip()
                        else default_template.format(file=relative_path, expected=expected)
                    ),
                    file=relative_path,
                    start_line=2,
                    start_column=1,
                    end_line=2,
                    end_column=max(len(actual), 1) + 1,
                    fix=FindingFix(
                        description=f"replace line 2 with `{expected}`",
                        safety=FixSafety.SAFE,
                        edits=(
                            FindingEdit(
                                file=relative_path,
                                start_line=2,
                                start_column=1,
                                end_line=2,
                                end_column=max(len(actual), 1) + 1,
                                replacement=expected,
                            ),
                        ),
                    ),
                )
            )
        return result


RULE: HarnessCheckRule = ShebangEncodingMarkerRule()
