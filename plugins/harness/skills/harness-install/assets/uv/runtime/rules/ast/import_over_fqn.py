#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Require import over FQN rule.
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


class ImportOverFqnRule(HarnessCheckRule):
    """Validate importOverFqn check."""

    category = "importOverFqn"

    def applies(self, ctx: RuleContext) -> bool:
        """Check if this rule applies to the context."""
        if not ctx.is_enabled(self.category):
            return False
        return stack_sources_configured(ctx.manifest.raw, self.category)

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """Validate importOverFqn check."""
        severity = ctx.severity_of(self.category)
        category = self.category
        sources = ctx.stack_sources(self.category)

        class FqnFinder(cst.CSTVisitor):
            METADATA_DEPENDENCIES = (cst.metadata.PositionProvider,)

            def __init__(self, rel_path: str) -> None:
                super().__init__()
                self.findings: list[Finding] = []
                self.rel_path = rel_path
                self.imported_names = set()

            def visit_ImportFrom(self, node: cst.ImportFrom) -> bool:
                if not isinstance(node.names, cst.ImportStar):
                    names_seq = (
                        node.names
                        if isinstance(node.names, (list, tuple))
                        else [node.names]
                    )
                    for name_item in names_seq:
                        if isinstance(name_item, cst.ImportAlias):
                            self.imported_names.add(
                                name_item.name.value
                                if isinstance(name_item.name, cst.Name)
                                else str(name_item.name)
                            )
                return True

            def visit_Attribute(self, node: cst.Attribute) -> bool:
                depth = 0
                current = node
                while isinstance(current, cst.Attribute):
                    depth += 1
                    current = current.value
                if depth >= 2 and isinstance(current, cst.Name):
                    fqn_parts = [current.value]
                    current = node
                    while isinstance(current, cst.Attribute):
                        fqn_parts.append(current.attr.value)
                        current = current.value
                    fqn_parts.reverse()
                    simple_name = fqn_parts[0]
                    if simple_name not in self.imported_names:
                        fqn_str = ".".join(fqn_parts)
                        pos = self.get_metadata(cst.metadata.PositionProvider, node)
                        self.findings.append(
                            Finding(
                                severity,
                                category,
                                f"{self.rel_path}:{pos.start.line}: fully qualified name `{fqn_str}` used inline; add an import and use the simple name",
                                file=self.rel_path,
                                start_line=pos.start.line,
                                start_column=pos.start.column + 1,
                                end_line=pos.end.line,
                                end_column=pos.end.column + 1,
                                fix=FindingFix(
                                    description="add import statement for top-level module; may alter resolution",
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
                visitor = FqnFinder(relative(path, ctx.root))
                wrapper.visit(visitor)
                yield from visitor.findings
        return list(collect_findings())


RULE: HarnessCheckRule = ImportOverFqnRule()
