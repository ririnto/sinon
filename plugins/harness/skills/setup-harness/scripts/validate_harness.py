#!/usr/bin/env -S uv run
# /// script
# dependencies = []
# ///

"""Validate a target repository's harness configuration."""

from __future__ import annotations

import argparse
import json
import os
import re
import shlex
import sys
from pathlib import Path
from typing import Any


CONTRACT_VERSION = 1
DEFAULT_CONFIG_PATH = "docs/harness-engineering/harness-engineering.json"
SKILLS_ALIAS_PATH = ".agents/skills"
SKILLS_ALIAS_TARGET = "../.claude/skills"
REQUIRED_TOP_LEVEL = (
    "contractVersion",
    "profile",
    "paths",
    "docs",
    "commands",
    "gates",
    "evidence",
    "packs",
    "absence",
)
REQUIRED_PROFILE = ("name", "stage", "targetStage", "level")
REQUIRED_PATHS = (
    "harnessRoot",
    "config",
    "scriptsRoot",
    "validator",
    "wrapper",
    "agentContextAlias",
    "agentContextAliasTarget",
    "githubWorkflow",
    "gitlabConfig",
    "hooksRoot",
    "commitMsgHook",
    "prePushHook",
)
REQUIRED_DOCS = ("agentContext", "architecture", "guardrails", "updates")
REQUIRED_COMMANDS = ("required", "optional")
REQUIRED_GATES = ("ci", "hooks")
REQUIRED_CI_GATE = ("provider", "stage", "branches")
REQUIRED_HOOKS_GATE = ("enabled", "stage")
REQUIRED_EVIDENCE = ("readiness", "knownViolations", "generatedDocs")
REQUIRED_ABSENCE = ("agentContextAlias", "ci", "hooks", "packs")
REQUIRED_CI_PROVIDERS = ("github-actions", "gitlab-ci", "none")
REQUIRED_BRANCH_SETS = ("push", "pullRequest", "mergeRequest")
ENFORCEMENT_LEVELS = ("warn", "error")
ABSENCE_STATES = ("required", "optional", "disabled", "not-applicable", "pending-conflict")
REQUIRED_ALIAS_STATES = ("required", "optional", "pending-conflict", "not-applicable")
ACTIVE_ABSENCE_STATES = ("required", "optional")
INACTIVE_ABSENCE_STATES = ("disabled", "not-applicable", "pending-conflict")
PACK_NAME_PATTERN = re.compile(r"^[a-z0-9][a-z0-9-]*$")
UNSAFE_COMMAND_CHARS = frozenset(";|&`$><\n")
TRUSTED_COMMAND_FORMS = (
    "sh scripts/harness/validate_harness.sh",
    "bun scripts/harness/validate-harness.mjs",
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


def validate_required_fields(
    data: dict[str, Any], required_fields: tuple[str, ...], section_name: str
) -> list[str]:
    """Validate that a mapping includes each required field."""
    errors: list[str] = []
    for key in required_fields:
        if key not in data:
            errors.append(f"missing {section_name} field: {key}")
    return errors


def get_dotted_value(data: dict[str, Any], dotted_field: str) -> Any:
    """Return a dotted field value from the config, or None when absent."""
    current: Any = data
    for segment in dotted_field.split("."):
        if not isinstance(current, dict) or segment not in current:
            return None
        current = current[segment]
    return current


def get_current_stage(config: dict[str, Any]) -> int:
    """Return the active profile stage, falling back to zero for invalid data."""
    profile = config.get("profile")
    if not isinstance(profile, dict):
        return 0
    current_stage = profile.get("stage", 0)
    if not isinstance(current_stage, int):
        return 0
    return current_stage


def validate_string_list(
    values: Any, field_name: str, allow_empty: bool = False
) -> list[str]:
    """Validate a list of strings."""
    if not isinstance(values, list):
        return [f"{field_name} must be a list of strings"]
    if not allow_empty and not values:
        return [f"{field_name} must be a non-empty list of strings"]
    if not all(isinstance(item, str) and item for item in values):
        return [f"{field_name} must contain only non-empty strings"]
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


def validate_command_list(
    commands: Any, field_name: str, allow_empty: bool = False
) -> list[str]:
    """Validate configured shell command strings without executing them."""
    if not isinstance(commands, list):
        return [f"{field_name} must be a list of commands"]
    if not allow_empty and not commands:
        return [f"{field_name} must contain at least one command"]
    errors: list[str] = []
    for index, command in enumerate(commands):
        errors.extend(validate_command(command, f"{field_name}[{index}]"))
    return errors


def validate_path_mapping(
    mapping: dict[str, Any], field_names: tuple[str, ...], section_name: str
) -> list[str]:
    """Validate a mapping whose declared values are repository-relative paths."""
    errors: list[str] = []
    for field_name in field_names:
        if field_name in mapping:
            errors.extend(
                validate_repo_relative_path(
                    mapping[field_name], f"{section_name}.{field_name}"
                )
            )
    return errors


def validate_contract_version(config: dict[str, Any]) -> list[str]:
    """Validate the explicit machine-contract version."""
    if config.get("contractVersion") != CONTRACT_VERSION:
        return [f"contractVersion must be {CONTRACT_VERSION}"]
    return []


def validate_profile(profile: dict[str, Any]) -> list[str]:
    """Validate the adoption profile section."""
    errors = validate_required_fields(profile, REQUIRED_PROFILE, "profile")
    name = profile.get("name")
    stage = profile.get("stage")
    target_stage = profile.get("targetStage")
    if not isinstance(name, str) or not name:
        errors.append("profile.name must be a non-empty string")
    if not isinstance(stage, int) or stage < 0:
        errors.append("profile.stage must be a non-negative integer")
    if not isinstance(target_stage, int) or target_stage < 0:
        errors.append("profile.targetStage must be a non-negative integer")
    if isinstance(stage, int) and isinstance(target_stage, int) and stage > target_stage:
        errors.append("profile.stage must be less than or equal to profile.targetStage")
    if profile.get("level") not in ENFORCEMENT_LEVELS:
        errors.append(f"profile.level must be one of: {', '.join(ENFORCEMENT_LEVELS)}")
    return errors


def validate_paths_section(paths: dict[str, Any]) -> list[str]:
    """Validate the machine-owned path registry."""
    errors = validate_required_fields(paths, REQUIRED_PATHS, "paths")
    errors.extend(validate_path_mapping(paths, REQUIRED_PATHS, "paths"))
    if paths.get("config") != DEFAULT_CONFIG_PATH:
        errors.append(f"paths.config must be {DEFAULT_CONFIG_PATH}")
    if paths.get("harnessRoot") != "docs/harness-engineering":
        errors.append("paths.harnessRoot must be docs/harness-engineering")
    if paths.get("agentContextAlias") != "AGENTS.md":
        errors.append("paths.agentContextAlias must be AGENTS.md")
    if paths.get("agentContextAliasTarget") != "CLAUDE.md":
        errors.append("paths.agentContextAliasTarget must be CLAUDE.md")
    return errors


def validate_docs_section(docs: dict[str, Any]) -> list[str]:
    """Validate the Markdown policy and workflow document roles."""
    errors = validate_required_fields(docs, REQUIRED_DOCS, "docs")
    errors.extend(validate_path_mapping(docs, REQUIRED_DOCS, "docs"))
    if docs.get("agentContext") != "CLAUDE.md":
        errors.append("docs.agentContext must be CLAUDE.md")
    if docs.get("architecture") != "ARCHITECTURE.md":
        errors.append("docs.architecture must be ARCHITECTURE.md")
    return errors


def validate_commands_section(commands: dict[str, Any]) -> list[str]:
    """Validate the configured command contract."""
    errors = validate_required_fields(commands, REQUIRED_COMMANDS, "commands")
    required = commands.get("required")
    errors.extend(validate_command_list(required, "commands.required"))
    errors.extend(
        validate_command_list(commands.get("optional"), "commands.optional", allow_empty=True)
    )
    if isinstance(required, list) and "sh scripts/harness/validate_harness.sh" not in required:
        errors.append(
            "commands.required must include sh scripts/harness/validate_harness.sh"
        )
    return errors


def validate_gates_section(gates: dict[str, Any]) -> list[str]:
    """Validate CI and hook gate metadata."""
    errors = validate_required_fields(gates, REQUIRED_GATES, "gates")
    ci = gates.get("ci")
    hooks = gates.get("hooks")
    if not isinstance(ci, dict):
        errors.append("gates.ci must be an object")
    else:
        errors.extend(validate_required_fields(ci, REQUIRED_CI_GATE, "gates.ci"))
        provider = ci.get("provider")
        if provider not in REQUIRED_CI_PROVIDERS:
            errors.append(
                f"gates.ci.provider must be one of: {', '.join(REQUIRED_CI_PROVIDERS)}"
            )
        ci_stage = ci.get("stage")
        if not isinstance(ci_stage, int) or ci_stage < 0:
            errors.append("gates.ci.stage must be a non-negative integer")
        branches = ci.get("branches")
        if isinstance(branches, dict):
            for key in REQUIRED_BRANCH_SETS:
                errors.extend(validate_string_list(branches.get(key), f"gates.ci.branches.{key}"))
        elif branches is not None:
            errors.append("gates.ci.branches must be an object")
    if not isinstance(hooks, dict):
        errors.append("gates.hooks must be an object")
    else:
        errors.extend(validate_required_fields(hooks, REQUIRED_HOOKS_GATE, "gates.hooks"))
        if not isinstance(hooks.get("enabled"), bool):
            errors.append("gates.hooks.enabled must be a boolean")
        hooks_stage = hooks.get("stage")
        if not isinstance(hooks_stage, int) or hooks_stage < 0:
            errors.append("gates.hooks.stage must be a non-negative integer")
    return errors


def validate_evidence_section(evidence: dict[str, Any]) -> list[str]:
    """Validate evidence ledger and generated-doc path roles."""
    errors = validate_required_fields(evidence, REQUIRED_EVIDENCE, "evidence")
    errors.extend(validate_path_mapping(evidence, REQUIRED_EVIDENCE, "evidence"))
    return errors


def validate_pack(pack: Any, pack_name: str) -> list[str]:
    """Validate one optional implementation pack."""
    if not PACK_NAME_PATTERN.match(pack_name):
        return [f"packs.{pack_name} name must use lowercase letters, digits, or hyphens"]
    if not isinstance(pack, dict):
        return [f"packs.{pack_name} must be an object"]
    errors: list[str] = []
    if not isinstance(pack.get("enabled"), bool):
        errors.append(f"packs.{pack_name}.enabled must be a boolean")
    errors.extend(
        validate_command_list(
            pack.get("commands"), f"packs.{pack_name}.commands", allow_empty=True
        )
    )
    paths = pack.get("paths")
    path_errors = validate_string_list(paths, f"packs.{pack_name}.paths", allow_empty=True)
    if path_errors:
        errors.extend(path_errors)
    elif isinstance(paths, list):
        for index, path_value in enumerate(paths):
            errors.extend(
                validate_repo_relative_path(path_value, f"packs.{pack_name}.paths[{index}]")
            )
    if pack.get("enabled") is True:
        if not pack.get("commands"):
            errors.append(f"packs.{pack_name}.commands must be non-empty when enabled")
        if not pack.get("paths"):
            errors.append(f"packs.{pack_name}.paths must be non-empty when enabled")
    return errors


def validate_packs_section(packs: dict[str, Any]) -> list[str]:
    """Validate optional implementation packs without requiring their use."""
    errors: list[str] = []
    for pack_name, pack in packs.items():
        errors.extend(validate_pack(pack, pack_name))
    return errors


def validate_absence_section(
    absence: dict[str, Any], gates: dict[str, Any], packs: dict[str, Any]
) -> list[str]:
    """Validate explicit absence semantics for optional surfaces."""
    errors = validate_required_fields(absence, REQUIRED_ABSENCE, "absence")
    for key in REQUIRED_ABSENCE:
        if key in absence and absence[key] not in ABSENCE_STATES:
            errors.append(f"absence.{key} must be one of: {', '.join(ABSENCE_STATES)}")
    alias_state = absence.get("agentContextAlias")
    if alias_state not in REQUIRED_ALIAS_STATES:
        errors.append(
            f"absence.agentContextAlias must be one of: {', '.join(REQUIRED_ALIAS_STATES)}"
        )
    ci = gates.get("ci")
    if isinstance(ci, dict):
        provider = ci.get("provider")
        ci_state = absence.get("ci")
        if provider == "none" and ci_state not in INACTIVE_ABSENCE_STATES:
            errors.append("absence.ci must mark CI absent when gates.ci.provider is none")
        if provider != "none" and ci_state not in ACTIVE_ABSENCE_STATES:
            errors.append("absence.ci must be required or optional when CI provider is active")
    hooks = gates.get("hooks")
    if isinstance(hooks, dict):
        hooks_state = absence.get("hooks")
        if hooks.get("enabled") is True and hooks_state not in ACTIVE_ABSENCE_STATES:
            errors.append("absence.hooks must be required or optional when hooks are enabled")
        if hooks.get("enabled") is False and hooks_state not in INACTIVE_ABSENCE_STATES:
            errors.append("absence.hooks must mark hooks absent when hooks are disabled")
    enabled_packs = any(
        isinstance(pack, dict) and pack.get("enabled") is True for pack in packs.values()
    )
    packs_state = absence.get("packs")
    if enabled_packs and packs_state not in ACTIVE_ABSENCE_STATES:
        errors.append("absence.packs must be required or optional when any pack is enabled")
    if not enabled_packs and packs_state not in INACTIVE_ABSENCE_STATES:
        errors.append("absence.packs must mark packs absent when all packs are disabled")
    return errors


def validate_shape(config: dict[str, Any]) -> list[str]:
    """Validate the versioned harness config contract."""
    errors: list[str] = []
    for key in REQUIRED_TOP_LEVEL:
        if key not in config:
            errors.append(f"missing top-level field: {key}")
    for key in config:
        if key not in REQUIRED_TOP_LEVEL:
            errors.append(f"unexpected top-level field: {key}")
    if errors:
        return errors
    errors.extend(validate_contract_version(config))
    profile = require_mapping(config, "profile")
    paths = require_mapping(config, "paths")
    docs = require_mapping(config, "docs")
    commands = require_mapping(config, "commands")
    gates = require_mapping(config, "gates")
    evidence = require_mapping(config, "evidence")
    packs = require_mapping(config, "packs")
    absence = require_mapping(config, "absence")
    errors.extend(validate_profile(profile))
    errors.extend(validate_paths_section(paths))
    errors.extend(validate_docs_section(docs))
    errors.extend(validate_commands_section(commands))
    errors.extend(validate_gates_section(gates))
    errors.extend(validate_evidence_section(evidence))
    errors.extend(validate_packs_section(packs))
    errors.extend(validate_absence_section(absence, gates, packs))
    return errors


def validate_resolved_declared_path(
    root: Path, resolved_root: str, path_value: Any, field_name: str, errors: list[str]
) -> None:
    """Validate that a configured path resolves inside the repo and exists."""
    if not isinstance(path_value, str):
        return
    if not is_repo_relative_path(path_value):
        errors.append(f"{field_name} must be repository-relative: {path_value}")
        return
    resolved_path = (root / path_value).resolve()
    if os.path.commonpath([str(resolved_path), resolved_root]) != resolved_root:
        errors.append(f"{field_name} resolves outside repository root: {path_value}")
    elif not resolved_path.exists():
        errors.append(f"{field_name} is missing: {path_value}")


def validate_required_installed_paths(
    config: dict[str, Any], root: Path, resolved_root: str, errors: list[str]
) -> None:
    """Validate paths that are required for every installed harness."""
    required_fields = (
        "paths.harnessRoot",
        "paths.config",
        "paths.scriptsRoot",
        "paths.validator",
        "paths.wrapper",
        "docs.agentContext",
        "docs.architecture",
        "docs.guardrails",
        "docs.updates",
        "evidence.readiness",
        "evidence.knownViolations",
    )
    for field_name in required_fields:
        validate_resolved_declared_path(
            root, resolved_root, get_dotted_value(config, field_name), field_name, errors
        )


def validate_gate_paths(
    config: dict[str, Any], root: Path, resolved_root: str, errors: list[str]
) -> None:
    """Validate paths that become required when gates are active."""
    gates = require_mapping(config, "gates")
    absence = require_mapping(config, "absence")
    paths = require_mapping(config, "paths")
    current_stage = get_current_stage(config)
    ci = gates.get("ci")
    if isinstance(ci, dict) and absence.get("ci") in ACTIVE_ABSENCE_STATES:
        ci_stage = ci.get("stage", 0)
        if isinstance(ci_stage, int) and current_stage >= ci_stage:
            if ci.get("provider") == "github-actions":
                validate_resolved_declared_path(
                    root, resolved_root, paths.get("githubWorkflow"), "paths.githubWorkflow", errors
                )
            if ci.get("provider") == "gitlab-ci":
                validate_resolved_declared_path(
                    root, resolved_root, paths.get("gitlabConfig"), "paths.gitlabConfig", errors
                )
    hooks = gates.get("hooks")
    if isinstance(hooks, dict) and absence.get("hooks") in ACTIVE_ABSENCE_STATES:
        hooks_stage = hooks.get("stage", 0)
        if hooks.get("enabled") is True and isinstance(hooks_stage, int) and current_stage >= hooks_stage:
            for key in ("hooksRoot", "commitMsgHook", "prePushHook"):
                validate_resolved_declared_path(
                    root, resolved_root, paths.get(key), f"paths.{key}", errors
                )


def validate_pack_paths(
    config: dict[str, Any], root: Path, resolved_root: str, errors: list[str]
) -> None:
    """Validate paths for enabled optional packs."""
    packs = require_mapping(config, "packs")
    absence = require_mapping(config, "absence")
    if absence.get("packs") not in ACTIVE_ABSENCE_STATES:
        return
    for pack_name, pack in packs.items():
        if not isinstance(pack, dict) or pack.get("enabled") is not True:
            continue
        paths = pack.get("paths")
        if not isinstance(paths, list):
            continue
        for index, path_value in enumerate(paths):
            validate_resolved_declared_path(
                root,
                resolved_root,
                path_value,
                f"packs.{pack_name}.paths[{index}]",
                errors,
            )


def validate_paths(
    config: dict[str, Any], root: Path, template_mode: bool
) -> list[str]:
    """Validate declared paths that should exist after installation."""
    if template_mode:
        return []
    errors: list[str] = []
    repo_root = root.resolve()
    resolved_root = str(repo_root)
    validate_required_installed_paths(config, root, resolved_root, errors)
    validate_gate_paths(config, root, resolved_root, errors)
    validate_pack_paths(config, root, resolved_root, errors)
    return errors


def validate_symlinks(
    config: dict[str, Any], root: Path, template_mode: bool
) -> list[str]:
    """Validate harness symlink conventions when symlinks are present."""
    if template_mode or get_current_stage(config) < 1:
        return []
    errors: list[str] = []
    paths = require_mapping(config, "paths")
    absence = require_mapping(config, "absence")
    alias_state = absence.get("agentContextAlias")
    if alias_state not in ("pending-conflict", "not-applicable"):
        compatibility_symlink = paths.get("agentContextAlias")
        compatibility_target = paths.get("agentContextAliasTarget")
        if isinstance(compatibility_symlink, str) and isinstance(compatibility_target, str):
            compatibility_path = root / compatibility_symlink
            if alias_state == "required" and not compatibility_path.exists():
                errors.append(f"{compatibility_symlink} must be present as a symlink")
            elif compatibility_path.exists():
                if not compatibility_path.is_symlink():
                    errors.append(f"{compatibility_symlink} exists but is not a symlink")
                elif os.readlink(compatibility_path) != compatibility_target:
                    errors.append(f"{compatibility_symlink} must point to {compatibility_target}")
    skills_alias_path = root / SKILLS_ALIAS_PATH
    if skills_alias_path.is_symlink():
        if os.readlink(skills_alias_path) != SKILLS_ALIAS_TARGET:
            errors.append(f"{SKILLS_ALIAS_PATH} must point to {SKILLS_ALIAS_TARGET}")
    elif skills_alias_path.exists():
        errors.append(f"{SKILLS_ALIAS_PATH} exists but is not a symlink")
    return errors


def main() -> int:
    """Run command-line validation.

    :return: 0 when validation passes, 1 on any failure.
    """
    parser = argparse.ArgumentParser(
        description="Validate harness config and installed target-repo paths."
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
    if not config_path.exists():
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
