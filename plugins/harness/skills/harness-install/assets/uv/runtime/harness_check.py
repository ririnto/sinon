#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.11"
# dependencies = ["libcst>=1.8.6"]
# ///
"""Harness checks enumeration with per-member validator functions."""

from __future__ import annotations

import enum
import fnmatch
import json
import logging
import os
import re
import stat
import sys
from pathlib import Path
from typing import Callable, NamedTuple

import libcst as cst

ROOT = Path.cwd()
STACK = "uv"
MANIFEST_PATH = "docs/harness/manifest.json"


class Finding(NamedTuple):
    """Represents a validation finding with severity, category, and message."""

    severity: str
    category: str
    message: str


def read_text(path: Path) -> str:
    """Read file text, resolving allowed root contract symlinks."""
    resolved_path = path
    if path.is_symlink():
        target = allowed_root_contract_target(path)
        if target is not None:
            resolved_path = target
        else:
            resolved_path = None
    if resolved_path is None:
        result = ""
    else:
        try:
            result = resolved_path.read_text(encoding="utf-8")
        except OSError:
            result = ""
    return result


def is_executable(path: Path) -> bool:
    """Check if file has executable bit, resolving allowed symlinks."""
    resolved_path = path
    if path.is_symlink():
        target = allowed_root_contract_target(path)
        if target is not None:
            resolved_path = target
        else:
            resolved_path = None
    if resolved_path is None:
        result = False
    else:
        try:
            result = bool(resolved_path.stat().st_mode & stat.S_IXUSR)
        except OSError:
            result = False
    return result


def first_line(path: Path) -> str:
    """Get first line of file text."""
    lines = read_text(path).splitlines()
    result = lines[0] if lines else ""
    return result


def relative(path: Path) -> str:
    """Return path relative to ROOT or string representation."""
    try:
        result = path.relative_to(ROOT).as_posix()
    except ValueError:
        result = str(path)
    return result


def allowed_root_contract_target(path: Path) -> Path | None:
    """Resolve root contract symlink (AGENTS.md <-> CLAUDE.md) if valid."""
    result = None
    if path.parent == ROOT and path.name in {"AGENTS.md", "CLAUDE.md"}:
        try:
            target_name = os.readlink(path)
            expected = "CLAUDE.md" if path.name == "AGENTS.md" else "AGENTS.md"
            if target_name == expected:
                target = ROOT / target_name
                if target.parent == ROOT and not target.is_symlink() and target.is_file():
                    result = target
        except OSError:
            pass
    return result


def is_safe_file(path: Path) -> bool:
    """Check if path is a regular file or allowed root contract symlink."""
    if path.is_symlink():
        result = allowed_root_contract_target(path) is not None
    else:
        result = path.is_file()
    return result


def is_safe_directory(path: Path) -> bool:
    """Check if path is a directory (not a symlink)."""
    result = not path.is_symlink() and path.is_dir()
    return result


def safe_walk(base: Path) -> tuple[Path, ...]:
    """Walk directory tree, excluding symlinks."""
    if base.is_symlink() or base.is_file() or not base.is_dir():
        result = ()
    else:
        output = []
        for current, directories, files in os.walk(base, followlinks=False):
            current_path = Path(current)
            directories[:] = [name for name in directories if not (current_path / name).is_symlink()]
            output.extend(child for name in files if not (child := current_path / name).is_symlink())
        result = tuple(output)
    return result


def safe_file_or_walk(base: Path) -> tuple[Path, ...]:
    """Return single file (if safe) or walk directory; no unsafe symlinks."""
    if base.is_symlink() and allowed_root_contract_target(base) is None:
        result = ()
    elif is_safe_file(base):
        result = (base,)
    else:
        result = safe_walk(base)
    return result


def severity_for(manifest: dict, category: str) -> str:
    """Get severity for category from manifest, default to ERROR."""
    result = "ERROR"
    section = manifest.get(category)
    if isinstance(section, dict):
        value = section.get("severity")
        if value in ("ERROR", "WARN", "INFO"):
            result = value
    return result


def _validate_require_files_exist(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate requireFilesExist check."""
    category = "requireFilesExist"
    section = manifest.get(category, {})
    if not isinstance(section, dict):
        return ()
    params = section.get("parameters", {})
    if not isinstance(params, dict):
        return ()
    paths = params.get("paths", [])
    if not isinstance(paths, list):
        return ()
    messages = section.get("messages", {})
    if not isinstance(messages, dict):
        return ()
    template = messages.get("default", "missing file: {path}")
    return tuple(
        Finding(
            severity_for(manifest, category),
            category,
            template.format(path=path),
        )
        for path in paths
        if isinstance(path, str) and not is_safe_file(root / path)
    )


def _validate_require_directories_exist(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate requireDirectoriesExist check."""
    category = "requireDirectoriesExist"
    section = manifest.get(category, {})
    if not isinstance(section, dict):
        return ()
    params = section.get("parameters", {})
    if not isinstance(params, dict):
        return ()
    paths = params.get("paths", [])
    if not isinstance(paths, list):
        return ()
    messages = section.get("messages", {})
    if not isinstance(messages, dict):
        return ()
    template = messages.get("default", "missing directory: {path}")
    return tuple(
        Finding(
            severity_for(manifest, category),
            category,
            template.format(path=path),
        )
        for path in paths
        if isinstance(path, str) and not is_safe_directory(root / path)
    )


def _validate_require_keepfile_in_empty_directories(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate requireKeepfileInEmptyDirectories check."""
    category = "requireKeepfileInEmptyDirectories"
    section = manifest.get(category, {})
    if not isinstance(section, dict):
        return ()
    params = section.get("parameters", {})
    if not isinstance(params, dict):
        return ()
    directories = params.get("directories", [])
    if not isinstance(directories, list):
        return ()
    messages = section.get("messages", {})
    if not isinstance(messages, dict):
        return ()
    template = messages.get(
        "default", "empty directory must keep placeholder or real files: {directory}"
    )

    return tuple(
        Finding(
            severity_for(manifest, category),
            category,
            template.format(directory=directory),
        )
        for directory in directories
        if isinstance(directory, str)
        and is_safe_directory(root / directory)
        and not any(p for p in (root / directory).iterdir() if p.name != ".gitkeep")
        and not is_safe_file(root / directory / ".gitkeep")
    )


def _validate_require_template_groups(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate requireTemplateGroups check."""
    category = "requireTemplateGroups"
    section = manifest.get(category, {})
    if not isinstance(section, dict):
        return ()
    params = section.get("parameters", {})
    if not isinstance(params, dict):
        return ()
    target_root = params.get("targetRoot", "docs/harness/templates")
    groups = params.get("groups", [])
    if not isinstance(groups, list):
        return ()
    messages = section.get("messages", {})
    if not isinstance(messages, dict):
        return ()
    template = messages.get("default", "missing template group: {targetRoot}/{group}")
    return tuple(
        Finding(
            severity_for(manifest, category),
            category,
            template.format(targetRoot=target_root, group=group),
        )
        for group in groups
        if isinstance(group, str) and not is_safe_directory(root / target_root / group)
    )


def _validate_require_doc_headings(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate requireDocHeadings check."""
    category = "requireDocHeadings"
    section = manifest.get(category, {})
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
    prefix = source_filter.get("prefix", "")
    suffix = source_filter.get("suffix", "")

    filtered_files = tuple(
        p for p in source_paths
        if isinstance(p, str) and p.startswith(prefix) and p.endswith(suffix)
    )

    headings = params.get("headings", [])
    if not isinstance(headings, list):
        return ()
    messages = section.get("messages", {})
    if not isinstance(messages, dict):
        return ()
    template = messages.get("default", "doc missing {heading}: {file}")

    return tuple(
        Finding(
            severity_for(manifest, category),
            category,
            template.format(heading=heading, file=file_path),
        )
        for file_path in filtered_files
        if is_safe_file(root / file_path)
        for heading in headings
        if isinstance(heading, str) and heading not in read_text(root / file_path)
    )


def _validate_require_doc_content(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate requireDocContent check."""
    category = "requireDocContent"
    section = manifest.get(category, {})
    if not isinstance(section, dict):
        return ()
    params = section.get("parameters", {})
    if not isinstance(params, dict):
        return ()
    checks = params.get("checks", [])
    if not isinstance(checks, list):
        return ()

    return tuple(
        Finding(severity_for(manifest, category), category, check.get("failureMessage"))
        for check in checks
        if isinstance(check, dict)
        and isinstance(check.get("files"), list)
        and isinstance(check.get("containsAll"), list)
        and isinstance(check.get("failureMessage"), str)
        and not all(
            isinstance(s, str) and s in "\n".join(
                read_text(root / f)
                for f in check.get("files", [])
                if isinstance(f, str) and is_safe_file(root / f)
            )
            for s in check.get("containsAll", [])
        )
    )


def _validate_require_agent_frontmatter(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate requireAgentFrontmatter check."""
    category = "requireAgentFrontmatter"
    section = manifest.get(category, {})
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
            Finding("ERROR", category, f"invalid namePattern regex: {name_pattern_str}"),
        )

    dir_path = root / directory
    missing_dir_msg = messages.get("missingDirectory", ".claude/agents must contain at least one .md agent")
    if not is_safe_directory(dir_path):
        return (Finding(severity_for(manifest, category), category, missing_dir_msg),)

    files = tuple(sorted(p for p in safe_walk(dir_path) if p.parent == dir_path and p.suffix == ".md"))
    if not files:
        return (Finding(severity_for(manifest, category), category, missing_dir_msg),)

    result = []
    for path in files:
        text = read_text(path)
        result.extend([] if text.startswith("---") else [Finding(
            severity_for(manifest, category),
            category,
            messages.get("missingFrontmatter", "agent missing frontmatter: {file}").format(file=relative(path)),
        )])
        result.extend([] if (not text.startswith("---") or name_pattern.search(text)) else [Finding(
            severity_for(manifest, category),
            category,
            messages.get("missingName", "agent missing name: {file}").format(file=relative(path)),
        )])
        result.extend([] if re.search(r"(?m)^description:\s*.+$", text) else [Finding(
            severity_for(manifest, category),
            category,
            messages.get("missingDescription", "agent missing description: {file}").format(file=relative(path)),
        )])
    return tuple(result)


def _validate_require_skill_frontmatter(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate requireSkillFrontmatter check."""
    category = "requireSkillFrontmatter"
    section = manifest.get(category, {})
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
    if not is_safe_directory(dir_path):
        return (Finding(severity_for(manifest, category), category, missing_dir_msg),)

    files = tuple(sorted(p for p in safe_walk(dir_path) if p.name == filename))
    if not files:
        return (Finding(severity_for(manifest, category), category, missing_dir_msg),)

    result = []
    for path in files:
        text = read_text(path)
        result.extend([] if text.startswith("---") else [Finding(
            severity_for(manifest, category),
            category,
            messages.get("missingFrontmatter", "skill missing frontmatter: {file}").format(file=relative(path)),
        )])
        result.extend([
            Finding(
                severity_for(manifest, category),
                category,
                messages.get("missingDescription", f"skill missing {field}: {{file}}").format(file=relative(path)),
            )
            for field in required_fields
            if isinstance(field, str) and not re.search(rf"(?m)^{re.escape(field)}:\s*.+$", text)
        ])
    return tuple(result)


def _validate_forbid_scaffold_leaks(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate forbidScaffoldLeaks check."""
    category = "forbidScaffoldLeaks"
    section = manifest.get(category, {})
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

    active_roots = tuple(root / b for b in bases_data if isinstance(b, str))
    excluded_paths = tuple(
        root / e for e in (excluded_data if isinstance(excluded_data, list) else ())
        if isinstance(e, str)
    )
    extensions = frozenset(
        f".{ext}" for ext in (exts_data if isinstance(exts_data, list) else [])
        if isinstance(ext, str)
    ) or frozenset({".md", ".txt", ".json", ".yml", ".yaml"})

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
            severity_for(manifest, category),
            category,
            messages.get("default", "{label} in active asset: {file}").format(
                label=label, file=relative(path)
            ),
        )
        for base in active_roots
        for path in safe_file_or_walk(base)
        if path.is_file()
        and path.suffix in extensions
        and not any(path == ex or ex in path.parents for ex in excluded_paths)
        for pattern, label in compiled_patterns
        if pattern.search(text := read_text(path))
    )


def _validate_require_hook_shebang(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate requireHookShebang check."""
    category = "requireHookShebang"
    section = manifest.get(category, {})
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
    template = messages.get("default", "{hook} must start with {expectedShebang}")

    return tuple(
        Finding(
            severity_for(manifest, category),
            category,
            template.format(hook=hook, expectedShebang=expected_shebang),
        )
        for hook in hooks
        if isinstance(hook, str) and first_line(root / hook) != expected_shebang
    )


def _validate_require_hook_executable(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate requireHookExecutable check."""
    category = "requireHookExecutable"
    section = manifest.get(category, {})
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
    template = messages.get("default", "{hook} must be executable")

    return tuple(
        Finding(
            severity_for(manifest, category),
            category,
            template.format(hook=hook),
        )
        for hook in hooks
        if isinstance(hook, str) and not is_executable(root / hook)
    )


def _validate_require_hook_generated_marker(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate requireHookGeneratedMarker check."""
    category = "requireHookGeneratedMarker"
    section = manifest.get(category, {})
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

    result = []
    for hook in hooks:
        if not isinstance(hook, str) or not is_safe_file(root / hook):
            continue
        text = read_text(root / hook)
        hook_name = Path(hook).name
        expected_marker = marker_template.format(name=hook_name)
        result.extend([] if expected_marker in text else [Finding(
            severity_for(manifest, category),
            category,
            messages.get("missingMarker", "{hook} must contain generated marker '{marker}'").format(
                hook=hook, marker=expected_marker
            ),
        )])
        result.extend([] if not (isinstance(placeholder_forbidden, str) and placeholder_forbidden in text) else [Finding(
            severity_for(manifest, category),
            category,
            messages.get("placeholderPresent", "{hook} still contains packaging placeholder text").format(hook=hook),
        )])
    return tuple(result)


def _validate_require_hook_stage(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate requireHookStage check."""
    category = "requireHookStage"
    section = manifest.get(category, {})
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
    template = messages.get("default", "{hook} must contain stage marker '# Harness stage: {expectedStage}'")

    return tuple(
        Finding(
            severity_for(manifest, category),
            category,
            template.format(hook=hook_name, expectedStage=expected_stage),
        )
        for hook_name, expected_stage in stack_stages.items()
        if isinstance(hook_name, str)
        and isinstance(expected_stage, str)
        and is_safe_file(path := root / f"docs/harness/git-hooks/{hook_name}")
        and marker_template.format(stage=expected_stage) not in (text := read_text(path))
    )


def _validate_require_hook_command(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate requireHookCommand check."""
    category = "requireHookCommand"
    section = manifest.get(category, {})
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
        return (Finding("ERROR", category, f"validation command for stack '{STACK}' missing from manifest"),)

    stack_pre_commit_commands = (
        allowed_pre_commit_cmds.get(STACK, [])
        if isinstance(allowed_pre_commit_cmds, dict)
        else []
    )
    if not isinstance(stack_pre_commit_commands, list):
        stack_pre_commit_commands = []

    pre_push_file = root / (pre_push_path if isinstance(pre_push_path, str) else "docs/harness/git-hooks/pre-push")
    pre_commit_file = root / (pre_commit_path if isinstance(pre_commit_path, str) else "docs/harness/git-hooks/pre-commit")

    pre_push_text = read_text(pre_push_file) if is_safe_file(pre_push_file) else ""
    pre_commit_text = read_text(pre_commit_file) if is_safe_file(pre_commit_file) else ""

    command_match = re.search(r"# Harness validation command:\s*(.+)$", pre_push_text, re.MULTILINE)
    declared_command = command_match.group(1).strip() if command_match else ""

    result = (
        [] if declared_command else [Finding(
            severity_for(manifest, category),
            category,
            messages.get("missingDeclaration", "pre-push hook must declare Harness validation command"),
        )]
    ) + (
        [] if (not declared_command or declared_command in stack_commands) else [Finding(
            severity_for(manifest, category),
            category,
            messages.get("unsupportedCommand", "pre-push hook declares unsupported validation command: {command}").format(
                command=declared_command
            ),
        )]
    ) + (
        [] if (not declared_command or declared_command in pre_push_text.splitlines()) else [Finding(
            severity_for(manifest, category),
            category,
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
            severity_for(manifest, category),
            category,
            messages.get("preCommitMustNotRunFullStack", "pre-commit hook must not run full stack validation commands"),
        )]
    ) + [
        Finding(
            severity_for(manifest, category),
            category,
            messages.get("ciCommandMatch", f"{ci_file}: CI command mismatch — expected {{command}}").format(
                command=declared_command
            ),
        )
        for ci_file in [".github/workflows/harness.yml", ".gitlab-ci.yml"]
        if is_safe_file(ci_path := root / ci_file)
        and declared_command
        and declared_command not in read_text(ci_path)
    ]

    return tuple(result)


def _validate_require_env_shebang_under(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate requireEnvShebangUnder check."""
    category = "requireEnvShebangUnder"
    section = manifest.get(category, {})
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
    template = messages.get("default", "executable script should use /usr/bin/env shebang: {file}")

    return tuple(
        Finding(
            severity_for(manifest, category),
            category,
            template.format(file=relative(path)),
        )
        for directory in directories
        if isinstance(directory, str)
        for path in safe_walk(root / directory)
        if path.is_file()
        and is_executable(path)
        and (line := first_line(path)).startswith("#!")
        and not line.startswith(expected_prefix)
    )


def _validate_forbid_unchecked_tasks_under(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate forbidUncheckedTasksUnder check."""
    category = "forbidUncheckedTasksUnder"
    section = manifest.get(category, {})
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
    template = messages.get("default", "completed plan has unchecked tasks: {file}")

    if not isinstance(directory, str):
        return ()

    dir_path = root / directory
    if not is_safe_directory(dir_path):
        return ()

    try:
        unchecked_pattern = re.compile(unchecked_pattern_str)
    except re.error:
        return (Finding("ERROR", category, f"invalid uncheckedTaskPattern regex: {unchecked_pattern_str}"),)

    pattern_glob = filename_pattern_str if isinstance(filename_pattern_str, str) else "*.md"

    return tuple(
        Finding(
            severity_for(manifest, category),
            category,
            template.format(file=relative(path)),
        )
        for path in sorted(dir_path.iterdir())
        if path.is_file()
        and path.name != ".gitkeep"
        and fnmatch.fnmatch(path.name, pattern_glob)
        and unchecked_pattern.search(text := read_text(path))
    )


def _validate_forbid_unsafe_symlinks(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate forbidUnsafeSymlinks check."""
    category = "forbidUnsafeSymlinks"
    section = manifest.get(category, {})
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
    scan_bases = (".claude", "docs", ".github", "AGENTS.md", "CLAUDE.md", "ARCHITECTURE.md")

    result = []
    for base in scan_bases:
        base_path = root / base
        if base_path.is_symlink():
            pair = tuple(sorted((base_path.name, os.readlink(base_path).split("/")[-1])))
            result.extend([] if pair in allowed_set else [Finding(
                severity_for(manifest, category),
                category,
                messages.get("scanRootNotAllowed", "symlink scan root is not allowed: {path}").format(
                    path=relative(base_path)
                ),
            )])
            continue
        if not is_safe_directory(base_path):
            continue
        for path in safe_walk(base_path):
            if not path.is_symlink():
                continue
            if path.parent == root and path.name in {"AGENTS.md", "CLAUDE.md"}:
                target_name = os.readlink(path)
                pair = tuple(sorted((path.name, target_name)))
                result.extend([] if pair in allowed_set else [Finding(
                    severity_for(manifest, category),
                    category,
                    messages.get("fileNotAllowed", "symlink file is not allowed: {path}").format(
                        path=relative(path)
                    ),
                )])
            else:
                result.extend([Finding(
                    severity_for(manifest, category),
                    category,
                    messages.get("pathNotAllowed", "symlink path is not allowed: {path}").format(
                        path=relative(path)
                    ),
                )])
    return tuple(result)




def _validate_require_single_top_level_kotlin_declaration(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate requireSingleTopLevelKotlinDeclaration check."""
    category = "requireSingleTopLevelKotlinDeclaration"
    section = manifest.get(category, {})
    if not isinstance(section, dict):
        return ()
    params = section.get("parameters", {})
    if not isinstance(params, dict):
        return ()
    directories = params.get("directories", [])
    if not isinstance(directories, list):
        return ()
    messages = section.get("messages", {})
    if not isinstance(messages, dict):
        return ()
    template = messages.get(
        "default",
        "kotlin file must have exactly one top-level declaration: {file}",
    )

    top_level_pattern = re.compile(
        r"^(class|interface|enum class|data class|sealed class|object|abstract class|val|var|fun|typealias)\b",
        re.MULTILINE,
    )

    result = []
    for directory in directories:
        if not isinstance(directory, str):
            continue
        dir_path = root / directory
        if not is_safe_directory(dir_path):
            continue
        for path in safe_walk(dir_path):
            if path.suffix != ".kt":
                continue
            text = read_text(path)
            declaration_count = len(top_level_pattern.findall(text))
            if declaration_count != 1:
                result.append(Finding(
                    severity_for(manifest, category),
                    category,
                    template.format(file=relative(path)),
                ))
    return tuple(result)


def _stack_sources(root: Path, manifest: dict, category: str) -> tuple[Path, ...]:
    """Resolve source files for a check category based on stack roots and extensions."""
    section = manifest.get(category, {})
    if not isinstance(section, dict):
        return ()
    params = section.get("parameters", {})
    if not isinstance(params, dict):
        return ()
    source_roots_per_stack = params.get("sourceRootsPerStack", {})
    if not isinstance(source_roots_per_stack, dict):
        return ()
    extensions_per_stack = params.get("extensionsPerStack", {})
    if not isinstance(extensions_per_stack, dict):
        return ()

    python_roots = source_roots_per_stack.get("python", [])
    if not isinstance(python_roots, list):
        return ()
    python_exts = extensions_per_stack.get("python", [])
    if not isinstance(python_exts, list):
        return ()

    ext_set = frozenset(e for e in python_exts if isinstance(e, str))
    seen = set()
    result = []

    for root_entry in python_roots:
        if not isinstance(root_entry, str):
            continue
        if "*" in root_entry:
            for resolved_path in root.glob(root_entry):
                if resolved_path.is_dir() and not resolved_path.is_symlink():
                    for file_path in resolved_path.rglob("*"):
                        if file_path.is_file() and not file_path.is_symlink():
                            if "__pycache__" not in file_path.parts:
                                suffix = file_path.suffix.lstrip(".")
                                if suffix in ext_set:
                                    abs_path = file_path.resolve()
                                    if abs_path not in seen:
                                        seen.add(abs_path)
                                        result.append(abs_path)
        else:
            dir_path = root / root_entry
            if dir_path.is_dir() and not dir_path.is_symlink():
                for file_path in dir_path.rglob("*"):
                    if file_path.is_file() and not file_path.is_symlink():
                        if "__pycache__" not in file_path.parts:
                            suffix = file_path.suffix.lstrip(".")
                            if suffix in ext_set:
                                abs_path = file_path.resolve()
                                if abs_path not in seen:
                                    seen.add(abs_path)
                                    result.append(abs_path)

    return tuple(sorted(result))


def _parse_python(path: Path) -> tuple[cst.Module | None, str | None]:
    """Parse a Python file and return Module or error message."""
    text = path.read_text(encoding="utf-8")
    try:
        module = cst.parse_module(text)
        result = (module, None)
    except cst.ParserSyntaxError as err:
        result = (None, str(err))
    return result


def _has_nested_function(func_node: cst.FunctionDef) -> bool:
    """Check if function body contains nested function, class, or lambda."""
    class _NestedFinder(cst.CSTVisitor):
        def __init__(self) -> None:
            super().__init__()
            self.found = False
            self._depth = 0
        def visit_FunctionDef(self, node: cst.FunctionDef) -> bool:
            if self._depth > 0:
                self.found = True
                return False
            self._depth += 1
            return True
        def visit_ClassDef(self, node: cst.ClassDef) -> bool:
            if self._depth > 0:
                self.found = True
                return False
            return True
        def leave_FunctionDef(self, original: cst.FunctionDef) -> None:
            self._depth -= 1
        def visit_Lambda(self, node: cst.Lambda) -> bool:
            self.found = True
            return False
    visitor = _NestedFinder()
    func_node.visit(visitor)
    return visitor.found


def _validate_forbid_greater_than_comparison(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate forbidGreaterThanComparison check."""
    category = "forbidGreaterThanComparison"
    severity = severity_for(manifest, category)
    sources = _stack_sources(root, manifest, category)

    class _ComparisonFinder(cst.CSTVisitor):
        def __init__(self) -> None:
            super().__init__()
            self.findings: list[Finding] = []
        def visit_Comparison(self, node: cst.Comparison) -> bool:
            for target in node.comparators:
                if isinstance(target.operator, (cst.GreaterThan, cst.GreaterThanEqual)):
                    pos = self.get_metadata(cst.metadata.PositionProvider, node)
                    self.findings.append(Finding(
                        severity,
                        category,
                        f"{relative(path)}:{pos.start.line}: forbidden `>`/`>=` comparison; rewrite with `<`/`<=` so the smaller value is on the left",
                    ))
            return True

    result = []
    for path in sources:
        tree, error = _parse_python(path)
        if error is not None:
            result.append(Finding(
                severity,
                category,
                f"{relative(path)}: syntax error: {error}",
            ))
            continue

        wrapper = cst.MetadataWrapper(tree)
        visitor = _ComparisonFinder()
        wrapper.visit(visitor)
        result.extend(visitor.findings)

    return tuple(result)


def _validate_forbid_blank_line_in_leaf_function(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate forbidBlankLineInLeafFunction check."""
    category = "forbidBlankLineInLeafFunction"
    severity = severity_for(manifest, category)
    sources = _stack_sources(root, manifest, category)

    class _BlankLineFinder(cst.CSTVisitor):
        def __init__(self, rel_path: str) -> None:
            super().__init__()
            self.findings: list[Finding] = []
            self.rel_path = rel_path
        def visit_FunctionDef(self, node: cst.FunctionDef) -> bool:
            if _has_nested_function(node):
                return False
            if not isinstance(node.body, cst.IndentedBlock):
                return False
            func_name = node.name.value
            for stmt in node.body.body:
                if isinstance(stmt, cst.EmptyLine) and stmt.comment is None:
                    pos = self.get_metadata(cst.metadata.PositionProvider, stmt)
                    self.findings.append(Finding(
                        severity,
                        category,
                        f"{self.rel_path}:{pos.start.line}: leaf function `{func_name}` contains a blank line; remove or extract the section",
                    ))
            return True

    result = []
    for path in sources:
        tree, error = _parse_python(path)
        if error is not None:
            result.append(Finding(
                severity,
                category,
                f"{relative(path)}: syntax error: {error}",
            ))
            continue

        wrapper = cst.MetadataWrapper(tree)
        visitor = _BlankLineFinder(relative(path))
        wrapper.visit(visitor)
        result.extend(visitor.findings)

    return tuple(result)


def _validate_forbid_early_return(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate forbidEarlyReturn check."""
    category = "forbidEarlyReturn"
    severity = severity_for(manifest, category)
    sources = _stack_sources(root, manifest, category)

    class _EarlyReturnFinder(cst.CSTVisitor):
        def __init__(self, rel_path: str) -> None:
            super().__init__()
            self.findings: list[Finding] = []
            self.rel_path = rel_path
        def visit_FunctionDef(self, node: cst.FunctionDef) -> bool:
            if _has_nested_function(node):
                return False
            if not isinstance(node.body, cst.IndentedBlock):
                return True
            func_name = node.name.value
            body_stmts = node.body.body
            if not body_stmts:
                return True
            for i, stmt in enumerate(body_stmts[:-1]):
                if isinstance(stmt, cst.SimpleStatementLine):
                    for inner_stmt in stmt.body:
                        if isinstance(inner_stmt, cst.Return):
                            pos = self.get_metadata(cst.metadata.PositionProvider, stmt)
                            self.findings.append(Finding(
                                severity,
                                category,
                                f"{self.rel_path}:{pos.start.line}: function `{func_name}` has an early/mid return; restructure with single exit",
                            ))
            return True

    result = []
    for path in sources:
        tree, error = _parse_python(path)
        if error is not None:
            result.append(Finding(
                severity,
                category,
                f"{relative(path)}: syntax error: {error}",
            ))
            continue

        wrapper = cst.MetadataWrapper(tree)
        visitor = _EarlyReturnFinder(relative(path))
        wrapper.visit(visitor)
        result.extend(visitor.findings)

    return tuple(result)


def _validate_forbid_silent_catch(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate forbidSilentCatch check."""
    category = "forbidSilentCatch"
    severity = severity_for(manifest, category)
    sources = _stack_sources(root, manifest, category)

    class _SilentCatchFinder(cst.CSTVisitor):
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
                        category,
                        f"{self.rel_path}:{pos.start.line}: silent catch; rethrow, translate to a Finding, or log via structured logger",
                    ))
                    continue
                if len(body_stmts) == 1:
                    stmt = body_stmts[0]
                    if isinstance(stmt, cst.SimpleStatementLine):
                        if len(stmt.body) == 1 and isinstance(stmt.body[0], cst.Pass):
                            pos = self.get_metadata(cst.metadata.PositionProvider, handler)
                            self.findings.append(Finding(
                                severity,
                                category,
                                f"{self.rel_path}:{pos.start.line}: silent catch; rethrow, translate to a Finding, or log via structured logger",
                            ))
            return True

    result = []
    for path in sources:
        tree, error = _parse_python(path)
        if error is not None:
            result.append(Finding(
                severity,
                category,
                f"{relative(path)}: syntax error: {error}",
            ))
            continue

        wrapper = cst.MetadataWrapper(tree)
        visitor = _SilentCatchFinder(relative(path))
        wrapper.visit(visitor)
        result.extend(visitor.findings)

    return tuple(result)


def _validate_forbid_unstructured_logging(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate forbidUnstructuredLogging check."""
    category = "forbidUnstructuredLogging"
    severity = severity_for(manifest, category)
    sources = _stack_sources(root, manifest, category)

    class _PrintFinder(cst.CSTVisitor):
        def __init__(self, rel_path: str) -> None:
            super().__init__()
            self.findings: list[Finding] = []
            self.rel_path = rel_path
        def visit_Call(self, node: cst.Call) -> bool:
            if isinstance(node.func, cst.Name):
                if node.func.value == "print":
                    pos = self.get_metadata(cst.metadata.PositionProvider, node)
                    self.findings.append(Finding(
                        severity,
                        category,
                        f"{self.rel_path}:{pos.start.line}: unstructured logging `print`; use structured logger",
                    ))
            return True

    result = []
    for path in sources:
        tree, error = _parse_python(path)
        if error is not None:
            result.append(Finding(
                severity,
                category,
                f"{relative(path)}: syntax error: {error}",
            ))
            continue

        wrapper = cst.MetadataWrapper(tree)
        visitor = _PrintFinder(relative(path))
        wrapper.visit(visitor)
        result.extend(visitor.findings)

    return tuple(result)


def _validate_forbid_wildcard_import(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate forbidWildcardImport check."""
    category = "forbidWildcardImport"
    severity = severity_for(manifest, category)
    sources = _stack_sources(root, manifest, category)

    class _WildcardFinder(cst.CSTVisitor):
        def __init__(self, rel_path: str) -> None:
            super().__init__()
            self.findings: list[Finding] = []
            self.rel_path = rel_path
        def visit_ImportFrom(self, node: cst.ImportFrom) -> bool:
            if isinstance(node.names, cst.ImportStar):
                pos = self.get_metadata(cst.metadata.PositionProvider, node)
                module_parts = []
                if isinstance(node.module, cst.Attribute):
                    current = node.module
                    parts = [current.attr.value]
                    while isinstance(current.value, cst.Attribute):
                        current = current.value
                        parts.append(current.attr.value)
                    if isinstance(current.value, cst.Name):
                        parts.append(current.value.value)
                    module_parts = list(reversed(parts))
                elif isinstance(node.module, cst.Name):
                    module_parts = [node.module.value]
                module_str = ".".join(module_parts) if module_parts else "?"
                self.findings.append(Finding(
                    severity,
                    category,
                    f"{self.rel_path}:{pos.start.line}: wildcard import `from {module_str} import *` forbidden; import explicit symbols",
                ))
            return True

    result = []
    for path in sources:
        tree, error = _parse_python(path)
        if error is not None:
            result.append(Finding(
                severity,
                category,
                f"{relative(path)}: syntax error: {error}",
            ))
            continue

        wrapper = cst.MetadataWrapper(tree)
        visitor = _WildcardFinder(relative(path))
        wrapper.visit(visitor)
        result.extend(visitor.findings)

    return tuple(result)


def _validate_require_import_over_fqn(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate requireImportOverFqn check."""
    category = "requireImportOverFqn"
    severity = severity_for(manifest, category)
    sources = _stack_sources(root, manifest, category)

    class _FqnFinder(cst.CSTVisitor):
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
        tree, error = _parse_python(path)
        if error is not None:
            result.append(Finding(
                severity,
                category,
                f"{relative(path)}: syntax error: {error}",
            ))
            continue

        wrapper = cst.MetadataWrapper(tree)
        visitor = _FqnFinder(relative(path))
        wrapper.visit(visitor)
        result.extend(visitor.findings)

    return tuple(result)


def _validate_require_doc_comment_on_public_declaration(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate requireDocCommentOnPublicDeclaration check."""
    category = "requireDocCommentOnPublicDeclaration"
    severity = severity_for(manifest, category)
    sources = _stack_sources(root, manifest, category)

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
                        category,
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
                        category,
                        f"{self.rel_path}:{pos.start.line}: public declaration `{class_name}` is missing a documentation comment",
                    ))
            return True

    result = []
    for path in sources:
        tree, error = _parse_python(path)
        if error is not None:
            result.append(Finding(
                severity,
                category,
                f"{relative(path)}: syntax error: {error}",
            ))
            continue

        wrapper = cst.MetadataWrapper(tree)
        visitor = _DocCommentFinder(relative(path))
        wrapper.visit(visitor)
        result.extend(visitor.findings)

    return tuple(result)


def _validate_forbid_empty_catch_block(root: Path, manifest: dict) -> tuple[Finding, ...]:
    """Validate forbidEmptyCatchBlock check."""
    category = "forbidEmptyCatchBlock"
    severity = severity_for(manifest, category)
    sources = _stack_sources(root, manifest, category)

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
                        category,
                        f"{self.rel_path}:{pos.start.line}: empty catch block; handle, rethrow, or convert to a Finding",
                    ))
            return True

    result = []
    for path in sources:
        tree, error = _parse_python(path)
        if error is not None:
            result.append(Finding(
                severity,
                category,
                f"{relative(path)}: syntax error: {error}",
            ))
            continue

        wrapper = cst.MetadataWrapper(tree)
        visitor = _EmptyCatchFinder(relative(path))
        wrapper.visit(visitor)
        result.extend(visitor.findings)

    return tuple(result)


class HarnessCheck(enum.Enum):
    """Enumeration of harness checks with embedded validator functions."""

    REQUIRE_FILES_EXIST = ("requireFilesExist", _validate_require_files_exist)
    REQUIRE_DIRECTORIES_EXIST = ("requireDirectoriesExist", _validate_require_directories_exist)
    REQUIRE_KEEPFILE_IN_EMPTY_DIRECTORIES = (
        "requireKeepfileInEmptyDirectories",
        _validate_require_keepfile_in_empty_directories,
    )
    REQUIRE_TEMPLATE_GROUPS = ("requireTemplateGroups", _validate_require_template_groups)
    REQUIRE_DOC_HEADINGS = ("requireDocHeadings", _validate_require_doc_headings)
    REQUIRE_DOC_CONTENT = ("requireDocContent", _validate_require_doc_content)
    REQUIRE_AGENT_FRONTMATTER = ("requireAgentFrontmatter", _validate_require_agent_frontmatter)
    REQUIRE_SKILL_FRONTMATTER = ("requireSkillFrontmatter", _validate_require_skill_frontmatter)
    FORBID_SCAFFOLD_LEAKS = ("forbidScaffoldLeaks", _validate_forbid_scaffold_leaks)
    REQUIRE_HOOK_SHEBANG = ("requireHookShebang", _validate_require_hook_shebang)
    REQUIRE_HOOK_EXECUTABLE = ("requireHookExecutable", _validate_require_hook_executable)
    REQUIRE_HOOK_GENERATED_MARKER = (
        "requireHookGeneratedMarker",
        _validate_require_hook_generated_marker,
    )
    REQUIRE_HOOK_STAGE = ("requireHookStage", _validate_require_hook_stage)
    REQUIRE_HOOK_COMMAND = ("requireHookCommand", _validate_require_hook_command)
    REQUIRE_ENV_SHEBANG_UNDER = ("requireEnvShebangUnder", _validate_require_env_shebang_under)
    FORBID_UNCHECKED_TASKS_UNDER = (
        "forbidUncheckedTasksUnder",
        _validate_forbid_unchecked_tasks_under,
    )
    FORBID_UNSAFE_SYMLINKS = ("forbidUnsafeSymlinks", _validate_forbid_unsafe_symlinks)
    REQUIRE_SINGLE_TOP_LEVEL_KOTLIN_DECLARATION = (
        "requireSingleTopLevelKotlinDeclaration",
        _validate_require_single_top_level_kotlin_declaration,
    )
    FORBID_GREATER_THAN_COMPARISON = ("forbidGreaterThanComparison", _validate_forbid_greater_than_comparison)
    FORBID_BLANK_LINE_IN_LEAF_FUNCTION = (
        "forbidBlankLineInLeafFunction",
        _validate_forbid_blank_line_in_leaf_function,
    )
    FORBID_EARLY_RETURN = ("forbidEarlyReturn", _validate_forbid_early_return)
    FORBID_SILENT_CATCH = ("forbidSilentCatch", _validate_forbid_silent_catch)
    FORBID_UNSTRUCTURED_LOGGING = ("forbidUnstructuredLogging", _validate_forbid_unstructured_logging)
    FORBID_WILDCARD_IMPORT = ("forbidWildcardImport", _validate_forbid_wildcard_import)
    REQUIRE_IMPORT_OVER_FQN = ("requireImportOverFqn", _validate_require_import_over_fqn)
    REQUIRE_DOC_COMMENT_ON_PUBLIC_DECLARATION = (
        "requireDocCommentOnPublicDeclaration",
        _validate_require_doc_comment_on_public_declaration,
    )
    FORBID_EMPTY_CATCH_BLOCK = ("forbidEmptyCatchBlock", _validate_forbid_empty_catch_block)

    def __init__(self, category: str, validator: Callable[[Path, dict], tuple[Finding, ...]]):
        """Initialize enum member with category name and validator function."""
        self.category = category
        self._validator = validator

    def applies(self, manifest: dict) -> bool:
        """Check if this check applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        enabled = section.get("enabled", True)
        return enabled is not False

    def validate(self, root: Path, manifest: dict) -> tuple[Finding, ...]:
        """Run validator for this check."""
        return self._validator(root, manifest)
