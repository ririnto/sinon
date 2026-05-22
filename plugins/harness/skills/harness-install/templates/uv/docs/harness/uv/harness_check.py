#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.11"
# dependencies = []
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
    if path.is_symlink():
        target = allowed_root_contract_target(path)
        if target is None:
            return ""
        path = target
    try:
        return path.read_text(encoding="utf-8")
    except OSError:
        return ""


def is_executable(path: Path) -> bool:
    """Check if file has executable bit, resolving allowed symlinks."""
    if path.is_symlink():
        target = allowed_root_contract_target(path)
        if target is None:
            return False
        path = target
    try:
        return bool(path.stat().st_mode & stat.S_IXUSR)
    except OSError:
        return False


def first_line(path: Path) -> str:
    """Get first line of file text."""
    lines = read_text(path).splitlines()
    return lines[0] if lines else ""


def relative(path: Path) -> str:
    """Return path relative to ROOT or string representation."""
    try:
        return path.relative_to(ROOT).as_posix()
    except ValueError:
        return str(path)


def allowed_root_contract_target(path: Path) -> Path | None:
    """Resolve root contract symlink (AGENTS.md <-> CLAUDE.md) if valid."""
    if path.parent != ROOT or path.name not in {"AGENTS.md", "CLAUDE.md"}:
        return None
    try:
        target_name = os.readlink(path)
    except OSError:
        return None
    expected = "CLAUDE.md" if path.name == "AGENTS.md" else "AGENTS.md"
    if target_name != expected:
        return None
    target = ROOT / target_name
    return (
        target
        if target.parent == ROOT and not target.is_symlink() and target.is_file()
        else None
    )


def is_safe_file(path: Path) -> bool:
    """Check if path is a regular file or allowed root contract symlink."""
    return (
        allowed_root_contract_target(path) is not None
        if path.is_symlink()
        else path.is_file()
    )


def is_safe_directory(path: Path) -> bool:
    """Check if path is a directory (not a symlink)."""
    return not path.is_symlink() and path.is_dir()


def safe_walk(base: Path) -> tuple[Path, ...]:
    """Walk directory tree, excluding symlinks."""
    if base.is_symlink() or base.is_file():
        return ()
    if not base.is_dir():
        return ()
    output = []
    for current, directories, files in os.walk(base, followlinks=False):
        current_path = Path(current)
        directories[:] = [name for name in directories if not (current_path / name).is_symlink()]
        output.extend(child for name in files if not (child := current_path / name).is_symlink())
    return tuple(output)


def safe_file_or_walk(base: Path) -> tuple[Path, ...]:
    """Return single file (if safe) or walk directory; no unsafe symlinks."""
    if base.is_symlink() and allowed_root_contract_target(base) is None:
        return ()
    return (base,) if is_safe_file(base) else safe_walk(base)


def severity_for(manifest: dict, category: str) -> str:
    """Get severity for category from manifest, default to ERROR."""
    section = manifest.get(category)
    if isinstance(section, dict):
        value = section.get("severity")
        if value in ("ERROR", "WARN", "INFO"):
            return value
    return "ERROR"


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
