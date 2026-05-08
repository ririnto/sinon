"""Validate a target repository's harness-engineering configuration."""

from __future__ import annotations

import argparse
import json
import os
import shlex
import sys
from pathlib import Path
from typing import Any


DEFAULT_CONFIG_PATH = "docs/harness-engineering/harness-engineering.json"
REQUIRED_TOP_LEVEL = ("stage", "docs", "scripts", "checks", "ci", "hooks")
REQUIRED_STAGE = ("current", "target", "mode", "exitGate")
REQUIRED_DOCS = (
    "harnessRoot",
    "config",
    "convention",
    "entrypoint",
    "compatibilitySymlink",
    "compatibilitySymlinkTarget",
    "compatibilitySymlinkStatus",
    "architecture",
    "guardrails",
    "knownViolations",
    "readiness",
    "updates",
)
REQUIRED_SCRIPTS = ("root", "validator", "wrapper")
REQUIRED_CI_PROVIDERS = ("github-actions", "gitlab-ci", "none")
REQUIRED_HOOKS = ("enabled", "path", "commitMsg", "prePush")
REQUIRED_SYMLINK_STATUSES = ("required", "pending-conflict", "not-applicable")
REPO_PATH_FIELDS = (
    "docs.harnessRoot",
    "docs.config",
    "docs.entrypoint",
    "docs.compatibilitySymlink",
    "docs.compatibilitySymlinkTarget",
    "docs.architecture",
    "docs.guardrails",
    "docs.knownViolations",
    "docs.readiness",
    "docs.updates",
    "scripts.root",
    "scripts.validator",
    "scripts.wrapper",
    "ci.githubWorkflow",
    "ci.gitlabConfig",
    "hooks.path",
    "hooks.commitMsg",
    "hooks.prePush",
)
UNSAFE_COMMAND_CHARS = frozenset(";|&`$><\n")
TRUSTED_COMMAND_FORMS = (
    "sh scripts/harness/validate_harness.sh",
    "node scripts/harness/validate-harness.mjs",
    "./gradlew harnessCheck",
    "mvn local.harness:harness-maven-plugin:harness-check",
)


def is_repo_relative_path(path_value: str) -> bool:
    """Return whether a config path stays relative to the repository."""
    if "\\" in path_value:
        return False
    if len(path_value) >= 2 and path_value[1] == ":" and path_value[0].isalpha():
        return False
    if path_value.startswith("//"):
        return False
    path = Path(path_value)
    return bool(path_value) and not path.is_absolute() and ".." not in path.parts


def validate_repo_relative_path(path_value: Any, field_name: str) -> list[str]:
    """Validate one config path field without checking existence."""
    if not isinstance(path_value, str):
        return [f"{field_name} must be a string path"]
    if not is_repo_relative_path(path_value):
        return [
            f"{field_name} must be a repository-relative path without '..': {path_value}"
        ]
    return []


def load_config(config_path: Path) -> dict[str, Any]:
    """Load the JSON config document from disk."""
    with config_path.open("r", encoding="utf-8") as config_file:
        data = json.load(config_file)
    if not isinstance(data, dict):
        raise ValueError("config root must be a JSON object")
    return data


def require_mapping(data: dict[str, Any], key: str) -> dict[str, Any]:
    """Return a nested mapping or raise a validation error."""
    value = data.get(key)
    if not isinstance(value, dict):
        raise ValueError(f"{key} must be an object")
    return value


def get_dotted_value(data: dict[str, Any], dotted_field: str) -> Any:
    """Return a dotted field value from the config, or None when absent."""
    current: Any = data
    for segment in dotted_field.split("."):
        if not isinstance(current, dict) or segment not in current:
            return None
        current = current[segment]
    return current


def validate_string_list(values: Any, field_name: str) -> list[str]:
    """Validate a list of non-empty strings."""
    if (
        not isinstance(values, list)
        or not values
        or not all(isinstance(item, str) and item for item in values)
    ):
        return [f"{field_name} must be a non-empty list of strings"]
    return []


def command_is_allowed(command: str) -> bool:
    """Return whether a configured command matches a trusted argv form exactly."""
    return command in TRUSTED_COMMAND_FORMS


def validate_command(command: Any, field_name: str) -> list[str]:
    """Validate a configured shell command string without executing it."""
    if not isinstance(command, str):
        return [f"{field_name} must be a command string"]
    if any(char in command for char in UNSAFE_COMMAND_CHARS):
        return [f"{field_name} contains shell metacharacters: {command}"]
    try:
        parts = shlex.split(command)
    except ValueError as error:
        return [f"{field_name} is not parseable: {error}"]
    if not parts:
        return [f"{field_name} contains an empty command"]
    if not command_is_allowed(command):
        return [f"{field_name} command is not an allowed argv form: {command}"]
    return []


def validate_command_list(commands: Any, field_name: str) -> list[str]:
    """Validate configured shell command strings without executing them."""
    if not isinstance(commands, list) or not all(
        isinstance(item, str) for item in commands
    ):
        return [f"{field_name} must be a list of commands"]
    errors: list[str] = []
    for index, command in enumerate(commands):
        errors.extend(validate_command(command, f"{field_name}[{index}]"))
    return errors


def validate_shape(config: dict[str, Any]) -> list[str]:
    """Validate the six-field harness config contract."""
    errors: list[str] = []
    for key in REQUIRED_TOP_LEVEL:
        if key not in config:
            errors.append(f"missing top-level field: {key}")
    for key in config:
        if key not in REQUIRED_TOP_LEVEL:
            errors.append(f"unexpected top-level field: {key}")
    if errors:
        return errors

    stage = require_mapping(config, "stage")
    docs = require_mapping(config, "docs")
    scripts = require_mapping(config, "scripts")
    checks = require_mapping(config, "checks")
    ci = require_mapping(config, "ci")
    hooks = require_mapping(config, "hooks")

    for key in REQUIRED_STAGE:
        if key not in stage:
            errors.append(f"missing stage field: {key}")
    if not isinstance(stage.get("current"), int):
        errors.append("stage.current must be an integer")
    if not isinstance(stage.get("target"), int):
        errors.append("stage.target must be an integer")
    if not isinstance(stage.get("mode"), str) or not stage.get("mode"):
        errors.append("stage.mode must be a non-empty string")
    if not isinstance(stage.get("exitGate"), str) or not stage.get("exitGate"):
        errors.append("stage.exitGate must be a non-empty string")

    for key in REQUIRED_DOCS:
        if key not in docs:
            errors.append(f"missing docs field: {key}")
    if docs.get("config") != DEFAULT_CONFIG_PATH:
        errors.append(f"docs.config must be {DEFAULT_CONFIG_PATH}")
    if docs.get("harnessRoot") != "docs/harness-engineering":
        errors.append("docs.harnessRoot must be docs/harness-engineering")
    if not isinstance(docs.get("convention"), str) or not docs.get("convention"):
        errors.append("docs.convention must be a non-empty string")
    if docs.get("entrypoint") != "CLAUDE.md":
        errors.append("docs.entrypoint must be CLAUDE.md")
    if (
        docs.get("compatibilitySymlink") != "AGENTS.md"
        or docs.get("compatibilitySymlinkTarget") != "CLAUDE.md"
    ):
        errors.append("docs compatibility symlink must be AGENTS.md -> CLAUDE.md")
    if docs.get("compatibilitySymlinkStatus") not in REQUIRED_SYMLINK_STATUSES:
        errors.append(
            f"docs.compatibilitySymlinkStatus must be one of: {', '.join(REQUIRED_SYMLINK_STATUSES)}"
        )

    for key in REQUIRED_SCRIPTS:
        if key not in scripts:
            errors.append(f"missing scripts field: {key}")

    errors.extend(
        validate_command_list(checks.get("requiredCommands"), "checks.requiredCommands")
    )
    if "optionalCommands" in checks:
        errors.extend(
            validate_command_list(checks["optionalCommands"], "checks.optionalCommands")
        )
    if "allowedCommandPrefixes" in checks:
        errors.extend(
            validate_command_list(
                checks["allowedCommandPrefixes"], "checks.allowedCommandPrefixes"
            )
        )

    provider = ci.get("provider")
    if provider not in REQUIRED_CI_PROVIDERS:
        errors.append(f"ci.provider must be one of: {', '.join(REQUIRED_CI_PROVIDERS)}")
    branches = ci.get("branches")
    if isinstance(branches, dict):
        for key in ("push", "pullRequest", "mergeRequest"):
            errors.extend(validate_string_list(branches.get(key), f"ci.branches.{key}"))
    elif branches is not None:
        errors.append("ci.branches must be an object")

    for key in REQUIRED_HOOKS:
        if key not in hooks:
            errors.append(f"missing hooks field: {key}")
    if not isinstance(hooks.get("enabled"), bool):
        errors.append("hooks.enabled must be a boolean")

    for field_name in REPO_PATH_FIELDS:
        path_value = get_dotted_value(config, field_name)
        if path_value is not None:
            errors.extend(validate_repo_relative_path(path_value, field_name))
    return errors


def validate_paths(
    config: dict[str, Any], root: Path, template_mode: bool
) -> list[str]:
    """Validate declared paths that should exist after installation."""
    if template_mode:
        return []
    errors: list[str] = []
    docs = require_mapping(config, "docs")
    scripts = require_mapping(config, "scripts")
    stage = require_mapping(config, "stage")
    current_stage = stage.get("current", 0)
    if not isinstance(current_stage, int):
        current_stage = 0
    repo_root = root.resolve()
    resolved_root = str(repo_root)

    def validate_resolved_declared_path(path_value: str, field_name: str) -> None:
        if not is_repo_relative_path(path_value):
            errors.append(f"{field_name} must be repository-relative: {path_value}")
            return
        resolved_path = (root / path_value).resolve()
        if os.path.commonpath([str(resolved_path), resolved_root]) != resolved_root:
            errors.append(
                f"{field_name} resolves outside repository root: {path_value}"
            )
        elif not resolved_path.exists():
            errors.append(f"{field_name} is missing: {path_value}")

    declared_paths = {
        "docs.config": docs.get("config"),
        "docs.entrypoint": docs.get("entrypoint"),
        "docs.harnessRoot": docs.get("harnessRoot"),
        "docs.guardrails": docs.get("guardrails"),
        "docs.knownViolations": docs.get("knownViolations"),
        "docs.readiness": docs.get("readiness"),
        "docs.updates": docs.get("updates"),
        "docs.architecture": docs.get("architecture"),
        "scripts.root": scripts.get("root"),
        "scripts.validator": scripts.get("validator"),
        "scripts.wrapper": scripts.get("wrapper"),
    }
    for field_name, path_value in declared_paths.items():
        if isinstance(path_value, str):
            validate_resolved_declared_path(path_value, field_name)

    ci = require_mapping(config, "ci")
    provider = ci.get("provider")
    if (
        current_stage >= 3
        and provider == "github-actions"
        and isinstance(ci.get("githubWorkflow"), str)
    ):
        validate_resolved_declared_path(
            ci["githubWorkflow"], "declared GitHub Actions workflow"
        )
    if (
        current_stage >= 3
        and provider == "gitlab-ci"
        and isinstance(ci.get("gitlabConfig"), str)
    ):
        validate_resolved_declared_path(ci["gitlabConfig"], "declared GitLab CI config")

    hooks = require_mapping(config, "hooks")
    if current_stage >= 2 and hooks.get("enabled") is True:
        for key in ("path", "commitMsg", "prePush"):
            path_value = hooks.get(key)
            if isinstance(path_value, str):
                validate_resolved_declared_path(path_value, f"declared hook {key}")
    return errors


def validate_symlinks(
    config: dict[str, Any], root: Path, template_mode: bool
) -> list[str]:
    """Validate harness symlink conventions when symlinks are present."""
    if template_mode:
        return []
    errors: list[str] = []
    docs = require_mapping(config, "docs")
    stage = require_mapping(config, "stage")
    current_stage = stage.get("current", 0)
    if not isinstance(current_stage, int) or current_stage < 1:
        return []
    compatibility_symlink = docs.get("compatibilitySymlink")
    compatibility_target = docs.get("compatibilitySymlinkTarget")
    compatibility_status = docs.get("compatibilitySymlinkStatus", "required")
    if compatibility_symlink != "AGENTS.md" or compatibility_target != "CLAUDE.md":
        errors.append("compatibility symlink must be AGENTS.md -> CLAUDE.md")
        return errors
    if compatibility_status in ("pending-conflict", "not-applicable"):
        return []
    compatibility_path = root / compatibility_symlink
    if not compatibility_path.is_symlink():
        if compatibility_path.exists():
            errors.append(f"{compatibility_symlink} exists but is not a symlink")
        else:
            errors.append(f"{compatibility_symlink} must be present as a symlink")
    elif os.readlink(compatibility_path) != str(compatibility_target):
        errors.append(f"{compatibility_symlink} must point to {compatibility_target}")
    return errors


def main() -> int:
    """Run command-line validation.

    :return: 0 when validation passes, 1 on any failure.
    """
    parser = argparse.ArgumentParser(
        description="Validate harness-engineering config and installed target-repo paths."
    )
    parser.add_argument(
        "--config",
        default=DEFAULT_CONFIG_PATH,
        help="Explicit config override for tests, migrations, or unusual repositories.",
    )
    parser.add_argument(
        "--root",
        default=".",
        help="Target repository root. Defaults to the current directory.",
    )
    parser.add_argument(
        "--template",
        action="store_true",
        help="Validate config shape only, without requiring installed paths.",
    )
    args = parser.parse_args()
    root = Path(args.root).resolve()
    config_path = Path(args.config)
    if not config_path.is_absolute():
        config_path = root / config_path
    legacy_config_path = root / ".harness-engineering.json"
    if not config_path.exists():
        if args.config == DEFAULT_CONFIG_PATH and legacy_config_path.exists():
            print(
                f"harness validation failed: root .harness-engineering.json is manual migration input only; move durable values to {DEFAULT_CONFIG_PATH}",
                file=sys.stderr,
            )
            return 1
        print(
            f"harness validation failed: config file is missing: {config_path}",
            file=sys.stderr,
        )
        return 1
    try:
        config = load_config(config_path)
        errors = validate_shape(config)
        if not errors:
            errors.extend(validate_paths(config, root, args.template))
            errors.extend(validate_symlinks(config, root, args.template))
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"harness validation failed: {error}", file=sys.stderr)
        return 1
    if errors:
        for error in errors:
            print(f"harness validation failed: {error}", file=sys.stderr)
        return 1
    print("harness validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
