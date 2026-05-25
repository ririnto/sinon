#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Forbid mutable collection construction and accumulation in Python sources.
"""

from __future__ import annotations

import sys

from collections.abc import Iterable

import libcst as cst

from core.rule_context import RuleContext, relative, stack_sources_configured
from rules.harness_check_rule import (
    Finding,
    FindingFix,
    FixSafety,
    HarnessCheckRule,
    parse_python,
)

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class MutableCollectionRule(HarnessCheckRule):
    """Validate mutableCollection check."""

    category = "mutableCollection"

    def applies(self, ctx: RuleContext) -> bool:
        """Check if this rule applies to the context."""
        if not ctx.is_enabled(self.category):
            return False
        return stack_sources_configured(ctx.manifest.raw, self.category)

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """Validate mutableCollection check."""
        severity = ctx.severity_of(self.category)
        parameters = ctx.read_json_object(ctx.category_object(self.category).get("parameters"))
        config = MutableConfig(
            configured(ctx, parameters.get("forbiddenConstructors"), ("list", "dict", "set")),
            configured(ctx, parameters.get("forbiddenTypes"), ()),
            configured(ctx, parameters.get("forbiddenFqns"), ("builtins.list", "builtins.dict", "builtins.set")),
            configured(ctx, parameters.get("accumulationMethods"), ("append", "add", "extend", "insert", "update", "setdefault")),
            configured(ctx, parameters.get("allowedBuilders"), ()),
            configured(ctx, parameters.get("allowedComprehensionsOrGenerators"), ("list", "dict", "set", "generator")),
        )

        def collect_findings() -> Iterable[Finding]:
            """Collect findings from configured Python source files."""
            for path in ctx.stack_sources(self.category):
                tree, error = parse_python(path)
                rel_path = relative(path, ctx.root)
                if error is not None:
                    yield Finding(severity, self.category, f"{rel_path}: syntax error: {error}")
                    continue
                wrapper = cst.MetadataWrapper(tree)
                visitor = MutableCollectionFinder(rel_path, severity, self.category, config)
                wrapper.visit(visitor)
                yield from visitor.findings
        return tuple(collect_findings())


class MutableConfig:
    """Resolved mutable collection rule configuration."""

    def __init__(
        self,
        forbidden_constructors: tuple[str, ...],
        forbidden_types: tuple[str, ...],
        forbidden_fqns: tuple[str, ...],
        accumulation_methods: tuple[str, ...],
        allowed_builders: tuple[str, ...],
        allowed_comprehensions_or_generators: tuple[str, ...],
    ) -> None:
        """Initialize resolved configuration."""
        self.forbidden_constructors = forbidden_constructors
        self.forbidden_types = forbidden_types
        self.forbidden_fqns = forbidden_fqns
        self.accumulation_methods = accumulation_methods
        self.allowed_builders = allowed_builders
        self.allowed_comprehensions_or_generators = allowed_comprehensions_or_generators


class MutableCollectionFinder(cst.CSTVisitor):
    """Find mutable collection constructor and accumulation calls."""

    METADATA_DEPENDENCIES = (cst.metadata.PositionProvider,)

    def __init__(
        self,
        rel_path: str,
        severity: str,
        category: str,
        config: MutableConfig,
    ) -> None:
        """Initialize finder state."""
        super().__init__()
        self.findings: tuple[Finding, ...] = ()
        self.rel_path = rel_path
        self.severity = severity
        self.category = category
        self.config = config

    def visit_Call(self, node: cst.Call) -> bool:
        """Check constructor and accumulation calls."""
        call_name = dotted_name(node.func)
        attr_name = node.func.attr.value if isinstance(node.func, cst.Attribute) else call_name
        if call_name in self.config.forbidden_constructors or call_name in self.config.forbidden_fqns:
            self.record(node, call_name)
        elif attr_name in self.config.accumulation_methods:
            receiver = dotted_name(node.func.value) if isinstance(node.func, cst.Attribute) else ""
            if receiver not in self.config.allowed_builders:
                self.record(node, attr_name)
        return True

    def visit_List(self, node: cst.List) -> bool:
        """Check list literal construction when configured."""
        if "list" in self.config.forbidden_types:
            self.record(node, "list literal")
        return True

    def visit_Dict(self, node: cst.Dict) -> bool:
        """Check dict literal construction when configured."""
        if "dict" in self.config.forbidden_types:
            self.record(node, "dict literal")
        return True

    def visit_Set(self, node: cst.Set) -> bool:
        """Check set literal construction when configured."""
        if "set" in self.config.forbidden_types:
            self.record(node, "set literal")
        return True

    def record(self, node: cst.CSTNode, name: str) -> None:
        """Append a finding for a mutable pattern."""
        pos = self.get_metadata(cst.metadata.PositionProvider, node)
        self.findings = (
            *self.findings,
            Finding(
                self.severity,
                self.category,
                f"{self.rel_path}:{pos.start.line}: mutable collection `{name}` is forbidden; use comprehension, tuple, or generator form",
                file=self.rel_path,
                start_line=pos.start.line,
                start_column=pos.start.column + 1,
                end_line=pos.end.line,
                end_column=pos.end.column + 1,
                fix=FindingFix(
                    description=f"replace `{name}` with functional construction",
                    safety=FixSafety.UNSAFE,
                    edits=(),
                ),
            ),
        )


def configured(ctx: RuleContext, value: object, defaults: tuple[str, ...]) -> tuple[str, ...]:
    """Read a configured string tuple, falling back to defaults."""
    values = tuple(ctx.read_string_array(value))
    return values if values else defaults


def dotted_name(node: cst.BaseExpression) -> str:
    """Return dotted name text for a Name or Attribute expression."""
    if isinstance(node, cst.Name):
        return node.value
    if isinstance(node, cst.Attribute):
        left = dotted_name(node.value)
        return f"{left}.{node.attr.value}" if left else node.attr.value
    return ""


RULE: HarnessCheckRule = MutableCollectionRule()
