#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-

# /// script
# requires-python = ">=3.13"
# dependencies = ["libcst>=1.8.6"]
# ///
"""
Harness checks enumeration with per-member rule classes and validation runner.
"""

from __future__ import annotations

import enum
import logging
import sys
from collections.abc import Iterable
from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule
from core.rule_context import create_rule_context
from reporter import render_findings

from rules.fs.file_presence import RULE as file_presence
from rules.fs.directory_presence import RULE as directory_presence
from rules.fs.empty_directory_placeholders import RULE as empty_directory_placeholders
from rules.text.template_groups import RULE as template_groups
from rules.text.doc_headings import RULE as doc_headings
from rules.text.doc_content import RULE as doc_content
from rules.text.agent_frontmatter import RULE as agent_frontmatter
from rules.text.skill_frontmatter import RULE as skill_frontmatter
from rules.text.scaffold_leaks import RULE as scaffold_leaks
from rules.text.hook_shebang import RULE as hook_shebang
from rules.text.hook_executable import RULE as hook_executable
from rules.text.hook_generated_marker import RULE as hook_generated_marker
from rules.text.hook_stage import RULE as hook_stage
from rules.text.hook_command import RULE as hook_command
from rules.text.env_shebang_usage import RULE as env_shebang_usage
from rules.text.shebang_encoding_marker import RULE as shebang_encoding_marker
from rules.text.unchecked_tasks import RULE as unchecked_tasks
from rules.fs.symlink_safety import RULE as symlink_safety
from rules.ast.greater_than_comparison import RULE as greater_than_comparison
from rules.ast.leaf_function_blank_lines import RULE as leaf_function_blank_lines
from rules.ast.leading_underscore import RULE as leading_underscore
from rules.ast.multiline_doc_style import RULE as multiline_doc_style
from rules.ast.early_return import RULE as early_return
from rules.ast.silent_catch import RULE as silent_catch
from rules.ast.unstructured_logging import RULE as unstructured_logging
from rules.ast.wildcard_import import RULE as wildcard_import
from rules.ast.import_over_fqn import RULE as import_over_fqn
from rules.ast.public_declaration_doc_comment import (
    RULE as public_declaration_doc_comment,
)
from rules.ast.empty_catch_block import RULE as empty_catch_block
from rules.ast.unchecked_cast_suppression import RULE as unchecked_cast_suppression
from rules.ast.triple_quote_inline_comment import RULE as triple_quote_inline_comment
from rules.text.ci_hook_command_parity import RULE as ci_hook_command_parity

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

MANIFEST_PATH = "docs/harness/manifest.json"
ROOT = Path.cwd()


class HarnessCheck(enum.Enum):
    """Enumeration of harness checks with embedded rule singletons."""

    FILE_PRESENCE = ("filePresence", file_presence)
    DIRECTORY_PRESENCE = ("directoryPresence", directory_presence)
    EMPTY_DIRECTORY_PLACEHOLDERS = (
        "emptyDirectoryPlaceholders",
        empty_directory_placeholders,
    )
    TEMPLATE_GROUPS = ("templateGroups", template_groups)
    DOC_HEADINGS = ("docHeadings", doc_headings)
    DOC_CONTENT = ("docContent", doc_content)
    AGENT_FRONTMATTER = ("agentFrontmatter", agent_frontmatter)
    SKILL_FRONTMATTER = ("skillFrontmatter", skill_frontmatter)
    SCAFFOLD_LEAKS = ("scaffoldLeaks", scaffold_leaks)
    HOOK_SHEBANG = ("hookShebang", hook_shebang)
    HOOK_EXECUTABLE = ("hookExecutable", hook_executable)
    HOOK_GENERATED_MARKER = (
        "hookGeneratedMarker",
        hook_generated_marker,
    )
    HOOK_STAGE = ("hookStage", hook_stage)
    HOOK_COMMAND = ("hookCommand", hook_command)
    ENV_SHEBANG_USAGE = ("envShebangUsage", env_shebang_usage)
    SHEBANG_ENCODING_MARKER = (
        "shebangEncodingMarker",
        shebang_encoding_marker,
    )
    UNCHECKED_TASKS = (
        "uncheckedTasks",
        unchecked_tasks,
    )
    SYMLINK_SAFETY = ("symlinkSafety", symlink_safety)
    GREATER_THAN_COMPARISON = ("greaterThanComparison", greater_than_comparison)
    LEAF_FUNCTION_BLANK_LINES = (
        "leafFunctionBlankLines",
        leaf_function_blank_lines,
    )
    EARLY_RETURN = ("earlyReturn", early_return)
    SILENT_CATCH = ("silentCatch", silent_catch)
    UNSTRUCTURED_LOGGING = ("unstructuredLogging", unstructured_logging)
    WILDCARD_IMPORT = ("wildcardImport", wildcard_import)
    IMPORT_OVER_FQN = ("importOverFqn", import_over_fqn)
    PUBLIC_DECLARATION_DOC_COMMENT = (
        "publicDeclarationDocComment",
        public_declaration_doc_comment,
    )
    LEADING_UNDERSCORE = ("leadingUnderscore", leading_underscore)
    MULTILINE_DOC_STYLE = ("multilineDocStyle", multiline_doc_style)
    EMPTY_CATCH_BLOCK = ("emptyCatchBlock", empty_catch_block)
    UNCHECKED_CAST_SUPPRESSION = (
        "uncheckedCastSuppression",
        unchecked_cast_suppression,
    )
    TRIPLE_QUOTE_INLINE_COMMENT = (
        "tripleQuoteInlineComment",
        triple_quote_inline_comment,
    )
    CI_HOOK_COMMAND_PARITY = (
        "ciHookCommandParity",
        ci_hook_command_parity,
    )

    def __init__(self, category: str, rule: HarnessCheckRule):
        """Initialize enum member with category name and rule singleton."""
        self.category = category
        self.rule = rule

    def applies(self, manifest: dict) -> bool:
        """Check if this check applies to the manifest."""
        return self.rule.applies(create_rule_context(Path.cwd(), manifest))

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Run validator for this check."""
        return self.rule.validate(create_rule_context(root, manifest))


def validate(manifest: dict) -> tuple[Finding, ...]:
    """
    Run all applicable checks and return deduplicated findings.

    Dedup key is (severity, category, message, file, start_line); the original
    Finding (with location and fix metadata) is preserved on first occurrence
    so structured reporter output keeps Safety/Help/Before/After sections.
    """
    seen: dict[tuple, Finding] = {}
    for check in HarnessCheck:
        if not check.applies(manifest):
            continue
        for finding in check.validate(ROOT, manifest):
            key = (
                finding.severity,
                finding.category,
                finding.message,
                finding.file,
                finding.start_line,
            )
            if key not in seen:
                seen[key] = finding
    return tuple(seen.values())


def main() -> int:
    """
    Load manifest, validate, and report findings.
    """
    logging.basicConfig(
        level=logging.INFO, format="[%(levelname)s] %(message)s", stream=sys.stderr
    )
    logger = logging.getLogger()
    if not HarnessCheckRule.is_safe_file(ROOT / MANIFEST_PATH):
        logger.error("manifest file missing: docs/harness/manifest.json")
        logger.error("Harness validation failed")
        return 1
    manifest = HarnessCheckRule.load_manifest()
    if not manifest:
        logger.error("manifest file invalid or empty JSON: docs/harness/manifest.json")
        logger.error("Harness validation failed")
        return 1
    for key in manifest.keys():
        if key not in set(
            check.category for check in HarnessCheck
        ) and key not in frozenset(
            {
                "name",
                "description",
                "$schema",
                "seedFiles",
                "generatedArtifacts",
                "harnessEvolution",
                "teamPatterns",
            }
        ):
            logger.warning("unknown manifest key: %s", key)
    findings = validate(manifest)
    for line in render_findings(ROOT, findings):
        print(line)
    return 1 if sum(1 for f in findings if f.severity == "ERROR") > 0 else 0


if __name__ == "__main__":
    raise SystemExit(main())
