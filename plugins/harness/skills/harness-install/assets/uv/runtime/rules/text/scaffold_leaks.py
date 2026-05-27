#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Forbid scaffold leaks rule.
"""

import sys

from collections.abc import Iterable
import re
from pathlib import Path

from rules.harness_check_rule import Finding, FindingFix, FixSafety, HarnessCheckRule
from core.rule_context import RuleContext, relative

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


def strip_markdown_code(text: str) -> str:
    """
    Remove Markdown code blocks and inline code spans before prose-level checks.
    """
    stripped_lines: list[str] = []
    in_fence = False
    fence_marker = ""
    for line in text.splitlines():
        fence_match = re.match(r" {0,3}(`{3,}|~{3,})", line)
        if fence_match:
            marker = fence_match.group(1)[0]
            if not in_fence:
                in_fence = True
                fence_marker = marker
            elif marker == fence_marker:
                in_fence = False
            stripped_lines.append("")
            continue
        if in_fence:
            stripped_lines.append("")
            continue
        stripped_lines.append(re.sub(r"`+[^`\n]*`+", "", line))
    return "\n".join(stripped_lines)


def is_relative_to(path: Path, root: Path) -> bool:
    """Return whether path stays within root."""
    return path.is_relative_to(root)


class ScaffoldLeaksRule(HarnessCheckRule):
    """Validate scaffoldLeaks check."""

    category = "scaffoldLeaks"

    def applies(self, ctx: RuleContext) -> bool:
        """
        Check if this rule applies to the context.

        :param ctx: rule execution context.
        :returns: ``True`` when the rule should run.
        """
        section = ctx.manifest.raw.get(self.category)
        if not isinstance(section, dict):
            return False
        return section.get("enabled", True) is not False

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """
        Validate scaffoldLeaks check.

        :param ctx: rule execution context.
        :returns: iterable of findings; empty when no issues are found.
        """
        section = ctx.manifest.raw.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        scope = params.get("scope", {})
        if not isinstance(scope, dict):
            return []
        bases_data = scope.get("bases", [])
        excluded_data = scope.get("excludedSubtrees", [])
        exts_data = scope.get("extensions", [])
        if not isinstance(bases_data, list):
            return []
        resolved_root = ctx.root.resolve()
        active_roots = tuple(
            candidate
            for b in bases_data
            if isinstance(b, str)
            for candidate in (ctx.root / b,)
            if is_relative_to(candidate.resolve(), resolved_root)
        )
        excluded_paths = tuple(
            candidate
            for e in (excluded_data if isinstance(excluded_data, list) else ())
            if isinstance(e, str)
            for candidate in (ctx.root / e,)
            if is_relative_to(candidate.resolve(), resolved_root)
        )
        extensions = frozenset(
            f".{ext}"
            for ext in (exts_data if isinstance(exts_data, list) else [])
            if isinstance(ext, str)
        ) or frozenset({".md", ".txt"})
        patterns_data = params.get("patterns", [])
        if not isinstance(patterns_data, list):
            return []
        compiled_patterns = tuple(
            (re.compile(item.get("pattern")), item.get("label"))
            for item in patterns_data
            if isinstance(item, dict)
            and isinstance(item.get("pattern"), str)
            and isinstance(item.get("label"), str)
            and (lambda p=item.get("pattern"): True if re.compile(p) else False)()
        )
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        return [
            Finding(
                ctx.severity_of(self.category),
                self.category,
                messages.get("default", "{label} in active asset: {file}").format(
                    label=label, file=relative(sub_path, ctx.root)
                ),
                file=relative(sub_path, ctx.root),
                start_line=1,
                start_column=1,
                end_line=1,
                end_column=1,
                fix=FindingFix(
                    description=f"replace placeholder ({label})",
                    safety=FixSafety.MANUAL,
                ),
            )
            for base in active_roots
            for paths, _ in [
                ctx.collect_files_under(base.relative_to(ctx.root).as_posix())
            ]
            for sub_path in paths
            if sub_path.is_file()
            and is_relative_to(sub_path.resolve(), resolved_root)
            and sub_path.suffix in extensions
            and not any(
                sub_path == ex or ex in sub_path.parents for ex in excluded_paths
            )
            for text in (
                strip_markdown_code(
                    ctx.read(sub_path.relative_to(ctx.root).as_posix())
                ),
            )
            for pattern, label in compiled_patterns
            if pattern.search(text)
        ]


RULE: HarnessCheckRule = ScaffoldLeaksRule()
