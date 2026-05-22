#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.11"
# dependencies = ["libcst>=1.8.6"]
# ///
"""Harness checks enumeration with per-member rule classes."""

from __future__ import annotations

import enum
import logging
import sys
from collections.abc import Iterable
from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from rules.require_files_exist import RULE as require_files_exist
from rules.require_directories_exist import RULE as require_directories_exist
from rules.require_keepfile_in_empty_directories import RULE as require_keepfile_in_empty_directories
from rules.require_template_groups import RULE as require_template_groups
from rules.require_doc_headings import RULE as require_doc_headings
from rules.require_doc_content import RULE as require_doc_content
from rules.require_agent_frontmatter import RULE as require_agent_frontmatter
from rules.require_skill_frontmatter import RULE as require_skill_frontmatter
from rules.forbid_scaffold_leaks import RULE as forbid_scaffold_leaks
from rules.require_hook_shebang import RULE as require_hook_shebang
from rules.require_hook_executable import RULE as require_hook_executable
from rules.require_hook_generated_marker import RULE as require_hook_generated_marker
from rules.require_hook_stage import RULE as require_hook_stage
from rules.require_hook_command import RULE as require_hook_command
from rules.require_env_shebang_under import RULE as require_env_shebang_under
from rules.forbid_unchecked_tasks_under import RULE as forbid_unchecked_tasks_under
from rules.forbid_unsafe_symlinks import RULE as forbid_unsafe_symlinks
from rules.require_single_top_level_kotlin_declaration import RULE as require_single_top_level_kotlin_declaration
from rules.forbid_greater_than_comparison import RULE as forbid_greater_than_comparison
from rules.forbid_blank_line_in_leaf_function import RULE as forbid_blank_line_in_leaf_function
from rules.forbid_early_return import RULE as forbid_early_return
from rules.forbid_silent_catch import RULE as forbid_silent_catch
from rules.forbid_unstructured_logging import RULE as forbid_unstructured_logging
from rules.forbid_wildcard_import import RULE as forbid_wildcard_import
from rules.require_import_over_fqn import RULE as require_import_over_fqn
from rules.require_doc_comment_on_public_declaration import RULE as require_doc_comment_on_public_declaration
from rules.forbid_empty_catch_block import RULE as forbid_empty_catch_block
from rules.require_ci_command_matches_hook import RULE as require_ci_command_matches_hook

MANIFEST_PATH = "docs/harness/manifest.json"


class HarnessCheck(enum.Enum):
    """Enumeration of harness checks with embedded rule singletons."""

    REQUIRE_FILES_EXIST = ("requireFilesExist", require_files_exist)
    REQUIRE_DIRECTORIES_EXIST = ("requireDirectoriesExist", require_directories_exist)
    REQUIRE_KEEPFILE_IN_EMPTY_DIRECTORIES = (
        "requireKeepfileInEmptyDirectories",
        require_keepfile_in_empty_directories,
    )
    REQUIRE_TEMPLATE_GROUPS = ("requireTemplateGroups", require_template_groups)
    REQUIRE_DOC_HEADINGS = ("requireDocHeadings", require_doc_headings)
    REQUIRE_DOC_CONTENT = ("requireDocContent", require_doc_content)
    REQUIRE_AGENT_FRONTMATTER = ("requireAgentFrontmatter", require_agent_frontmatter)
    REQUIRE_SKILL_FRONTMATTER = ("requireSkillFrontmatter", require_skill_frontmatter)
    FORBID_SCAFFOLD_LEAKS = ("forbidScaffoldLeaks", forbid_scaffold_leaks)
    REQUIRE_HOOK_SHEBANG = ("requireHookShebang", require_hook_shebang)
    REQUIRE_HOOK_EXECUTABLE = ("requireHookExecutable", require_hook_executable)
    REQUIRE_HOOK_GENERATED_MARKER = (
        "requireHookGeneratedMarker",
        require_hook_generated_marker,
    )
    REQUIRE_HOOK_STAGE = ("requireHookStage", require_hook_stage)
    REQUIRE_HOOK_COMMAND = ("requireHookCommand", require_hook_command)
    REQUIRE_ENV_SHEBANG_UNDER = ("requireEnvShebangUnder", require_env_shebang_under)
    FORBID_UNCHECKED_TASKS_UNDER = (
        "forbidUncheckedTasksUnder",
        forbid_unchecked_tasks_under,
    )
    FORBID_UNSAFE_SYMLINKS = ("forbidUnsafeSymlinks", forbid_unsafe_symlinks)
    REQUIRE_SINGLE_TOP_LEVEL_KOTLIN_DECLARATION = (
        "requireSingleTopLevelKotlinDeclaration",
        require_single_top_level_kotlin_declaration,
    )
    FORBID_GREATER_THAN_COMPARISON = ("forbidGreaterThanComparison", forbid_greater_than_comparison)
    FORBID_BLANK_LINE_IN_LEAF_FUNCTION = (
        "forbidBlankLineInLeafFunction",
        forbid_blank_line_in_leaf_function,
    )
    FORBID_EARLY_RETURN = ("forbidEarlyReturn", forbid_early_return)
    FORBID_SILENT_CATCH = ("forbidSilentCatch", forbid_silent_catch)
    FORBID_UNSTRUCTURED_LOGGING = ("forbidUnstructuredLogging", forbid_unstructured_logging)
    FORBID_WILDCARD_IMPORT = ("forbidWildcardImport", forbid_wildcard_import)
    REQUIRE_IMPORT_OVER_FQN = ("requireImportOverFqn", require_import_over_fqn)
    REQUIRE_DOC_COMMENT_ON_PUBLIC_DECLARATION = (
        "requireDocCommentOnPublicDeclaration",
        require_doc_comment_on_public_declaration,
    )
    FORBID_EMPTY_CATCH_BLOCK = ("forbidEmptyCatchBlock", forbid_empty_catch_block)
    REQUIRE_CI_COMMAND_MATCHES_HOOK = (
        "requireCiCommandMatchesHook",
        require_ci_command_matches_hook,
    )

    def __init__(self, category: str, rule: HarnessCheckRule):
        """Initialize enum member with category name and rule singleton."""
        self.category = category
        self.rule = rule

    def applies(self, manifest: dict) -> bool:
        """Check if this check applies to the manifest."""
        return self.rule.applies(manifest)

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Run validator for this check."""
        return self.rule.validate(root, manifest)
