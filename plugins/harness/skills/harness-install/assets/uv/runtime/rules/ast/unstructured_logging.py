#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Forbid unstructured logging rule.
"""

import sys


from collections.abc import Iterable

import libcst as cst

from rules.harness_check_rule import (
    Finding,
    FindingFix,
    FixSafety,
    HarnessCheckRule,
    parse_python,
)
from core.rule_context import RuleContext, relative, stack_sources_configured

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class UnstructuredLoggingRule(HarnessCheckRule):
    """Validate unstructuredLogging check."""

    category = "unstructuredLogging"

    def applies(self, ctx: RuleContext) -> bool:
        """Check if this rule applies to the context."""
        if not ctx.is_enabled(self.category):
            return False
        return stack_sources_configured(ctx.manifest.raw, self.category)

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """Validate unstructuredLogging check."""
        severity = ctx.severity_of(self.category)
        category = self.category
        sources = ctx.stack_sources(self.category)
        forbidden = self.resolve_api_list(ctx, "forbiddenLoggingApis", {"print"})
        allowed = self.resolve_api_list(ctx, "allowedLoggingApis", set())
        functional_output_paths = self.resolve_api_list(ctx, "functionalOutputPaths", set())

        class PrintFinder(cst.CSTVisitor):
            METADATA_DEPENDENCIES = (cst.metadata.PositionProvider,)

            def __init__(self, rel_path: str) -> None:
                super().__init__()
                self.findings: list[Finding] = []
                self.rel_path = rel_path

            def visit_Call(self, node: cst.Call) -> bool:
                api_name = self.api_name(node.func)
                if api_name in allowed or api_name not in forbidden:
                    return True
                if self.rel_path in functional_output_paths:
                    return True
                pos = self.get_metadata(cst.metadata.PositionProvider, node)
                self.findings.append(
                    Finding(
                        severity,
                        category,
                        f"{self.rel_path}:{pos.start.line}: unstructured logging `{api_name}`; use structured logger",
                        file=self.rel_path,
                        start_line=pos.start.line,
                        start_column=pos.start.column + 1,
                        end_line=pos.end.line,
                        end_column=pos.end.column + 1,
                        fix=FindingFix(
                            description="replace with structured logger call; requires logger import and API choice",
                            safety=FixSafety.UNSAFE,
                            edits=(),
                        ),
                    )
                )
                return True

            @staticmethod
            def api_name(func: cst.BaseExpression) -> str:
                """Return a dotted API name for calls this rule can classify."""
                if isinstance(func, cst.Name):
                    return func.value
                if isinstance(func, cst.Attribute):
                    parts = []
                    current: cst.BaseExpression | cst.Attribute = func
                    while isinstance(current, cst.Attribute):
                        parts.append(current.attr.value)
                        current = current.value
                    if isinstance(current, cst.Name):
                        parts.append(current.value)
                    return ".".join(reversed(parts))
                return ""

        def collect_findings():
            for path in sources:
                tree, error = parse_python(path)
                if error is not None:
                    yield Finding(
                        severity,
                        category,
                        f"{relative(path, ctx.root)}: syntax error: {error}",
                    )
                    continue
                wrapper = cst.MetadataWrapper(tree)
                visitor = PrintFinder(relative(path, ctx.root))
                wrapper.visit(visitor)
                yield from visitor.findings
        return list(collect_findings())

    def resolve_api_list(
        self, ctx: RuleContext, key: str, default: set[str]
    ) -> set[str]:
        """Resolve a string set from unstructuredLogging parameters."""
        section = ctx.manifest.raw.get(self.category, {})
        params = section.get("parameters", {})
        values = params.get(key, [])
        if not values:
            return default
        return set(values)


RULE: HarnessCheckRule = UnstructuredLoggingRule()
