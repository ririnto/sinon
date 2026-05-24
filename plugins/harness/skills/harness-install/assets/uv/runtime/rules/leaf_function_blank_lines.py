#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Forbid multiple consecutive blank lines in leaf function rule."""

from collections.abc import Iterable

from pathlib import Path

import libcst as cst

from harness_check_rule import Finding, HarnessCheckRule

from .utils import has_nested_function, parse_python, relative, severity_for, stack_sources

class LeafFunctionBlankLinesRule(HarnessCheckRule):
    """Validate leafFunctionBlankLines check."""

    category = "leafFunctionBlankLines"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        return section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate leafFunctionBlankLines check."""
        severity = severity_for(manifest, self.category)
        category = self.category
        max_consecutive_blank_lines = self.max_consecutive_blank_lines(manifest)

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
                                pos = self.get_metadata(cst.metadata.PositionProvider, line)
                                self.findings.append(Finding(
                                    severity,
                                    category,
                                    f"{self.rel_path}:{pos.start.line}: leaf function `{node.name.value}` "
                                    "contains too many blank lines; remove or extract the section",
                                ))
                        else:
                            blank_lines = 0
                    blank_lines = 0
                return True
        findings = []
        for path in stack_sources(root, manifest, self.category):
            tree, error = parse_python(path)
            if error is not None:
                findings.append(Finding(
                    severity,
                    self.category,
                    f"{relative(path)}: syntax error: {error}",
                ))
                continue
            wrapper = cst.MetadataWrapper(tree)
            visitor = BlankLineFinder(relative(path))
            wrapper.visit(visitor)
            findings.extend(visitor.findings)
        return findings

    def max_consecutive_blank_lines(self, manifest: dict) -> int:
        """Return the maximum allowed consecutive blank lines."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return 1
        parameters = section.get("parameters")
        if not isinstance(parameters, dict):
            return 1
        value = parameters.get("maxConsecutiveBlankLines")
        return max(0, value) if isinstance(value, int) else 1


RULE: HarnessCheckRule = LeafFunctionBlankLinesRule()
