---
name: plugin-authoring
description: >-
  Create or refactor a Claude Code plugin root with a manifest, README, and only the runtime components it uses.
  Use when scaffolding a plugin, validating manifest fields and component paths, or trimming unused plugin surfaces.
---

# Plugin Authoring

Build a minimal Claude Code plugin whose manifest, filesystem, and documentation describe the same published runtime.

## Owned Surface

- `.claude-plugin/plugin.json`
- plugin `README.md`
- `skills/`, `commands/`, and `agents/`
- `hooks/hooks.json`, `.mcp.json`, `.lsp.json`, and `settings.json`
- `output-styles/`, `themes/`, `monitors/monitors.json`, and `bin/`
- helper programs bundled beside the component that owns them

## Minimal Plugin

```text
your-plugin/
+-- .claude-plugin/
|   +-- plugin.json
+-- README.md
+-- skills/
    +-- example/
        +-- SKILL.md
```

Only `plugin.json` belongs inside `.claude-plugin/`.
Runtime components stay at the plugin root.
Remove `skills/` when the plugin does not ship a skill, and add no optional surface until the plugin needs it.

Use this manifest:

```json
{
  "$schema": "https://json.schemastore.org/claude-code-plugin-manifest.json",
  "name": "your-plugin-name",
  "description": "Plugin for a clearly bounded workflow.",
  "author": {
    "name": "your-handle"
  }
}
```

For Sinon git-sourced plugins, omit `version` unless maintainers have adopted a semver release cycle.
Do not add `interface`.

## Default Discovery

Claude Code discovers these plugin-root locations without manifest declarations:

- `skills/`, `commands/`, `agents/`, `output-styles/`, and `themes/`
- `hooks/hooks.json`, `.mcp.json`, `.lsp.json`, and `settings.json`
- `monitors/monitors.json`
- executable files in `bin/`

Keep a default path out of the manifest when it is the only source for that surface.
Use a manifest component field only for a custom path or schema-supported inline configuration.
Every declared path MUST begin with `./` and resolve inside the plugin root.

## Path Behavior Is Field-Specific

Do not describe every component path as additive.

| Behavior | Fields |
| --- | --- |
| Adds to the default scan | `skills` |
| Replaces the default scan | `commands`, `agents`, `outputStyles`, `experimental.themes`, `experimental.monitors` |
| Uses its own merge rules | `hooks`, `mcpServers`, `lspServers` |

When a replacing field must preserve the default directory, include that directory explicitly with the custom path:

```json
{
  "agents": ["./agents/", "./special/reviewer.md"],
  "experimental": {
    "themes": ["./themes/", "./branding/themes/"]
  }
}
```

Top-level `themes` and `monitors` remain accepted during migration but are deprecated and produce validation warnings.
Use `experimental.themes` and `experimental.monitors` for new or updated manifests.

Manifest `settings` is an inline object, not a path field.
Claude Code applies only documented allowlisted plugin settings.
Manifest `userConfig` declares prompted values and is independent of component path rules.

## Procedure

1. Define one plugin purpose and its intended consumers.
2. Inventory only the runtime components required for that purpose.
3. Create `.claude-plugin/plugin.json` and the plugin `README.md`.
4. Add each required default component at the plugin root.
5. Add a manifest field only for a custom path or inline configuration.
6. Keep bundled code under `${CLAUDE_PLUGIN_ROOT}` and generated state under `${CLAUDE_PLUGIN_DATA}`.
7. Document included skills, agents, runtime behavior, layout, setup, and scope boundaries in the README.
8. Validate the manifest, component files, and live plugin.
9. Remove empty or unused surfaces before delivery.

## Component Decisions

| Surface | Add when | Default path |
| --- | --- | --- |
| Skills | Reusable instructions should activate from task intent | `skills/<name>/SKILL.md` |
| Commands | Legacy flat command or skill files are required | `commands/` |
| Agents | A bounded reusable subagent is shipped | `agents/<name>.md` |
| Hooks | Lifecycle events must trigger deterministic or model-based behavior | `hooks/hooks.json` |
| MCP | The plugin provides external or local MCP tools | `.mcp.json` |
| LSP | The plugin provides language intelligence | `.lsp.json` |
| Settings | The plugin selects a default agent or subagent status line | `settings.json` |
| User configuration | Values must be prompted when the plugin is enabled | manifest `userConfig` |
| Output styles | Reusable response formatting is shipped | `output-styles/` |
| Themes | Read-only color themes are shipped | `themes/` |
| Monitors | A background monitor is genuinely required | `monitors/monitors.json` |
| Executables | A bundled program should be available as a bare Bash command | `bin/` |

Plugin-root `settings.json` currently supports only `agent` and `subagentStatusLine`.
Do not put ordinary Claude Code settings such as `permissions`, `env`, `model`, `hooks`, or `statusLine` there.

Open `references/plugin-runtime-components.md` only after selecting an optional surface.

## Data Boundary

- `${CLAUDE_PLUGIN_ROOT}` is the installed plugin version and MUST be treated as bundled, ephemeral, and read-only.
- `${CLAUDE_PLUGIN_DATA}` is the persistent writable directory for caches, indexes, logs, installed dependencies, and state.
- `${CLAUDE_PROJECT_DIR}` is the target project root.

An update may leave the previous plugin directory on disk temporarily.
After plugin component changes, run `/reload-plugins`; monitors require a session restart to switch to an updated plugin path.

## First Safe Commands

Validate JSON syntax and the plugin package:

```sh
python3 -m json.tool .claude-plugin/plugin.json
claude plugin validate .
```

Load a development plugin directly:

```sh
claude --plugin-dir /absolute/path/to/your-plugin
```

After changing hooks, MCP, LSP, agents, output styles, or other non-skill components, run `/reload-plugins` or restart Claude Code.
Skill content changes are detected immediately.

## Validation

- `plugin.json` has the required schema, name, and object-form author
- every declared path begins with `./`, exists, and stays inside the plugin root
- replacing fields preserve defaults explicitly when required
- `experimental.themes` and `experimental.monitors` are used instead of deprecated top-level keys
- default-only component paths are omitted
- `settings.json` contains only supported keys
- `hooks/hooks.json` and `.mcp.json` use their required wrapper objects
- each agent filename matches its frontmatter `name` for Sinon packages
- each skill directory name matches its `SKILL.md` `name`
- README inventory matches the shipped tree
- no generated state is written under `${CLAUDE_PLUGIN_ROOT}`
- `claude plugin validate .` has no blocking error

## Output Contract

Return:

1. the final plugin tree
2. the final manifest
3. every component file created or changed
4. the validation commands and results
5. a brief reason for every optional surface retained

## Pitfalls

- Do not place runtime components inside `.claude-plugin/`.
- Do not restate default component paths as the only manifest value.
- Do not call replacing path fields additive.
- Do not use top-level `themes` or `monitors` in new manifests.
- Do not treat manifest `settings` as a file path.
- Do not use plugin `settings.json` for arbitrary settings.
- Do not write generated data under `${CLAUDE_PLUGIN_ROOT}`.
- Do not leave placeholder components in a published plugin.

## Support Files

- `references/plugin-layout.md` - open for composite trees and custom-path examples.
- `references/plugin-runtime-components.md` - open after choosing hooks, MCP, LSP, settings, output styles, themes, monitors, or executables.
- `references/plugin-release.md` - open for install scope, release, and persistent-data review.
- `assets/` - copy only the starter file for a component the plugin will actually ship.
