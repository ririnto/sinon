#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Forbid unsafe symlinks rule.
"""

import sys


from collections.abc import Iterable
import os

from rules.harness_check_rule import (
    Finding,
    FindingFix,
    FixSafety,
    HarnessCheckRule,
)
from core.rule_context import RuleContext, relative

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class SymlinkSafetyRule(HarnessCheckRule):
    """Validate symlinkSafety check."""

    category = "symlinkSafety"

    def applies(self, ctx: RuleContext) -> bool:
        """Check if this rule applies to the context."""
        section = ctx.manifest.raw.get(self.category)
        if not isinstance(section, dict):
            return False
        return section.get("enabled", True) is not False

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """Validate symlinkSafety check."""
        section = ctx.manifest.raw.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        allowed_pairs = params.get("allowedSymlinkPairs", [])
        if not isinstance(allowed_pairs, list):
            return []
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        allowed_set = frozenset(
            tuple(sorted((p[0], p[1])))
            for p in allowed_pairs
            if isinstance(p, list) and len(p) == 2
        )
        scan_bases = (
            ".claude",
            "docs",
            ".github",
            "AGENTS.md",
            "CLAUDE.md",
            "ARCHITECTURE.md",
        )
        root_findings = []
        for base in scan_bases:
            if ctx.is_symlink(base):
                base_path = ctx.root / base
                target_name = os.readlink(str(base_path)).split("/")[-1]
                if tuple(sorted((base, target_name))) not in allowed_set:
                    root_findings.append(
                        Finding(
                            ctx.severity_of(self.category),
                            self.category,
                            messages.get(
                                "scanRootNotAllowed",
                                "symlink scan root is not allowed: {path}",
                            ).format(path=relative(base_path, ctx.root)),
                            file=relative(base_path, ctx.root),
                            start_line=1,
                            start_column=1,
                            end_line=1,
                            end_column=1,
                            fix=FindingFix(
                                description=f"resolve symlink {relative(base_path, ctx.root)}",
                                safety=FixSafety.MANUAL,
                            ),
                        )
                    )
        file_findings = []
        for base in scan_bases:
            if ctx.is_symlink(base) or not ctx.is_directory(base):
                continue
            files, _ = ctx.walk_directory(base)
            for path in files:
                path_str = path.relative_to(ctx.root).as_posix()
                if not ctx.is_symlink(path_str):
                    continue
                if path.parent == ctx.root and path.name in {"AGENTS.md", "CLAUDE.md"}:
                    target_name = os.readlink(str(path))
                    pair = tuple(sorted((path.name, target_name)))
                    if pair not in allowed_set:
                        file_findings.append(
                            Finding(
                                ctx.severity_of(self.category),
                                self.category,
                                messages.get(
                                    "fileNotAllowed",
                                    "symlink file is not allowed: {path}",
                                ).format(path=relative(path, ctx.root)),
                                file=relative(path, ctx.root),
                                start_line=1,
                                start_column=1,
                                end_line=1,
                                end_column=1,
                                fix=FindingFix(
                                    description=f"resolve symlink {relative(path, ctx.root)}",
                                    safety=FixSafety.MANUAL,
                                ),
                            )
                        )
                else:
                    file_findings.append(
                        Finding(
                            ctx.severity_of(self.category),
                            self.category,
                            messages.get(
                                "pathNotAllowed",
                                "symlink path is not allowed: {path}",
                            ).format(path=relative(path, ctx.root)),
                            file=relative(path, ctx.root),
                            start_line=1,
                            start_column=1,
                            end_line=1,
                            end_column=1,
                            fix=FindingFix(
                                description=f"resolve symlink {relative(path, ctx.root)}",
                                safety=FixSafety.MANUAL,
                            ),
                        )
                    )
        return root_findings + file_findings


RULE: HarnessCheckRule = SymlinkSafetyRule()
