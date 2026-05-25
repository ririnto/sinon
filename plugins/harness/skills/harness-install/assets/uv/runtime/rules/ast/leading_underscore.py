#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Forbid leading underscores in Python file basenames and declarations."""

from __future__ import annotations

import re
import sys

from collections.abc import Iterable
from pathlib import Path

import libcst as cst

from core.rule_context import RuleContext, relative, stack_sources_configured
from rules.harness_check_rule import Finding, FindingFix, FixSafety, HarnessCheckRule, parse_python

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class RuleConfig:
    """Configuration for allowed leading-underscore exemptions."""

    def __init__(self, allowed_names: frozenset[str], allowed_patterns: tuple[re.Pattern[str], ...], python_dunder_exemptions: bool) -> None:
        """Initialize leading-underscore matching configuration."""
        self.allowed_names = allowed_names
        self.allowed_patterns = allowed_patterns
        self.python_dunder_exemptions = python_dunder_exemptions

    def is_forbidden(self, name: str) -> bool:
        """Return whether a name violates the leading-underscore policy."""
        if not name.startswith("_"):
            return False
        if name in self.allowed_names:
            return False
        if self.python_dunder_exemptions and name.startswith("__") and name.endswith("__"):
            return False
        return not any(pattern.fullmatch(name) for pattern in self.allowed_patterns)


def read_rule_config(ctx: RuleContext) -> RuleConfig:
    """Read leading-underscore parameters from the manifest."""
    section = ctx.manifest.raw.get("leadingUnderscore", {})
    params = section.get("parameters", {}) if isinstance(section, dict) else {}
    allowed_names = params.get("allowedNames", []) if isinstance(params, dict) else []
    allowed_patterns = params.get("allowedPatterns", []) if isinstance(params, dict) else []
    python_dunder_exemptions = params.get("pythonDunderExemptions", True) if isinstance(params, dict) else True
    return RuleConfig(
        frozenset({"_", *(item for item in allowed_names if isinstance(item, str))}),
        tuple(re.compile(item) for item in allowed_patterns if isinstance(item, str)),
        bool(python_dunder_exemptions),
    )


class LeadingUnderscoreRule(HarnessCheckRule):
    """Validate leadingUnderscore check."""

    category = "leadingUnderscore"

    def applies(self, ctx: RuleContext) -> bool:
        """Check if this rule applies to the context."""
        if not ctx.is_enabled(self.category):
            return False
        return stack_sources_configured(ctx.manifest.raw, self.category)

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """Validate leadingUnderscore check."""
        severity = ctx.severity_of(self.category)
        rule_config = read_rule_config(ctx)
        return [
            finding
            for path in ctx.stack_sources(self.category)
            for finding in self.validate_file(path, ctx, severity, rule_config)
        ]

    def validate_file(self, path: Path, ctx: RuleContext, severity: str, rule_config: RuleConfig) -> tuple[Finding, ...]:
        """Validate one Python file."""
        basename = path.stem
        basename_findings = (self.finding(ctx, path, basename, 1, severity),) if rule_config.is_forbidden(basename) else ()
        tree, error = parse_python(path)
        if error is not None or tree is None:
            return basename_findings + (Finding(severity, self.category, f"{relative(path, ctx.root)}: syntax error: {error}"),)
        wrapper = cst.MetadataWrapper(tree)
        visitor = DeclarationVisitor(ctx, path, severity, rule_config)
        wrapper.visit(visitor)
        return basename_findings + tuple(visitor.findings)

    def finding(self, ctx: RuleContext, path: Path, name: str, line: int, severity: str) -> Finding:
        """Create a leading-underscore finding."""
        rel_path = relative(path, ctx.root)
        return Finding(
            severity,
            self.category,
            f"{rel_path}:{line}: declaration `{name}` uses a leading underscore",
            file=rel_path,
            start_line=line,
            start_column=1,
            end_line=line,
            end_column=max(1, len(name) + 1),
            fix=FindingFix(
                description=f"rename `{name}` without a leading underscore",
                safety=FixSafety.MANUAL,
                edits=(),
            ),
        )


class DeclarationVisitor(cst.CSTVisitor):
    """Collect leading-underscore declaration findings."""

    METADATA_DEPENDENCIES = (cst.metadata.PositionProvider,)

    def __init__(self, ctx: RuleContext, path: Path, severity: str, rule_config: RuleConfig) -> None:
        """Initialize visitor state."""
        super().__init__()
        self.ctx = ctx
        self.path = path
        self.severity = severity
        self.rule_config = rule_config
        self.findings: list[Finding] = []

    def visit_FunctionDef(self, node: cst.FunctionDef) -> bool:
        """Visit a function declaration."""
        self.record(node.name.value, node)
        return True

    def visit_ClassDef(self, node: cst.ClassDef) -> bool:
        """Visit a class declaration."""
        self.record(node.name.value, node)
        return True

    def record(self, name: str, node: cst.CSTNode) -> None:
        """Record a finding when the supplied name is forbidden."""
        if self.rule_config.is_forbidden(name):
            pos = self.get_metadata(cst.metadata.PositionProvider, node)
            self.findings.append(LeadingUnderscoreRule().finding(self.ctx, self.path, name, pos.start.line, self.severity))


RULE: HarnessCheckRule = LeadingUnderscoreRule()
