#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require doc comment on public declaration rule."""

from collections.abc import Iterable

from pathlib import Path

import libcst as cst

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import parse_python, relative, severity_for, stack_sources

class RequireDocCommentOnPublicDeclarationRule(HarnessCheckRule):
    """Validate requireDocCommentOnPublicDeclaration check."""

    category = "requireDocCommentOnPublicDeclaration"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        return section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate requireDocCommentOnPublicDeclaration check."""
        severity = severity_for(manifest, self.category)
        sources = stack_sources(root, manifest, self.category)
        class _DocCommentFinder(cst.CSTVisitor):
            def __init__(self, rel_path: str) -> None:
                super().__init__()
                self.findings: list[Finding] = []
                self.rel_path = rel_path
            def visit_FunctionDef(self, node: cst.FunctionDef) -> bool:
                func_name = node.name.value
                if not func_name.startswith("_"):
                    if not isinstance(node.body, cst.IndentedBlock):
                        return True
                    if not node.body.body:
                        return True
                    first_stmt = node.body.body[0]
                    has_docstring = False
                    if isinstance(first_stmt, cst.SimpleStatementLine):
                        if first_stmt.body and isinstance(first_stmt.body[0], cst.Expr):
                            expr_value = first_stmt.body[0].value
                            if isinstance(expr_value, cst.SimpleString) or isinstance(expr_value, cst.ConcatenatedString):
                                has_docstring = True
                    if not has_docstring:
                        pos = self.get_metadata(cst.metadata.PositionProvider, node)
                        self.findings.append(Finding(
                            severity,
                            self.category,
                            f"{self.rel_path}:{pos.start.line}: public declaration `{func_name}` is missing a documentation comment",
                        ))
                return True
            def visit_ClassDef(self, node: cst.ClassDef) -> bool:
                class_name = node.name.value
                if not class_name.startswith("_"):
                    if not isinstance(node.body, cst.IndentedBlock):
                        return True
                    if not node.body.body:
                        return True
                    first_stmt = node.body.body[0]
                    has_docstring = False
                    if isinstance(first_stmt, cst.SimpleStatementLine):
                        if first_stmt.body and isinstance(first_stmt.body[0], cst.Expr):
                            expr_value = first_stmt.body[0].value
                            if isinstance(expr_value, cst.SimpleString) or isinstance(expr_value, cst.ConcatenatedString):
                                has_docstring = True
                    if not has_docstring:
                        pos = self.get_metadata(cst.metadata.PositionProvider, node)
                        self.findings.append(Finding(
                            severity,
                            self.category,
                            f"{self.rel_path}:{pos.start.line}: public declaration `{class_name}` is missing a documentation comment",
                        ))
                return True
        result = []
        for path in sources:
            tree, error = parse_python(path)
            if error is not None:
                result.append(Finding(
                    severity,
                    self.category,
                    f"{relative(path)}: syntax error: {error}",
                ))
                continue
            wrapper = cst.MetadataWrapper(tree)
            visitor = _DocCommentFinder(relative(path))
            wrapper.visit(visitor)
            result.extend(visitor.findings)
        return result


RULE: HarnessCheckRule = RequireDocCommentOnPublicDeclarationRule()
