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
REQUIRED_TOP_LEVEL = ("projectProfile", "stage", "docs", "scripts", "sourceRoots", "schemaSources", "generatedArtifacts", "generatedPolicy", "projectProfiles", "layerModel", "checks", "enforcement", "readiness", "ci", "hooks", "gradle", "node", "standalone", "userRequirementRules", "yamlStyle")
REQUIRED_STAGE = ("current", "target", "mode", "exitGate")
REQUIRED_DOCS = ("harnessRoot", "config", "guardrails", "knownViolations", "readiness", "updates", "entrypoint", "compatibilitySymlink", "compatibilitySymlinkTarget", "compatibilitySymlinkStatus", "root", "architecture", "design", "frontend", "plansDoc", "productSense", "reliability", "security", "quality", "designDocs", "productSpecs", "plans", "generated", "references")
REQUIRED_SYMLINK_STATUSES = ("required", "pending-conflict", "not-applicable")
REQUIRED_SCRIPTS = ("root", "validator", "wrapper")
REQUIRED_CI_PROVIDERS = ("github-actions", "gitlab-ci", "none")
REQUIRED_HOOKS = ("enabled", "path", "commitMsg", "prePush")
REQUIRED_GRADLE = ("enabled", "buildFile", "taskName")
REQUIRED_NODE = ("enabled", "packageFile", "packageManager", "scriptName", "installCommand", "runCommand", "workspaceMode")
REQUIRED_STANDALONE = ("enabled", "commands", "entrypoint", "description")
REQUIRED_ENFORCEMENT = ("defaultSeverity", "allowedSeverities", "settings")
REQUIRED_YAML_STYLE = ("literalMultiline", "foldedMultiline")
REQUIRED_READINESS = ("maturityLevel", "minimumBlockingLevel", "scorePath", "knownViolations", "ratchet", "freshness")
UNSAFE_COMMAND_CHARS = frozenset(";|&`$><\n")
TRUSTED_COMMAND_FORMS = [
    "sh scripts/harness/validate_harness.sh",
    "uvx --offline --no-python-downloads python scripts/harness/validate_harness.py --config docs/harness-engineering/harness-engineering.json",
    "./gradlew harnessCheck",
    "npm run harness:validate",
    "pnpm run harness:validate",
    "yarn run harness:validate",
    "bun run harness:validate",
]


def is_repo_relative_path(path_value: str) -> bool:
    """Return whether a config path stays relative to the repository."""
    path = Path(path_value)
    return not path.is_absolute() and ".." not in path.parts


def validate_repo_relative_path(path_value: Any, field_name: str) -> list[str]:
    """Validate one config path field without checking existence."""
    if not isinstance(path_value, str):
        return [f"{field_name} must be a string path"]
    if not is_repo_relative_path(path_value):
        return [f"{field_name} must be a repository-relative path without '..': {path_value}"]
    return []


def repo_path(root: Path, path_value: str) -> Path:
    """Resolve a checked repository-relative path under root."""
    return root / path_value


def validate_path_list(paths: Any, field_name: str) -> list[str]:
    """Validate a list of repository-relative path strings."""
    errors: list[str] = []
    if not isinstance(paths, list) or not all(isinstance(item, str) for item in paths):
        return [f"{field_name} must be a list of paths"]
    for path_value in paths:
        errors.extend(validate_repo_relative_path(path_value, field_name))
    return errors


def validate_string_list(values: Any, field_name: str) -> list[str]:
    """Validate a list of non-empty strings."""
    if not isinstance(values, list) or not values or not all(isinstance(item, str) and item for item in values):
        return [f"{field_name} must be a non-empty list of strings"]
    return []


def validate_severity(value: Any, field_name: str) -> list[str]:
    """Validate one warn-or-error enforcement level."""
    if value not in ("warn", "error"):
        return [f"{field_name} must be warn or error"]
    return []


def command_is_allowed(command: str, trusted_forms: list[str]) -> bool:
    """Return whether a configured command matches a trusted argv form."""
    command_parts = shlex.split(command)
    return any(command_parts == shlex.split(trusted_form) for trusted_form in trusted_forms)


def validate_command(command: Any, trusted_forms: list[str], field_name: str) -> list[str]:
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
    if not command_is_allowed(command, trusted_forms):
        return [f"{field_name} command is not an allowed argv form: {command}"]
    return []


def validate_command_list(commands: Any, trusted_forms: list[str], field_name: str) -> list[str]:
    """Validate configured shell command strings without executing them."""
    errors: list[str] = []
    if not isinstance(commands, list) or not all(isinstance(item, str) for item in commands):
        return [f"{field_name} must be a list of commands"]
    for command in commands:
        errors.extend(validate_command(command, trusted_forms, field_name))
    return errors


def validate_generated_policy(config: dict[str, Any]) -> list[str]:
    """Validate schema-source and generated-artifact policy fields."""
    errors: list[str] = []
    generated_policy = require_mapping(config, "generatedPolicy")
    generated_root = generated_policy.get("root")
    if not isinstance(generated_root, str):
        errors.append("generatedPolicy.root must be a string path")
        generated_root = "docs/generated"
    else:
        errors.extend(validate_repo_relative_path(generated_root, "generatedPolicy.root"))
    source_schemas_stay_out = generated_policy.get("sourceSchemasStayOutsideGeneratedDocs") is True
    requires_regenerate = generated_policy.get("requiresRegenerationCommand") is True
    requires_provenance = generated_policy.get("requiresProvenance") is True
    for index, source_group in enumerate(config.get("schemaSources", [])):
        if not isinstance(source_group, dict):
            errors.append(f"schemaSources[{index}] must be an object")
            continue
        paths = source_group.get("paths")
        if not isinstance(paths, list) or not all(isinstance(item, str) for item in paths):
            errors.append(f"schemaSources[{index}].paths must be a list of paths")
            continue
        for path_value in paths:
            errors.extend(validate_repo_relative_path(path_value, f"schemaSources[{index}].paths"))
            if source_schemas_stay_out and path_value.startswith(str(generated_root).rstrip("/") + "/"):
                errors.append(f"schema source must not live under {generated_root}: {path_value}")
    for index, artifact in enumerate(config.get("generatedArtifacts", [])):
        if not isinstance(artifact, dict):
            errors.append(f"generatedArtifacts[{index}] must be an object")
            continue
        errors.extend(validate_repo_relative_path(artifact.get("path"), f"generatedArtifacts[{index}].path"))
        if requires_provenance:
            sources = artifact.get("sources")
            if not isinstance(sources, list) or not all(isinstance(item, str) for item in sources) or not sources:
                errors.append(f"generatedArtifacts[{index}].sources must list source paths")
            else:
                for source_path in sources:
                    errors.extend(validate_repo_relative_path(source_path, f"generatedArtifacts[{index}].sources"))
                    if source_path.startswith(str(generated_root).rstrip("/") + "/"):
                        errors.append(f"generated artifact source must not live under {generated_root}: {source_path}")
        if requires_regenerate:
            errors.extend(
                validate_command(
                    artifact.get("regenerate"),
                    TRUSTED_COMMAND_FORMS,
                    f"generatedArtifacts[{index}].regenerate",
                )
            )
    return errors


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


def validate_shape(config: dict[str, Any]) -> list[str]:
    """Validate required harness config fields."""
    errors: list[str] = []
    for key in REQUIRED_TOP_LEVEL:
        if key not in config:
            errors.append(f"missing top-level field: {key}")
    project_profile = config.get("projectProfile", {})
    if isinstance(project_profile, dict) and not isinstance(project_profile.get("name"), str):
        errors.append("projectProfile.name must be a string")
    stage = config.get("stage", {})
    if isinstance(stage, dict):
        for key in REQUIRED_STAGE:
            if key not in stage:
                errors.append(f"missing stage field: {key}")
    docs = config.get("docs", {})
    if isinstance(docs, dict):
        for key in REQUIRED_DOCS:
            if key not in docs:
                errors.append(f"missing docs field: {key}")
        if docs.get("config") != DEFAULT_CONFIG_PATH:
            errors.append(f"docs.config must be {DEFAULT_CONFIG_PATH}")
        if docs.get("entrypoint") != "CLAUDE.md":
            errors.append("docs.entrypoint must be CLAUDE.md")
        if docs.get("compatibilitySymlink") != "AGENTS.md" or docs.get("compatibilitySymlinkTarget") != "CLAUDE.md":
            errors.append("docs compatibility symlink must be AGENTS.md -> CLAUDE.md")
        if docs.get("compatibilitySymlinkStatus") not in REQUIRED_SYMLINK_STATUSES:
            errors.append(f"docs.compatibilitySymlinkStatus must be one of: {', '.join(REQUIRED_SYMLINK_STATUSES)}")
        for key in ("harnessRoot", "config", "guardrails", "knownViolations", "readiness", "updates", "entrypoint", "compatibilitySymlink", "compatibilitySymlinkTarget", "root", "architecture", "design", "frontend", "plansDoc", "productSense", "reliability", "security", "quality", "designDocs", "productSpecs", "generated", "references"):
            if key in docs:
                errors.extend(validate_repo_relative_path(docs[key], f"docs.{key}"))
        plans = docs.get("plans", {})
        if isinstance(plans, dict):
            for key in ("active", "completed", "debt"):
                if key in plans:
                    errors.extend(validate_repo_relative_path(plans[key], f"docs.plans.{key}"))
    scripts = config.get("scripts", {})
    if isinstance(scripts, dict):
        for key in REQUIRED_SCRIPTS:
            if key not in scripts:
                errors.append(f"missing scripts field: {key}")
    checks = config.get("checks", {})
    if isinstance(checks, dict):
        informational_forms = checks.get("allowedCommandPrefixes")
        if not isinstance(informational_forms, list) or not all(isinstance(item, str) for item in informational_forms):
            errors.append("checks.allowedCommandPrefixes must be a list of command forms")
        trusted_prefixes = list(TRUSTED_COMMAND_FORMS)
        errors.extend(validate_command_list(checks.get("requiredCommands", []), trusted_prefixes, "checks.requiredCommands"))
        errors.extend(validate_command_list(checks.get("optionalCommands", []), trusted_prefixes, "checks.optionalCommands"))
    source_roots = config.get("sourceRoots")
    if not isinstance(source_roots, list) or not all(isinstance(item, str) for item in source_roots):
        errors.append("sourceRoots must be a list of paths")
    elif any(not is_repo_relative_path(item) for item in source_roots):
        errors.append("sourceRoots must contain repository-relative paths without '..'")
    schema_sources = config.get("schemaSources")
    if not isinstance(schema_sources, list):
        errors.append("schemaSources must be a list")
    generated_artifacts = config.get("generatedArtifacts")
    if not isinstance(generated_artifacts, list):
        errors.append("generatedArtifacts must be a list")
    generated_policy = config.get("generatedPolicy", {})
    if not isinstance(generated_policy, dict):
        errors.append("generatedPolicy must be an object")
    elif generated_policy.get("sourceSchemasStayOutsideGeneratedDocs") is not True:
        errors.append("generatedPolicy.sourceSchemasStayOutsideGeneratedDocs must be true")
    project_profiles = config.get("projectProfiles", {})
    if not isinstance(project_profiles, dict):
        errors.append("projectProfiles must be an object")
    else:
        for profile_name, profile in project_profiles.items():
            if not isinstance(profile, dict):
                errors.append(f"projectProfiles.{profile_name} must be an object")
                continue
            for key in ("sourceRoots", "testRoots", "schemaSources", "generatedArtifacts"):
                if key in profile:
                    errors.extend(validate_path_list(profile[key], f"projectProfiles.{profile_name}.{key}"))
            if "buildIntegration" in profile:
                errors.extend(validate_string_list(profile["buildIntegration"], f"projectProfiles.{profile_name}.buildIntegration"))
    layer_model = config.get("layerModel", {})
    if isinstance(layer_model, dict):
        layers = layer_model.get("layers")
        allowed_edges = layer_model.get("allowedEdges")
        roots = layer_model.get("roots", [])
        if not isinstance(layers, list) or not all(isinstance(item, str) for item in layers):
            errors.append("layerModel.layers must be a list of layer names")
        if not isinstance(allowed_edges, dict):
            errors.append("layerModel.allowedEdges must be an object")
        if "roots" in layer_model:
            errors.extend(validate_path_list(roots, "layerModel.roots"))
        cross_cutting = layer_model.get("crossCutting", {})
        if isinstance(cross_cutting, dict) and "path" in cross_cutting:
            errors.extend(validate_repo_relative_path(cross_cutting["path"], "layerModel.crossCutting.path"))
    scoped_path_rules = config.get("scopedPathRules", [])
    if isinstance(scoped_path_rules, list):
        for index, rule in enumerate(scoped_path_rules):
            if isinstance(rule, dict) and "path" in rule:
                errors.extend(validate_repo_relative_path(rule["path"], f"scopedPathRules[{index}].path"))
    ci = config.get("ci", {})
    if isinstance(ci, dict) and ci.get("provider") not in REQUIRED_CI_PROVIDERS:
        errors.append(f"ci.provider must be one of: {', '.join(REQUIRED_CI_PROVIDERS)}")
    if isinstance(ci, dict):
        branches = ci.get("branches", {})
        if not isinstance(branches, dict):
            errors.append("ci.branches must be an object")
        else:
            errors.extend(validate_string_list(branches.get("push", []), "ci.branches.push"))
            errors.extend(validate_string_list(branches.get("pullRequest", []), "ci.branches.pullRequest"))
            errors.extend(validate_string_list(branches.get("mergeRequest", []), "ci.branches.mergeRequest"))
    readiness = config.get("readiness", {})
    if isinstance(readiness, dict):
        for key in REQUIRED_READINESS:
            if key not in readiness:
                errors.append(f"missing readiness field: {key}")
        ratchet = readiness.get("ratchet", {})
        if not isinstance(ratchet, dict):
            errors.append("readiness.ratchet must be an object")
        else:
            if "mode" in ratchet:
                errors.extend(validate_severity(ratchet["mode"], "readiness.ratchet.mode"))
            if "newViolations" in ratchet:
                errors.extend(validate_severity(ratchet["newViolations"], "readiness.ratchet.newViolations"))
        freshness = readiness.get("freshness", {})
        if not isinstance(freshness, dict):
            errors.append("readiness.freshness must be an object")
    enforcement = config.get("enforcement", {})
    if isinstance(enforcement, dict):
        for key in REQUIRED_ENFORCEMENT:
            if key not in enforcement:
                errors.append(f"missing enforcement field: {key}")
        if "defaultSeverity" in enforcement:
            errors.extend(validate_severity(enforcement["defaultSeverity"], "enforcement.defaultSeverity"))
        allowed_severities = enforcement.get("allowedSeverities", [])
        if allowed_severities != ["warn", "error"]:
            errors.append("enforcement.allowedSeverities must be ['warn', 'error']")
        settings = enforcement.get("settings", {})
        if not isinstance(settings, dict) or not settings:
            errors.append("enforcement.settings must be a non-empty object")
        else:
            for setting_name, setting_level in settings.items():
                if not isinstance(setting_name, str) or not setting_name:
                    errors.append("enforcement.settings keys must be non-empty strings")
                errors.extend(validate_severity(setting_level, f"enforcement.settings.{setting_name}"))
    hooks = config.get("hooks", {})
    if isinstance(hooks, dict):
        for key in REQUIRED_HOOKS:
            if key not in hooks:
                errors.append(f"missing hooks field: {key}")
    gradle = config.get("gradle", {})
    if isinstance(gradle, dict):
        for key in REQUIRED_GRADLE:
            if key not in gradle:
                errors.append(f"missing gradle field: {key}")
    node = config.get("node", {})
    if isinstance(node, dict):
        for key in REQUIRED_NODE:
            if key not in node:
                errors.append(f"missing node field: {key}")
        if node.get("packageManager") not in ("auto", "npm", "pnpm", "yarn", "bun"):
            errors.append("node.packageManager must be one of: auto, npm, pnpm, yarn, bun")
        if "packageFile" in node:
            errors.extend(validate_repo_relative_path(node["packageFile"], "node.packageFile"))
        for key in ("installCommand", "runCommand"):
            if key in node and isinstance(node[key], str) and any(char in node[key] for char in UNSAFE_COMMAND_CHARS):
                errors.append(f"node.{key} contains shell metacharacters: {node[key]}")
    standalone = config.get("standalone", {})
    if isinstance(standalone, dict):
        for key in REQUIRED_STANDALONE:
            if key not in standalone:
                errors.append(f"missing standalone field: {key}")
        errors.extend(validate_command_list(standalone.get("commands", []), TRUSTED_COMMAND_FORMS, "standalone.commands"))
    user_requirement_rules = config.get("userRequirementRules", [])
    if not isinstance(user_requirement_rules, list):
        errors.append("userRequirementRules must be a list")
    else:
        for index, rule in enumerate(user_requirement_rules):
            if not isinstance(rule, dict):
                errors.append(f"userRequirementRules[{index}] must be an object")
                continue
            for key in ("name", "type", "severity", "scope", "description", "validation"):
                if not isinstance(rule.get(key), str) or not rule.get(key):
                    errors.append(f"userRequirementRules[{index}].{key} must be a non-empty string")
            errors.extend(validate_severity(rule.get("severity"), f"userRequirementRules[{index}].severity"))
    yaml_style = config.get("yamlStyle", {})
    if isinstance(yaml_style, dict):
        for key in REQUIRED_YAML_STYLE:
            if key not in yaml_style:
                errors.append(f"missing yamlStyle field: {key}")
        if yaml_style.get("literalMultiline") != "|-":
            errors.append("yamlStyle.literalMultiline must be |-")
        if yaml_style.get("foldedMultiline") != ">-":
            errors.append("yamlStyle.foldedMultiline must be >-")
    if not errors:
        errors.extend(validate_generated_policy(config))
    return errors


def validate_paths(config: dict[str, Any], root: Path, template_mode: bool) -> list[str]:
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
            errors.append(f"{field_name} resolves outside repository root: {path_value}")
        elif not resolved_path.exists():
            errors.append(f"{field_name} is missing: {path_value}")

    for path_value in (docs.get("entrypoint"), docs.get("harnessRoot"), docs.get("guardrails"), docs.get("knownViolations"), docs.get("readiness"), docs.get("updates"), docs.get("root"), docs.get("architecture"), scripts.get("root"), scripts.get("validator"), scripts.get("wrapper")):
        if isinstance(path_value, str):
            validate_resolved_declared_path(path_value, "declared path")
    ci = require_mapping(config, "ci")
    provider = ci.get("provider")
    if current_stage >= 3 and provider == "github-actions" and isinstance(ci.get("githubWorkflow"), str):
        validate_resolved_declared_path(ci["githubWorkflow"], "declared GitHub Actions workflow")
    if current_stage >= 3 and provider == "gitlab-ci" and isinstance(ci.get("gitlabConfig"), str):
        validate_resolved_declared_path(ci["gitlabConfig"], "declared GitLab CI config")
    hooks = require_mapping(config, "hooks")
    if current_stage >= 2 and hooks.get("enabled") is True:
        for key in ("path", "commitMsg", "prePush"):
            path_value = hooks.get(key)
            if isinstance(path_value, str):
                validate_resolved_declared_path(path_value, f"declared hook {key}")
    gradle = require_mapping(config, "gradle")
    if gradle.get("enabled") is True and isinstance(gradle.get("buildFile"), str):
        validate_resolved_declared_path(gradle["buildFile"], "declared Gradle build file")
    node = require_mapping(config, "node")
    if node.get("enabled") is True and isinstance(node.get("packageFile"), str):
        validate_resolved_declared_path(node["packageFile"], "declared Node package file")
    standalone = require_mapping(config, "standalone")
    if standalone.get("enabled") is True and isinstance(standalone.get("entrypoint"), str):
        validate_resolved_declared_path(standalone["entrypoint"], "declared standalone entrypoint")
    for source_root in config.get("sourceRoots", []):
        if isinstance(source_root, str):
            validate_resolved_declared_path(source_root, "declared source root")
    return errors


def validate_symlinks(config: dict[str, Any], root: Path, template_mode: bool) -> list[str]:
    """Validate harness symlink conventions when symlinks are present."""
    if template_mode:
        return []
    errors: list[str] = []
    docs = require_mapping(config, "docs")
    stage = require_mapping(config, "stage")
    current_stage = stage.get("current", 0)
    if not isinstance(current_stage, int):
        current_stage = 0
    if current_stage < 1:
        return []
    compatibility_symlink = docs.get("compatibilitySymlink")
    compatibility_target = docs.get("compatibilitySymlinkTarget", docs.get("entrypoint", "CLAUDE.md"))
    compatibility_status = docs.get("compatibilitySymlinkStatus", "required")
    if compatibility_symlink != "AGENTS.md" or compatibility_target != "CLAUDE.md":
        errors.append("compatibility symlink must be AGENTS.md -> CLAUDE.md")
        return errors
    if compatibility_status in ("pending-conflict", "not-applicable"):
        return []
    if isinstance(compatibility_symlink, str):
        compatibility_path = root / compatibility_symlink
        if not compatibility_path.is_symlink():
            if compatibility_path.exists():
                errors.append(f"{compatibility_symlink} exists but is not a symlink")
            else:
                errors.append(f"{compatibility_symlink} must be present as a symlink")
        elif os.readlink(compatibility_path) != str(compatibility_target):
            errors.append(f"{compatibility_symlink} must point to {compatibility_target}")
    skills_link = root / ".agents" / "skills"
    if skills_link.exists() or skills_link.is_symlink():
        if not skills_link.is_symlink():
            errors.append(".agents/skills exists but is not a symlink")
        elif os.readlink(skills_link) != "../.claude/skills":
            errors.append(".agents/skills must point to ../.claude/skills")
    return errors


def main() -> int:
    """Run command-line validation."""
    parser = argparse.ArgumentParser(description="Validate harness-engineering config and installed target-repo paths.")
    parser.add_argument("--config", default=DEFAULT_CONFIG_PATH, help="Path to the harness config JSON file.")
    parser.add_argument("--root", default=".", help="Target repository root. Defaults to the current directory.")
    parser.add_argument("--template", action="store_true", help="Validate config shape only, without requiring installed paths.")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    config_path = Path(args.config)
    if not config_path.is_absolute():
        config_path = root / config_path
    try:
        config = load_config(config_path)
        errors = validate_shape(config)
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
