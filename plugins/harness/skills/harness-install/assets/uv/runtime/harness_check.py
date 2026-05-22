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

from rules.require_files_exist import RequireFilesExistRule
from rules.require_directories_exist import RequireDirectoriesExistRule
from rules.require_keepfile_in_empty_directories import RequireKeepleInEmptyDirectoriesRule
from rules.require_template_groups import RequireTemplateGroupsRule
from rules.require_doc_headings import RequireDocHeadingsRule
from rules.require_doc_content import RequireDocContentRule
from rules.require_agent_frontmatter import RequireAgentFrontmatterRule
from rules.require_skill_frontmatter import RequireSkillFrontmatterRule
from rules.forbid_scaffold_leaks import ForbidScaffoldLeaksRule
from rules.require_hook_shebang import RequireHookShebangRule
from rules.require_hook_executable import RequireHookExecutableRule
from rules.require_hook_generated_marker import RequireHookGeneratedMarkerRule
from rules.require_hook_stage import RequireHookStageRule
from rules.require_hook_command import RequireHookCommandRule
from rules.require_env_shebang_under import RequireEnvShebangUnderRule
from rules.forbid_unchecked_tasks_under import ForbidUncheckedTasksUnderRule
from rules.forbid_unsafe_symlinks import ForbidUnsafeSymlinksRule
from rules.require_single_top_level_kotlin_declaration import RequireSingleTopLevelKotlinDeclarationRule
from rules.forbid_greater_than_comparison import ForbidGreaterThanComparisonRule
from rules.forbid_blank_line_in_leaf_function import ForbidBlankLineInLeafFunctionRule
from rules.forbid_early_return import ForbidEarlyReturnRule
from rules.forbid_silent_catch import ForbidSilentCatchRule
from rules.forbid_unstructured_logging import ForbidUnstructuredLoggingRule
from rules.forbid_wildcard_import import ForbidWildcardImportRule
from rules.require_import_over_fqn import RequireImportOverFqnRule
from rules.require_doc_comment_on_public_declaration import RequireDocCommentOnPublicDeclarationRule
from rules.forbid_empty_catch_block import ForbidEmptyCatchBlockRule

MANIFEST_PATH = "docs/harness/manifest.json"


class HarnessCheck(enum.Enum):
    """Enumeration of harness checks with embedded rule classes."""

    REQUIRE_FILES_EXIST = ("requireFilesExist", RequireFilesExistRule)
    REQUIRE_DIRECTORIES_EXIST = ("requireDirectoriesExist", RequireDirectoriesExistRule)
    REQUIRE_KEEPFILE_IN_EMPTY_DIRECTORIES = (
        "requireKeepfileInEmptyDirectories",
        RequireKeepleInEmptyDirectoriesRule,
    )
    REQUIRE_TEMPLATE_GROUPS = ("requireTemplateGroups", RequireTemplateGroupsRule)
    REQUIRE_DOC_HEADINGS = ("requireDocHeadings", RequireDocHeadingsRule)
    REQUIRE_DOC_CONTENT = ("requireDocContent", RequireDocContentRule)
    REQUIRE_AGENT_FRONTMATTER = ("requireAgentFrontmatter", RequireAgentFrontmatterRule)
    REQUIRE_SKILL_FRONTMATTER = ("requireSkillFrontmatter", RequireSkillFrontmatterRule)
    FORBID_SCAFFOLD_LEAKS = ("forbidScaffoldLeaks", ForbidScaffoldLeaksRule)
    REQUIRE_HOOK_SHEBANG = ("requireHookShebang", RequireHookShebangRule)
    REQUIRE_HOOK_EXECUTABLE = ("requireHookExecutable", RequireHookExecutableRule)
    REQUIRE_HOOK_GENERATED_MARKER = (
        "requireHookGeneratedMarker",
        RequireHookGeneratedMarkerRule,
    )
    REQUIRE_HOOK_STAGE = ("requireHookStage", RequireHookStageRule)
    REQUIRE_HOOK_COMMAND = ("requireHookCommand", RequireHookCommandRule)
    REQUIRE_ENV_SHEBANG_UNDER = ("requireEnvShebangUnder", RequireEnvShebangUnderRule)
    FORBID_UNCHECKED_TASKS_UNDER = (
        "forbidUncheckedTasksUnder",
        ForbidUncheckedTasksUnderRule,
    )
    FORBID_UNSAFE_SYMLINKS = ("forbidUnsafeSymlinks", ForbidUnsafeSymlinksRule)
    REQUIRE_SINGLE_TOP_LEVEL_KOTLIN_DECLARATION = (
        "requireSingleTopLevelKotlinDeclaration",
        RequireSingleTopLevelKotlinDeclarationRule,
    )
    FORBID_GREATER_THAN_COMPARISON = ("forbidGreaterThanComparison", ForbidGreaterThanComparisonRule)
    FORBID_BLANK_LINE_IN_LEAF_FUNCTION = (
        "forbidBlankLineInLeafFunction",
        ForbidBlankLineInLeafFunctionRule,
    )
    FORBID_EARLY_RETURN = ("forbidEarlyReturn", ForbidEarlyReturnRule)
    FORBID_SILENT_CATCH = ("forbidSilentCatch", ForbidSilentCatchRule)
    FORBID_UNSTRUCTURED_LOGGING = ("forbidUnstructuredLogging", ForbidUnstructuredLoggingRule)
    FORBID_WILDCARD_IMPORT = ("forbidWildcardImport", ForbidWildcardImportRule)
    REQUIRE_IMPORT_OVER_FQN = ("requireImportOverFqn", RequireImportOverFqnRule)
    REQUIRE_DOC_COMMENT_ON_PUBLIC_DECLARATION = (
        "requireDocCommentOnPublicDeclaration",
        RequireDocCommentOnPublicDeclarationRule,
    )
    FORBID_EMPTY_CATCH_BLOCK = ("forbidEmptyCatchBlock", ForbidEmptyCatchBlockRule)

    def __init__(self, category: str, rule_cls: type[HarnessCheckRule]):
        """Initialize enum member with category name and rule class."""
        self.category = category
        self._rule_cls = rule_cls

    def applies(self, manifest: dict) -> bool:
        """Check if this check applies to the manifest."""
        rule = self._rule_cls()
        return rule.applies(manifest)

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Run validator for this check."""
        rule = self._rule_cls()
        return rule.validate(root, manifest)
