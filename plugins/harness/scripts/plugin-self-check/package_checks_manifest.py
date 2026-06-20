"""Manifest validation for Sinon plugin packages."""

from __future__ import annotations

from pathlib import Path

from package_checks_common import (
    JsonValue,
    PLUGIN_SCHEMA,
    Reporter,
    read_json,
)


MANIFEST_METADATA_KEYS = {
    "$schema",
    "author",
    "description",
    "homepage",
    "keywords",
    "license",
    "name",
    "repository",
    "version",
}
MANIFEST_STRUCTURED_KEYS = {
    "channels",
    "defaultEnabled",
    "dependencies",
    "displayName",
    "userConfig",
}
MANIFEST_COMPONENT_KEYS = {
    "agents",
    "hooks",
    "lspServers",
    "mcpServers",
    "outputStyles",
    "skills",
}
INLINE_OBJECT_COMPONENT_KEYS = {
    "hooks",
    "lspServers",
    "mcpServers",
}
TOP_LEVEL_EXPERIMENTAL_KEYS = {
    "monitors": "experimental.monitors",
    "themes": "experimental.themes",
}
AUTO_DISCOVERED_COMPONENT_PATHS = {
    "agents": "./agents/",
    "hooks": "./hooks/hooks.json",
    "lspServers": "./.lsp.json",
    "mcpServers": "./.mcp.json",
    "outputStyles": "./output-styles/",
    "skills": "./skills/",
    "experimental.monitors": "./monitors/monitors.json",
    "experimental.themes": "./themes/",
}


def validate_manifest_component(
    plugin_root: Path,
    manifest_path: Path,
    key: str,
    value: JsonValue,
    reporter: Reporter,
) -> None:
    """Validate one manifest component field."""
    validate_auto_discovered_component_path(manifest_path, key, value, reporter)
    if isinstance(value, list):
        for item in value:
            validate_manifest_list_item(plugin_root, manifest_path, key, item, reporter)
        return
    if isinstance(value, dict):
        if key not in INLINE_OBJECT_COMPONENT_KEYS:
            reporter.error(manifest_path, f"{key} must be a string path or array")
        return
    if not isinstance(value, str):
        reporter.error(manifest_path, f"{key} must be a string path or array")
        return
    validate_manifest_string_path(plugin_root, manifest_path, key, value, reporter)


def validate_manifest_list_item(
    plugin_root: Path,
    manifest_path: Path,
    key: str,
    value: JsonValue,
    reporter: Reporter,
) -> None:
    """Validate one item inside an array-valued manifest component field."""
    if isinstance(value, str):
        validate_manifest_string_path(plugin_root, manifest_path, key, value, reporter)
        return
    if key == "experimental.monitors" and isinstance(value, dict):
        return
    reporter.error(manifest_path, f"{key} array items must be string paths")


def validate_manifest_string_path(
    plugin_root: Path,
    manifest_path: Path,
    key: str,
    value: str,
    reporter: Reporter,
) -> None:
    """Validate one manifest string path inside the plugin root."""
    if not value.startswith("./"):
        reporter.error(manifest_path, f"{key} path must begin with ./")
    declared_path = plugin_root / value.removeprefix("./")
    if not declared_path.exists():
        reporter.error(manifest_path, f"declared path does not exist: {value}")
        return
    root_path = plugin_root.resolve()
    resolved_path = declared_path.resolve()
    if resolved_path != root_path and root_path not in resolved_path.parents:
        reporter.error(manifest_path, f"declared path escapes plugin root: {value}")


def validate_auto_discovered_component_path(
    manifest_path: Path,
    key: str,
    value: JsonValue,
    reporter: Reporter,
) -> None:
    """Reject redundant declarations that only restate auto-discovered paths."""
    default_path = AUTO_DISCOVERED_COMPONENT_PATHS.get(key)
    if default_path is None:
        return
    if value == default_path or value == [default_path]:
        reporter.error(
            manifest_path,
            f"{default_path} is auto-discovered; omit {key} unless combining it with custom paths",
        )


def validate_experimental_manifest(
    plugin_root: Path,
    manifest_path: Path,
    value: JsonValue,
    reporter: Reporter,
) -> None:
    """Validate experimental manifest component paths."""
    if not isinstance(value, dict):
        reporter.error(manifest_path, "experimental must be an object")
        return
    monitors = value.get("monitors")
    if monitors is not None:
        validate_manifest_component(
            plugin_root,
            manifest_path,
            "experimental.monitors",
            monitors,
            reporter,
        )
    themes = value.get("themes")
    if themes is not None:
        validate_manifest_component(
            plugin_root,
            manifest_path,
            "experimental.themes",
            themes,
            reporter,
        )


def validate_manifest(plugin_root: Path, reporter: Reporter) -> None:
    """Validate one plugin manifest and its declared filesystem surface."""
    manifest_path = plugin_root / ".claude-plugin" / "plugin.json"
    manifest = read_json(manifest_path, reporter)
    if not manifest:
        return
    if manifest.get("$schema") != PLUGIN_SCHEMA:
        reporter.error(manifest_path, f"$schema must be {PLUGIN_SCHEMA}")
    if manifest.get("name") != plugin_root.name:
        reporter.error(manifest_path, "name must match plugin directory basename")
    author = manifest.get("author")
    if not isinstance(author, dict) or not author.get("name"):
        reporter.error(manifest_path, "author must use object form with name")
    if "interface" in manifest:
        reporter.error(manifest_path, "interface must not appear in plugin manifest")
    for key, value in manifest.items():
        if key == "experimental":
            validate_experimental_manifest(plugin_root, manifest_path, value, reporter)
            continue
        if key in TOP_LEVEL_EXPERIMENTAL_KEYS:
            validate_manifest_component(
                plugin_root,
                manifest_path,
                TOP_LEVEL_EXPERIMENTAL_KEYS[key],
                value,
                reporter,
            )
            continue
        if key in MANIFEST_METADATA_KEYS or key in MANIFEST_STRUCTURED_KEYS:
            continue
        if key not in MANIFEST_COMPONENT_KEYS:
            reporter.error(manifest_path, f"unsupported manifest field: {key}")
            continue
        validate_manifest_component(plugin_root, manifest_path, key, value, reporter)
