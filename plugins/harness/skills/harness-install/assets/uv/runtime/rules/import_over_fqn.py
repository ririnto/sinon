#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require import over FQN rule."""

from collections.abc import Iterable

from pathlib import Path

import libcst as cst

from harness_check_rule import Finding, HarnessCheckRule

from .utils import parse_python, relative, severity_for, stack_sources

class ImportOverFqnRule(HarnessCheckRule):
    """Validate importOverFqn check."""

    category = "importOverFqn"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        return section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate importOverFqn check."""
        severity = severity_for(manifest, self.category)
        category = self.category
        sources = stack_sources(root, manifest, self.category)
        class FqnFinder(cst.CSTVisitor):
            METADATA_DEPENDENCIES = (cst.metadata.PositionProvider,)
            def __init__(self, rel_path: str) -> None:
                super().__init__()
                self.findings: list[Finding] = []
                self.rel_path = rel_path
                self.imported_names = set()
            def visit_ImportFrom(self, node: cst.ImportFrom) -> bool:
                if not isinstance(node.names, cst.ImportStar):
                    names_seq = node.names if isinstance(node.names, (list, tuple)) else [node.names]
                    for name_item in names_seq:
                        if isinstance(name_item, cst.ImportAlias):
                            self.imported_names.add(name_item.name.value if isinstance(name_item.name, cst.Name) else str(name_item.name))
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
                        self.findings.append(Finding(
                            severity,
                            category,
                            f"{self.rel_path}:{pos.start.line}: fully qualified name `{fqn_str}` used inline; add an import and use the simple name",
                        ))
                return True
        result = []
        for path in sources:
            tree, error = parse_python(path)
            if error is not None:
                result.append(Finding(
                    severity,
                    category,
                    f"{relative(path)}: syntax error: {error}",
                ))
                continue
            wrapper = cst.MetadataWrapper(tree)
            visitor = FqnFinder(relative(path))
            wrapper.visit(visitor)
            result.extend(visitor.findings)
        return result


RULE: HarnessCheckRule = ImportOverFqnRule()
