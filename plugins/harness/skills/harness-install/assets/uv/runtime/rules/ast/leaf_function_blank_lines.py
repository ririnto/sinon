#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Forbid multiple consecutive blank lines in leaf function rule.
"""

import sys


from collections.abc import Iterable

import libcst as cst

from rules.harness_check_rule import (
    Finding,
    FindingEdit,
    FindingFix,
    FixSafety,
    HarnessCheckRule,
    has_nested_function,
    parse_python,
)
from core.rule_context import RuleContext, relative, stack_sources_configured

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class LeafFunctionBlankLinesRule(HarnessCheckRule):
    """Validate leafFunctionBlankLines check."""

    category = "leafFunctionBlankLines"

    def applies(self, ctx: RuleContext) -> bool:
        """Check if this rule applies to the context."""
        if not ctx.is_enabled(self.category):
            return False
        return stack_sources_configured(ctx.manifest.raw, self.category)

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """Validate leafFunctionBlankLines check."""
        severity = ctx.severity_of(self.category)
        category = self.category
        max_consecutive_blank_lines = self.max_consecutive_blank_lines(ctx)

        class BlankLineFinder(cst.CSTVisitor):
            METADATA_DEPENDENCIES = (cst.metadata.PositionProvider,)

            def __init__(self, rel_path: str) -> None:
                super().__init__()
                self.findings: list[Finding] = []
                self.rel_path = rel_path

            def visit_FunctionDef(self, node: cst.FunctionDef) -> bool:
                if has_nested_function(node):
                    return False
                if not isinstance(node.body, cst.IndentedBlock):
                    return False
                blank_lines = 0
                for stmt in node.body.body:
                    for line in stmt.leading_lines:
                        if line.comment is None:
                            blank_lines += 1
                            if max_consecutive_blank_lines < blank_lines:
                                pos = self.get_metadata(
                                    cst.metadata.PositionProvider, line
                                )
                                self.findings.append(
                                    Finding(
                                        severity,
                                        category,
                                        f"{self.rel_path}:{pos.start.line}: leaf function `{node.name.value}` "
                                        "contains too many blank lines; remove or extract the section",
                                        file=self.rel_path,
                                        start_line=pos.start.line,
                                        start_column=1,
                                        end_line=pos.start.line,
                                        end_column=1,
                                        fix=FindingFix(
                                            description="remove blank line",
                                            safety=FixSafety.SAFE,
                                            edits=(
                                                FindingEdit(
                                                    file=self.rel_path,
                                                    start_line=pos.start.line,
                                                    start_column=1,
                                                    end_line=pos.start.line + 1,
                                                    end_column=1,
                                                    replacement="",
                                                ),
                                            ),
                                        ),
                                    )
                                )
                        else:
                            blank_lines = 0
                    blank_lines = 0
                return True

        def collect_findings():
            for path in ctx.stack_sources(self.category):
                tree, error = parse_python(path)
                if error is not None:
                    yield Finding(
                        severity,
                        self.category,
                        f"{relative(path, ctx.root)}: syntax error: {error}",
                    )
                    continue
                wrapper = cst.MetadataWrapper(tree)
                visitor = BlankLineFinder(relative(path, ctx.root))
                wrapper.visit(visitor)
                yield from visitor.findings
        return list(collect_findings())

    def max_consecutive_blank_lines(self, ctx: RuleContext) -> int:
        """Return the maximum allowed consecutive blank lines."""
        section = ctx.manifest.raw.get(self.category)
        if not isinstance(section, dict):
            return 1
        parameters = section.get("parameters")
        if not isinstance(parameters, dict):
            return 1
        value = parameters.get("maxConsecutiveBlankLines")
        return max(0, value) if isinstance(value, int) else 1


RULE: HarnessCheckRule = LeafFunctionBlankLinesRule()
