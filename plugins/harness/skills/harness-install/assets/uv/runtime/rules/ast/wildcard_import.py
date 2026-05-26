#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Forbid wildcard import rule.
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


class WildcardImportRule(HarnessCheckRule):
    """Validate wildcardImport check."""

    category = "wildcardImport"

    def applies(self, ctx: RuleContext) -> bool:
        """Check if this rule applies to the context."""
        if not ctx.is_enabled(self.category):
            return False
        return stack_sources_configured(ctx.manifest.raw, self.category)

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """Validate wildcardImport check."""
        severity = ctx.severity_of(self.category)
        category = self.category
        sources = ctx.stack_sources(self.category)

        class WildcardFinder(cst.CSTVisitor):
            METADATA_DEPENDENCIES = (cst.metadata.PositionProvider,)

            def __init__(self, rel_path: str) -> None:
                super().__init__()
                self.findings: list[Finding] = []
                self.rel_path = rel_path

            def visit_ImportFrom(self, node: cst.ImportFrom) -> bool:
                if isinstance(node.names, cst.ImportStar):
                    pos = self.get_metadata(cst.metadata.PositionProvider, node)
                    module_parts = []
                    if isinstance(node.module, cst.Attribute):
                        current = node.module
                        parts = [current.attr.value]
                        while isinstance(current.value, cst.Attribute):
                            current = current.value
                            parts.append(current.attr.value)
                        if isinstance(current.value, cst.Name):
                            parts.append(current.value.value)
                        module_parts = list(reversed(parts))
                    elif isinstance(node.module, cst.Name):
                        module_parts = [node.module.value]
                    module_str = ".".join(module_parts) if module_parts else "?"
                    self.findings.append(
                        Finding(
                            severity,
                            category,
                            f"{self.rel_path}:{pos.start.line}: wildcard import `from {module_str} import *` forbidden; import explicit symbols",
                            file=self.rel_path,
                            start_line=pos.start.line,
                            start_column=pos.start.column + 1,
                            end_line=pos.end.line,
                            end_column=pos.end.column + 1,
                            fix=FindingFix(
                                description="expand * to explicit symbol list; may alter name resolution",
                                safety=FixSafety.UNSAFE,
                                edits=(),
                            ),
                        )
                    )
                return True

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
                visitor = WildcardFinder(relative(path, ctx.root))
                wrapper.visit(visitor)
                yield from visitor.findings

        return list(collect_findings())


RULE: HarnessCheckRule = WildcardImportRule()
