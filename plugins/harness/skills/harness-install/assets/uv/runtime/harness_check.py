#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-

# /// script
# requires-python = ">=3.11"
# dependencies = ["libcst>=1.8.6"]
# ///
"""Harness checks enumeration with per-member rule classes."""

from __future__ import annotations

import enum
import sys
from collections.abc import Iterable
from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule
from core.rule_context import create_rule_context

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
from rules.ast.kotlin_top_level_declaration_count import (
    RULE as kotlin_top_level_declaration_count,
)
from rules.ast.greater_than_comparison import RULE as greater_than_comparison
from rules.ast.leaf_function_blank_lines import RULE as leaf_function_blank_lines
from rules.ast.early_return import RULE as early_return
from rules.ast.silent_catch import RULE as silent_catch
from rules.ast.unstructured_logging import RULE as unstructured_logging
from rules.ast.wildcard_import import RULE as wildcard_import
from rules.ast.import_over_fqn import RULE as import_over_fqn
from rules.ast.public_declaration_doc_comment import RULE as public_declaration_doc_comment
from rules.ast.empty_catch_block import RULE as empty_catch_block
from rules.text.ci_hook_command_parity import RULE as ci_hook_command_parity

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

MANIFEST_PATH = "docs/harness/manifest.json"


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
    KOTLIN_TOP_LEVEL_DECLARATION_COUNT = (
        "kotlinTopLevelDeclarationCount",
        kotlin_top_level_declaration_count,
    )
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
    EMPTY_CATCH_BLOCK = ("emptyCatchBlock", empty_catch_block)
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
