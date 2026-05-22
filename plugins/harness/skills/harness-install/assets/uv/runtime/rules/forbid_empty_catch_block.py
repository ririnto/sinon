#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Forbid empty catch block rule."""

from collections.abc import Iterable

from pathlib import Path

import libcst as cst

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import parse_python, relative, severity_for, stack_sources

class ForbidEmptyCatchBlockRule(HarnessCheckRule):
    """Validate forbidEmptyCatchBlock check."""

    category = "forbidEmptyCatchBlock"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        enabled = section.get("enabled", True)
        return enabled is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate forbidEmptyCatchBlock check."""
        severity = severity_for(manifest, self.category)
        sources = stack_sources(root, manifest, self.category)
        class _EmptyCatchFinder(cst.CSTVisitor):
            def __init__(self, rel_path: str) -> None:
                super().__init__()
                self.findings: list[Finding] = []
                self.rel_path = rel_path
            def visit_Try(self, node: cst.Try) -> bool:
                for handler in node.handlers:
                    if not isinstance(handler.body, cst.IndentedBlock):
                        continue
                    body_stmts = handler.body.body
                    if not body_stmts:
                        pos = self.get_metadata(cst.metadata.PositionProvider, handler)
                        self.findings.append(Finding(
                            severity,
                            self.category,
                            f"{self.rel_path}:{pos.start.line}: empty catch block; handle, rethrow, or convert to a Finding",
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
            visitor = _EmptyCatchFinder(relative(path))
            wrapper.visit(visitor)
            result.extend(visitor.findings)
        return result


RULE: HarnessCheckRule = ForbidEmptyCatchBlockRule()
