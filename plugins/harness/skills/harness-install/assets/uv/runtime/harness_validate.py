#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = ["libcst>=1.8.6"]
# ///
"""
Validate repository harness installation and structure.
"""
from __future__ import annotations

import json
import logging
import os
import re
import stat
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import NamedTuple, TypeGuard

from harness_check import HarnessCheck
from harness_check_rule import Finding, HarnessCheckRule, JsonObject, ROOT

STACK = "uv"
MANIFEST_PATH = "docs/harness/manifest.json"


@dataclass(frozen=True)
class FilePresenceCheck:
    """
    Check that required files exist.
    """
    category: str = "filePresence"

    def applies(self, manifest: dict) -> bool:
        section = manifest.get(self.category)
        return isinstance(section, dict) and section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> tuple[Finding, ...]:
        section = manifest.get(self.category, {})
        if not isinstance(section, dict) or not isinstance(section.get("parameters", {}), dict):
            return ()
        params = section.get("parameters", {})
        paths = params.get("paths", [])
        if not isinstance(paths, list):
            return ()
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return ()
        return tuple(
            Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get("default", "missing file: {path}").format(path=path),
            )
            for path in paths
            if isinstance(path, str) and not HarnessCheckRule.is_safe_file(root / path)
        )


@dataclass(frozen=True)
class DirectoryPresenceCheck:
    """
    Check that required directories exist.
    """
    category: str = "directoryPresence"

    def applies(self, manifest: dict) -> bool:
        section = manifest.get(self.category)
        return isinstance(section, dict) and section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> tuple[Finding, ...]:
        section = manifest.get(self.category, {})
        if not isinstance(section, dict) or not isinstance(section.get("parameters", {}), dict):
            return ()
        params = section.get("parameters", {})
        paths = params.get("paths", [])
        if not isinstance(paths, list):
            return ()
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return ()
        return tuple(
            Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get("default", "missing directory: {path}").format(path=path),
            )
            for path in paths
            if isinstance(path, str) and not HarnessCheckRule.is_safe_directory(root / path)
        )


@dataclass(frozen=True)
class EmptyDirectoryPlaceholdersRule:
    """
    Check that empty directories contain .gitkeep or files.
    """
    category: str = "emptyDirectoryPlaceholders"

    def applies(self, manifest: dict) -> bool:
        section = manifest.get(self.category)
        return isinstance(section, dict) and section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> tuple[Finding, ...]:
        section = manifest.get(self.category, {})
        if not isinstance(section, dict) or not isinstance(section.get("parameters", {}), dict):
            return ()
        params = section.get("parameters", {})
        directories = params.get("directories", [])
        if not isinstance(directories, list):
            return ()
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return ()
        return tuple(
            Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get(
                    "default", "empty directory must keep placeholder or real files: {directory}"
                ).format(directory=directory),
            )
            for directory in directories
            if isinstance(directory, str)
            and HarnessCheckRule.is_safe_directory(root / directory)
            and not any(p for p in (root / directory).iterdir() if p.name != ".gitkeep")
            and not HarnessCheckRule.is_safe_file(root / directory / ".gitkeep")
        )


@dataclass(frozen=True)
class TemplateGroupsCheck:
    """
    Check that template groups exist.
    """
    category: str = "templateGroups"

    def applies(self, manifest: dict) -> bool:
        section = manifest.get(self.category)
        return isinstance(section, dict) and section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> tuple[Finding, ...]:
        section = manifest.get(self.category, {})
        if not isinstance(section, dict) or not isinstance(section.get("parameters", {}), dict):
            return ()
        params = section.get("parameters", {})
        target_root = params.get("targetRoot", "docs/harness/templates")
        groups = params.get("groups", [])
        if not isinstance(groups, list):
            return ()
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return ()
        return tuple(
            Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get("default", "missing template group: {targetRoot}/{group}").format(targetRoot=target_root, group=group),
            )
            for group in groups
            if isinstance(group, str) and not HarnessCheckRule.is_safe_directory(root / target_root / group)
        )


@dataclass(frozen=True)
class DocHeadingsCheck:
    """
    Check that required docs contain required headings.
    """
    category: str = "docHeadings"

    def applies(self, manifest: dict) -> bool:
        section = manifest.get(self.category)
        return isinstance(section, dict) and section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> tuple[Finding, ...]:
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return ()
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return ()
        source_from = params.get("sourceFilesFromCategory")
        if not isinstance(source_from, str):
            return ()
        source_section = manifest.get(source_from, {})
        if not isinstance(source_section, dict):
            return ()
        source_params = source_section.get("parameters", {})
        if not isinstance(source_params, dict):
            return ()
        source_paths = source_params.get("paths", [])
        if not isinstance(source_paths, list):
            return ()
        source_filter = params.get("sourceFilter", {})
        if not isinstance(source_filter, dict):
            return ()
        filtered_files = tuple(
            p for p in source_paths
            if isinstance(p, str) and p.startswith(source_filter.get("prefix", "")) and p.endswith(source_filter.get("suffix", ""))
        )
        headings = params.get("headings", [])
        if not isinstance(headings, list):
            return ()
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return ()
        return tuple(
            Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get("default", "doc missing {heading}: {file}").format(heading=heading, file=file_path),
            )
            for file_path in filtered_files
            if HarnessCheckRule.is_safe_file(root / file_path)
            for heading in headings
            if isinstance(heading, str) and heading not in HarnessCheckRule.read_text(root / file_path)
        )


@dataclass(frozen=True)
class DocContentCheck:
    """
    Check that specified files contain required substrings.
    """
    category: str = "docContent"

    def applies(self, manifest: JsonObject) -> bool:
        section = manifest.get(self.category)
        return HarnessCheckRule.is_json_object(section) and section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: JsonObject) -> tuple[Finding, ...]:
        section = manifest.get(self.category, {})
        if not HarnessCheckRule.is_json_object(section):
            return ()
        params = section.get("parameters", {})
        if not HarnessCheckRule.is_json_object(params):
            return ()
        checks = HarnessCheckRule.json_array(params.get("checks", []))
        findings: list[Finding] = []
        for check in checks:
            if not HarnessCheckRule.is_json_object(check):
                continue
            if not isinstance(check.get("files"), list):
                continue
            failure_message = check.get("failureMessage")
            if not isinstance(failure_message, str):
                continue
            combined = "\n".join(
                HarnessCheckRule.read_text(root / f)
                for f in HarnessCheckRule.json_array(check.get("files", []))
                if isinstance(f, str) and HarnessCheckRule.is_safe_file(root / f)
            )
            if self.condition_matches(check, combined):
                continue
            findings.append(Finding(HarnessCheckRule.severity_for(manifest, self.category), self.category, failure_message))
        return tuple(findings)

    @staticmethod
    def condition_matches(check: JsonObject, content: str) -> bool:
        """
        Evaluate a nested condition expression.
        """
        condition = check.get("condition", check.get("when"))
        if condition is not None:
            return DocContentCheck.evaluate_condition(condition, content)
        return False

    @staticmethod
    def evaluate_condition(condition: object, content: str) -> bool:
        """
        Evaluate nested allOf / anyOf / not / contains content conditions.
        """
        if isinstance(condition, str):
            return condition in content
        if HarnessCheckRule.is_json_array(condition):
            return all(DocContentCheck.evaluate_condition(item, content) for item in condition)
        if not HarnessCheckRule.is_json_object(condition):
            return False
        has_all = "allOf" in condition
        has_any = "anyOf" in condition
        has_contains = "contains" in condition
        has_not = "not" in condition
        if not any((has_all, has_any, has_contains, has_not)):
            return False
        all_of = DocContentCheck.condition_array(condition.get("allOf"))
        any_of = DocContentCheck.condition_array(condition.get("anyOf"))
        contains = DocContentCheck.string_array(condition.get("contains"))
        and_matches = not has_all or all(DocContentCheck.evaluate_condition(item, content) for item in all_of)
        or_matches = not has_any or any(DocContentCheck.evaluate_condition(item, content) for item in any_of)
        contains_matches = all(item in content for item in contains)
        not_condition = condition.get("not")
        not_matches = not has_not or not DocContentCheck.evaluate_condition(not_condition, content)
        return and_matches and or_matches and contains_matches and not_matches

    @staticmethod
    def condition_array(value: object) -> list[object]:
        """
        Normalize a condition value into a list of condition nodes.
        """
        if HarnessCheckRule.is_json_array(value):
            return value
        if isinstance(value, str) or HarnessCheckRule.is_json_object(value):
            return [value]
        return []

    @staticmethod
    def string_array(value: object) -> list[str]:
        """
        Normalize a string or string array into string items.
        """
        if isinstance(value, str):
            return [value]
        if HarnessCheckRule.is_json_array(value):
            return [item for item in value if isinstance(item, str)]
        return []


@dataclass(frozen=True)
class AgentFrontmatterCheck:
    """
    Check that agents have required frontmatter.
    """
    category: str = "agentFrontmatter"

    def applies(self, manifest: dict) -> bool:
        section = manifest.get(self.category)
        return isinstance(section, dict) and section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> tuple[Finding, ...]:
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return ()
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return ()
        directory = params.get("directory", ".claude/agents")
        required_fields = params.get("requiredFields", [])
        if not isinstance(required_fields, list):
            return ()
        name_pattern_str = params.get("namePattern", "(?m)^name:\\s*[-a-z0-9]+\\s*$")
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return ()
        try:
            name_pattern = re.compile(name_pattern_str)
        except re.error:
            return (
                Finding("ERROR", self.category, f"invalid namePattern regex: {name_pattern_str}"),
            )
        dir_path = root / directory
        missing_dir_msg = messages.get("missingDirectory", ".claude/agents must contain at least one .md agent")
        if not HarnessCheckRule.is_safe_directory(dir_path):
            return (Finding(HarnessCheckRule.severity_for(manifest, self.category), self.category, missing_dir_msg),)
        files = tuple(sorted(p for p in HarnessCheckRule.safe_walk(dir_path) if p.parent == dir_path and p.suffix == ".md"))
        if not files:
            return (Finding(HarnessCheckRule.severity_for(manifest, self.category), self.category, missing_dir_msg),)
        findings = []
        for path in files:
            text = HarnessCheckRule.read_text(path)
            findings.extend([] if text.startswith("---") else [Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get("missingFrontmatter", "agent missing frontmatter: {file}").format(file=HarnessCheckRule.relative(path)),
            )])
            findings.extend([] if (not text.startswith("---") or name_pattern.search(text)) else [Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get("missingName", "agent missing name: {file}").format(file=HarnessCheckRule.relative(path)),
            )])
            findings.extend([] if re.search(r"(?m)^description:\s*.+$", text) else [Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get("missingDescription", "agent missing description: {file}").format(file=HarnessCheckRule.relative(path)),
            )])
        return tuple(findings)


@dataclass(frozen=True)
class SkillFrontmatterCheck:
    """
    Check that skills have required frontmatter.
    """
    category: str = "skillFrontmatter"

    def applies(self, manifest: dict) -> bool:
        section = manifest.get(self.category)
        return isinstance(section, dict) and section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> tuple[Finding, ...]:
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return ()
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return ()
        root_directory = params.get("rootDirectory", ".claude/skills")
        filename = params.get("filename", "SKILL.md")
        required_fields = params.get("requiredFields", [])
        if not isinstance(required_fields, list):
            return ()
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return ()
        dir_path = root / root_directory
        missing_dir_msg = messages.get("missingDirectory", ".claude/skills must contain at least one SKILL.md")
        if not HarnessCheckRule.is_safe_directory(dir_path):
            return (Finding(HarnessCheckRule.severity_for(manifest, self.category), self.category, missing_dir_msg),)
        files = tuple(sorted(p for p in HarnessCheckRule.safe_walk(dir_path) if p.name == filename))
        if not files:
            return (Finding(HarnessCheckRule.severity_for(manifest, self.category), self.category, missing_dir_msg),)
        findings = []
        for path in files:
            text = HarnessCheckRule.read_text(path)
            findings.extend([] if text.startswith("---") else [Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get("missingFrontmatter", "skill missing frontmatter: {file}").format(file=HarnessCheckRule.relative(path)),
            )])
            findings.extend([
                Finding(
                    HarnessCheckRule.severity_for(manifest, self.category),
                    self.category,
                    messages.get("missingDescription", f"skill missing {field}: {{file}}").format(file=HarnessCheckRule.relative(path)),
                )
                for field in required_fields
                if isinstance(field, str) and not re.search(rf"(?m)^{re.escape(field)}:\s*.+$", text)
            ])
        return tuple(findings)


@dataclass(frozen=True)
class ScaffoldLeaksCheck:
    """
    Check for unresolved scaffold tokens and placeholders in active assets.
    """
    category: str = "scaffoldLeaks"

    @staticmethod
    def strip_markdown_code(text: str) -> str:
        """
        Remove Markdown code blocks and inline code spans before prose-level checks.
        """
        stripped_lines: list[str] = []
        in_fence = False
        fence_marker = ""
        for line in text.splitlines():
            fence_match = re.match(r" {0,3}(`{3,}|~{3,})", line)
            if fence_match:
                marker = fence_match.group(1)[0]
                if not in_fence:
                    in_fence = True
                    fence_marker = marker
                elif marker == fence_marker:
                    in_fence = False
                stripped_lines.append("")
                continue
            if in_fence:
                stripped_lines.append("")
                continue
            stripped_lines.append(re.sub(r"`+[^`\n]*`+", "", line))
        return "\n".join(stripped_lines)

    def applies(self, manifest: dict) -> bool:
        section = manifest.get(self.category)
        return isinstance(section, dict) and section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> tuple[Finding, ...]:
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return ()
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return ()
        scope = params.get("scope", {})
        if not isinstance(scope, dict):
            return ()
        bases_data = scope.get("bases", [])
        excluded_data = scope.get("excludedSubtrees", [])
        exts_data = scope.get("extensions", [])
        if not isinstance(bases_data, list):
            return ()
        resolved_root = root.resolve()
        active_roots = tuple(
            candidate for b in bases_data if isinstance(b, str)
            for candidate in (root / b,)
            if HarnessCheckRule.is_relative_to(candidate.resolve(), resolved_root)
        )
        excluded_paths = tuple(
            candidate for e in (excluded_data if isinstance(excluded_data, list) else ())
            if isinstance(e, str)
            for candidate in (root / e,)
            if HarnessCheckRule.is_relative_to(candidate.resolve(), resolved_root)
        )
        extensions = frozenset(
            f".{ext}" for ext in (exts_data if isinstance(exts_data, list) else [])
            if isinstance(ext, str)
        ) or frozenset({".md", ".txt"})
        patterns_data = params.get("patterns", [])
        if not isinstance(patterns_data, list):
            return ()
        compiled_patterns = tuple(
            (re.compile(item.get("pattern")), item.get("label"))
            for item in patterns_data
            if isinstance(item, dict)
            and isinstance(item.get("pattern"), str)
            and isinstance(item.get("label"), str)
            and (lambda p=item.get("pattern"): True if re.compile(p) else False)()
        )
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return ()
        return tuple(
            Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get("default", "{label} in active asset: {file}").format(
                    label=label, file=HarnessCheckRule.relative(path)
                ),
            )
            for base in active_roots
            for path in HarnessCheckRule.safe_file_or_walk(base)
            if path.is_file()
            and HarnessCheckRule.is_relative_to(path.resolve(), resolved_root)
            and path.suffix in extensions
            and not any(path == ex or ex in path.parents for ex in excluded_paths)
            for text in (self.strip_markdown_code(HarnessCheckRule.read_text(path)),)
            for pattern, label in compiled_patterns
            if pattern.search(text)
        )


@dataclass(frozen=True)
class HookShebangCheck:
    """
    Check that hooks use correct shebang.
    """
    category: str = "hookShebang"

    def applies(self, manifest: dict) -> bool:
        section = manifest.get(self.category)
        return isinstance(section, dict) and section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> tuple[Finding, ...]:
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return ()
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return ()
        hooks = params.get("hooks", [])
        if not isinstance(hooks, list):
            return ()
        expected_shebang = params.get("expectedShebang", "#!/usr/bin/env sh")
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return ()
        return tuple(
            Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get("default", "{hook} must start with {expectedShebang}").format(hook=hook, expectedShebang=expected_shebang),
            )
            for hook in hooks
            if isinstance(hook, str) and HarnessCheckRule.first_line(root / hook) != expected_shebang
        )


@dataclass(frozen=True)
class HookExecutableCheck:
    """
    Check that hooks are executable.
    """
    category: str = "hookExecutable"

    def applies(self, manifest: dict) -> bool:
        section = manifest.get(self.category)
        return isinstance(section, dict) and section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> tuple[Finding, ...]:
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return ()
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return ()
        hooks = params.get("hooks", [])
        if not isinstance(hooks, list):
            return ()
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return ()
        return tuple(
            Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get("default", "{hook} must be executable").format(hook=hook),
            )
            for hook in hooks
            if isinstance(hook, str) and not HarnessCheckRule.is_executable(root / hook)
        )


@dataclass(frozen=True)
class HookGeneratedMarkerCheck:
    """
    Check that hooks contain generated marker and no packaging placeholder.
    """
    category: str = "hookGeneratedMarker"

    def applies(self, manifest: dict) -> bool:
        section = manifest.get(self.category)
        return isinstance(section, dict) and section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> tuple[Finding, ...]:
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return ()
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return ()
        hooks = params.get("hooks", [])
        if not isinstance(hooks, list):
            return ()
        marker_template = params.get("markerTemplate", "# Harness generated hook: {name}")
        placeholder_forbidden = params.get("placeholderForbidden", "packaged placeholder is replaced during harness installation")
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return ()
        findings = []
        for hook in hooks:
            if isinstance(hook, str) and HarnessCheckRule.is_safe_file(root / hook):
                text = HarnessCheckRule.read_text(root / hook)
                hook_name = Path(hook).name
                expected_marker = marker_template.format(name=hook_name)
                findings.extend([] if expected_marker in text else [Finding(
                    HarnessCheckRule.severity_for(manifest, self.category),
                    self.category,
                    messages.get("missingMarker", "{hook} must contain generated marker '{marker}'").format(
                        hook=hook, marker=expected_marker
                    ),
                )])
                findings.extend([] if not (isinstance(placeholder_forbidden, str) and placeholder_forbidden in text) else [Finding(
                    HarnessCheckRule.severity_for(manifest, self.category),
                    self.category,
                    messages.get("placeholderPresent", "{hook} still contains packaging placeholder text").format(hook=hook),
                )])
        return tuple(findings)


@dataclass(frozen=True)
class HookStageCheck:
    """
    Check that hooks contain stage marker for the active stack.
    """
    category: str = "hookStage"

    def applies(self, manifest: dict) -> bool:
        section = manifest.get(self.category)
        return isinstance(section, dict) and section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> tuple[Finding, ...]:
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return ()
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return ()
        marker_template = params.get("markerTemplate", "# Harness stage: {stage}")
        stages = params.get("stages", {})
        if not isinstance(stages, dict):
            return ()
        stack_stages = stages.get(STACK, {})
        if not isinstance(stack_stages, dict):
            return ()
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return ()
        return tuple(
            Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get("default", "{hook} must contain stage marker '# Harness stage: {expectedStage}'").format(hook=hook_name, expectedStage=expected_stage),
            )
            for hook_name, expected_stage in stack_stages.items()
            if isinstance(hook_name, str)
            and isinstance(expected_stage, str)
            and HarnessCheckRule.is_safe_file(path := root / f"docs/harness/git-hooks/{hook_name}")
            and marker_template.format(stage=expected_stage) not in (text := HarnessCheckRule.read_text(path))
        )


@dataclass(frozen=True)
class HookCommandCheck:
    """
    Check that hooks declare and run correct validation commands.
    """
    category: str = "hookCommand"

    def applies(self, manifest: dict) -> bool:
        section = manifest.get(self.category)
        return isinstance(section, dict) and section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> tuple[Finding, ...]:
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return ()
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return ()
        pre_push_path = params.get("prePushHook", "docs/harness/git-hooks/pre-push")
        pre_commit_path = params.get("preCommitHook", "docs/harness/git-hooks/pre-commit")
        allowed_cmds = params.get("allowedCommands", {})
        allowed_pre_commit_cmds = params.get("allowedPreCommitCommands", {})
        messages = section.get("messages", {})
        if not isinstance(allowed_cmds, dict) or not isinstance(messages, dict):
            return ()
        stack_commands = allowed_cmds.get(STACK)
        if not isinstance(stack_commands, list):
            return (Finding("ERROR", self.category, f"validation command for stack '{STACK}' missing from manifest"),)
        stack_pre_commit_commands = (
            allowed_pre_commit_cmds.get(STACK, [])
            if isinstance(allowed_pre_commit_cmds, dict)
            else []
        )
        if not isinstance(stack_pre_commit_commands, list):
            stack_pre_commit_commands = []
        pre_push_file = root / (pre_push_path if isinstance(pre_push_path, str) else "docs/harness/git-hooks/pre-push")
        pre_commit_file = root / (pre_commit_path if isinstance(pre_commit_path, str) else "docs/harness/git-hooks/pre-commit")
        pre_push_text = HarnessCheckRule.read_text(pre_push_file) if HarnessCheckRule.is_safe_file(pre_push_file) else ""
        pre_commit_text = HarnessCheckRule.read_text(pre_commit_file) if HarnessCheckRule.is_safe_file(pre_commit_file) else ""
        command_match = re.search(r"# Harness validation command:\s*(.+)$", pre_push_text, re.MULTILINE)
        declared_command = command_match.group(1).strip() if command_match else ""
        findings = (
            [] if declared_command else [Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get("missingDeclaration", "pre-push hook must declare Harness validation command"),
            )]
        ) + (
            [] if (not declared_command or declared_command in stack_commands) else [Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get("unsupportedCommand", "pre-push hook declares unsupported validation command: {command}").format(
                    command=declared_command
                ),
            )]
        ) + (
            [] if (not declared_command or declared_command in pre_push_text.splitlines()) else [Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get("commandNotRun", "pre-push hook must run the declared validation command"),
            )]
        ) + (
            [] if not (
                stack_pre_commit_commands
                and not any(
                    re.search(rf"(^|\s)({re.escape(cmd)}|\s)(\s|$)", pre_commit_text)
                    for cmd in stack_pre_commit_commands
                )
                and re.search(
                    r"(^|\s)(uv|bun|gradle|mvn)(\s|$)|\./gradlew|harnessValidate|harness_validate\.py|harness-validate\.ts",
                    pre_commit_text,
                )
            ) else [Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get("preCommitMustNotRunFullStack", "pre-commit hook must not run full stack validation commands"),
            )]
        ) + [
            Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get("ciCommandMatch", f"{ci_file}: CI command mismatch — expected {{command}}").format(
                    command=declared_command
                ),
            )
            for ci_file in [".github/workflows/harness.yml", ".gitlab-ci.yml"]
            if HarnessCheckRule.is_safe_file(ci_path := root / ci_file)
            and declared_command
            and declared_command not in HarnessCheckRule.read_text(ci_path)
        ]
        return tuple(findings)


@dataclass(frozen=True)
class EnvShebangUsageCheck:
    """
    Check that executable scripts use /usr/bin/env shebang.
    """
    category: str = "envShebangUsage"

    def applies(self, manifest: dict) -> bool:
        section = manifest.get(self.category)
        return isinstance(section, dict) and section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> tuple[Finding, ...]:
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return ()
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return ()
        directories = params.get("directories", [])
        if not isinstance(directories, list):
            return ()
        expected_prefix = params.get("expectedPrefix", "#!/usr/bin/env ")
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return ()
        return tuple(
            Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get("default", "executable script should use /usr/bin/env shebang: {file}").format(file=HarnessCheckRule.relative(path)),
            )
            for directory in directories
            if isinstance(directory, str)
            for path in HarnessCheckRule.safe_walk(root / directory)
            if path.is_file()
            and HarnessCheckRule.is_executable(path)
            and (line := HarnessCheckRule.first_line(path)).startswith("#!")
            and not line.startswith(expected_prefix)
        )


@dataclass(frozen=True)
class UncheckedTasksCheck:
    """
    Check that completed plans have no unchecked tasks.
    """
    category: str = "uncheckedTasks"

    def applies(self, manifest: dict) -> bool:
        section = manifest.get(self.category)
        return isinstance(section, dict) and section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> tuple[Finding, ...]:
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return ()
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return ()
        directory = params.get("directory", "docs/exec-plans/completed")
        filename_pattern_str = params.get("filenamePattern", "*.md")
        unchecked_pattern_str = params.get("uncheckedTaskPattern", r"^\s*-\s*\[ \]\s")
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return ()
        if not isinstance(directory, str):
            return ()
        dir_path = root / directory
        if not HarnessCheckRule.is_safe_directory(dir_path):
            return ()
        try:
            unchecked_pattern = re.compile(unchecked_pattern_str)
        except re.error:
            return (Finding("ERROR", self.category, f"invalid uncheckedTaskPattern regex: {unchecked_pattern_str}"),)
        import fnmatch
        return tuple(
            Finding(
                HarnessCheckRule.severity_for(manifest, self.category),
                self.category,
                messages.get("default", "completed plan has unchecked tasks: {file}").format(file=HarnessCheckRule.relative(path)),
            )
            for path in sorted(dir_path.iterdir())
            if path.is_file()
            and path.name != ".gitkeep"
            and fnmatch.fnmatch(path.name, filename_pattern_str if isinstance(filename_pattern_str, str) else "*.md")
            and unchecked_pattern.search(text := HarnessCheckRule.read_text(path))
        )


@dataclass(frozen=True)
class SymlinkSafetyCheck:
    """
    Check that no unsafe symlinks exist outside allowed root contract pairs.
    """
    category: str = "symlinkSafety"

    def applies(self, manifest: dict) -> bool:
        section = manifest.get(self.category)
        return isinstance(section, dict) and section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> tuple[Finding, ...]:
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return ()
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return ()
        allowed_pairs = params.get("allowedSymlinkPairs", [])
        if not isinstance(allowed_pairs, list):
            return ()
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return ()
        allowed_set = frozenset(
            tuple(sorted((p[0], p[1]))) for p in allowed_pairs
            if isinstance(p, list) and len(p) == 2
        )
        findings = []
        for base in (".claude", "docs", ".github", "AGENTS.md", "CLAUDE.md", "ARCHITECTURE.md"):
            base_path = root / base
            if base_path.is_symlink():
                pair = tuple(sorted((base_path.name, os.readlink(base_path).split("/")[-1])))
                findings.extend([] if pair in allowed_set else [Finding(
                    HarnessCheckRule.severity_for(manifest, self.category),
                    self.category,
                    messages.get("scanRootNotAllowed", "symlink scan root is not allowed: {path}").format(
                        path=HarnessCheckRule.relative(base_path)
                    ),
                )])
            elif HarnessCheckRule.is_safe_directory(base_path):
                for path in HarnessCheckRule.safe_walk(base_path):
                    if path.is_symlink():
                        if path.parent == root and path.name in {"AGENTS.md", "CLAUDE.md"}:
                            pair = tuple(sorted((path.name, os.readlink(path))))
                            findings.extend([] if pair in allowed_set else [Finding(
                                HarnessCheckRule.severity_for(manifest, self.category),
                                self.category,
                                messages.get("fileNotAllowed", "symlink file is not allowed: {path}").format(
                                    path=HarnessCheckRule.relative(path)
                                ),
                            )])
                        else:
                            findings.extend([Finding(
                                HarnessCheckRule.severity_for(manifest, self.category),
                                self.category,
                                messages.get("pathNotAllowed", "symlink path is not allowed: {path}").format(
                                    path=HarnessCheckRule.relative(path)
                                ),
                            )])
        return tuple(findings)


CHECKS: tuple[FilePresenceCheck | DirectoryPresenceCheck | EmptyDirectoryPlaceholdersRule | TemplateGroupsCheck | DocHeadingsCheck | DocContentCheck | AgentFrontmatterCheck | SkillFrontmatterCheck | ScaffoldLeaksCheck | HookShebangCheck | HookExecutableCheck | HookGeneratedMarkerCheck | HookStageCheck | HookCommandCheck | EnvShebangUsageCheck | UncheckedTasksCheck | SymlinkSafetyCheck, ...] = (
    FilePresenceCheck(),
    DirectoryPresenceCheck(),
    EmptyDirectoryPlaceholdersRule(),
    TemplateGroupsCheck(),
    DocHeadingsCheck(),
    DocContentCheck(),
    AgentFrontmatterCheck(),
    SkillFrontmatterCheck(),
    ScaffoldLeaksCheck(),
    HookShebangCheck(),
    HookExecutableCheck(),
    HookGeneratedMarkerCheck(),
    HookStageCheck(),
    HookCommandCheck(),
    EnvShebangUsageCheck(),
    UncheckedTasksCheck(),
    SymlinkSafetyCheck(),
)


def validate(manifest: dict) -> tuple[Finding, ...]:
    """
    Run all applicable checks and return deduplicated findings.
    """
    all_findings = tuple(
        f for check in HarnessCheck if check.applies(manifest) for f in check.validate(ROOT, manifest)
    )
    return tuple(Finding(sev, cat, msg) for sev, cat, msg in dict.fromkeys((f.severity, f.category, f.message) for f in all_findings).keys())


def main() -> int:
    """
    Load manifest, validate, and report findings.
    """
    logging.basicConfig(level=logging.INFO, format="[%(levelname)s] %(message)s", stream=sys.stderr)
    logger = logging.getLogger()
    path = ROOT / MANIFEST_PATH
    if not HarnessCheckRule.is_safe_file(path):
        logger.error("manifest file missing: docs/harness/manifest.json")
        logger.error("Harness validation failed")
        return 1
    manifest = HarnessCheckRule.load_manifest()
    if not manifest:
        logger.error("manifest file invalid or empty JSON: docs/harness/manifest.json")
        logger.error("Harness validation failed")
        return 1
    known_categories = set(check.category for check in HarnessCheck)
    known_metadata = {"name", "description", "$schema", "seedFiles", "generatedArtifacts", "harnessEvolution", "teamPatterns"}
    for key in manifest.keys():
        if key not in known_categories and key not in known_metadata:
            logger.warning("unknown manifest key: %s", key)
    findings = validate(manifest)
    severity_order = ("ERROR", "WARN", "INFO")
    grouped = {
        sev: [f for f in findings if f.severity == sev]
        for sev in severity_order
    }
    for severity in severity_order:
        for finding in grouped[severity]:
            logger.log(
                logging.ERROR if severity == "ERROR" else logging.WARNING if severity == "WARN" else logging.INFO,
                finding.message
            )
    if grouped["ERROR"]:
        logger.error("Harness validation failed")
        return 1
    logger.info("Harness validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
