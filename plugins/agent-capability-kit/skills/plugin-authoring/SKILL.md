---
name: plugin-authoring
description: >-
  Create or refactor a Claude Code plugin root with a manifest and README.
  Keep only runtime components the plugin actually uses.
  Use when scaffolding a new plugin, validating `plugin.json` manifest fields, or trimming unused runtime surfaces from an existing plugin.
---

# Plugin Authoring

Author a Claude Code plugin root that is structurally clear, minimal, and fully authorable from local files alone.

## Goal

Create or refactor one plugin root so its Claude Code manifest, README, and optional runtime surfaces match the filesystem layout.

## Scope

This skill owns the plugin root and plugin-level runtime files:

- `.claude-plugin/plugin.json` (Claude Code runtime manifest)
- `README.md`
- `agents/`
- `skills/`
- `commands/`
- `hooks/`
- `.mcp.json`
- `.lsp.json`
- `settings.json`
- `output-styles/`
- `themes/`
- `monitors/monitors.json`
- `bin/`

## Operating rules

1. Only `plugin.json` belongs inside `.claude-plugin/` when that runtime directory exists.
2. Runtime components live at the plugin root.
3. Add only the directories and config files the plugin actually needs.
4. Inside `plugin.json`, every declared path MUST begin with `./`.
   - Keep default component locations out of the manifest because Claude Code discovers them automatically.
   - Default runtime locations include:
     - `skills/`, `agents/`, `commands/`, `hooks/hooks.json`, `.mcp.json`
     - `.lsp.json`, `settings.json`, `output-styles/`, `themes/`, `monitors/monitors.json`, `bin/`
   - Use component path fields only for custom paths or schema-supported inline configuration.
   - Custom path fields are additive: each adds to the matching default directory scan rather than replacing it.
     - `skills`, `agents`, `commands`, `outputStyles`, `themes`, and `monitors` all supplement their default directories when declared.
     - `settings` merges into the user settings and applies only documented allowlisted keys.
     - `userConfig` declares prompted values exposed as `${user_config.KEY}`.
   - Custom directory paths, when present, SHOULD use the trailing-slash directory form.
5. Manifest path declarations MUST match real files or directories.
   - Path-valued component fields MUST resolve inside the plugin root.
   - Inline object component fields do not require a companion path file.
6. The manifest MUST NOT declare an `interface` block.
   - Sinon git-sourced plugins SHOULD omit `version` unless a maintainer adopts semver releases for that plugin.
   - Keep standard component locations out of `plugin.json`; use component keys only for custom paths or inline configuration.
7. Keep plugin metadata concise and operational.
8. Keep bundled source files under `${CLAUDE_PLUGIN_ROOT}` and keep generated or persistent runtime data under `${CLAUDE_PLUGIN_DATA}`.
9. Keep the ordinary authoring path in this file.
   - Open support files only for named blockers, deeper examples, or release review.
10. Keep `agents/` at the plugin root whenever the plugin ships default agents.
    - Each `.md` filename under `agents/` MUST match its frontmatter `name` field exactly, and both MUST use kebab-case.
    - Document the shipped agents in the plugin `README.md`.
11. For Sinon marketplace plugins, publish only plugin roots that include `.claude-plugin/plugin.json`.
12. Ordinary authoring remains offline.
    - Maintainers changing host-specific or schema-specific guidance should verify against official host documentation when available.
    - Record any verification blocker.

## Canonical minimal tree

Start from this tree when the plugin ships skills:

```text
your-plugin/
+-- .claude-plugin/
|   +-- plugin.json
+-- README.md
+-- skills/
```

Use the matching subset when the plugin ships only one component type.
Add `agents/` at the plugin root when the plugin ships agents or subagents, but keep it out of `plugin.json`.
Remove any directory the plugin does not actually use, and add other runtime surfaces only when the plugin needs them.

## Procedure

1. Define the plugin purpose in one sentence.
2. Create `.claude-plugin/plugin.json` from the inline minimal manifest below.
   - Optionally copy `assets/plugin.json` when a starter file is useful.
3. Create `README.md` describing the plugin purpose, included skills, agents, runtime model, layout, and scope notes.
4. Keep only metadata, custom component paths, and inline component configuration in the manifest.
5. Create root-level component directories only when the plugin ships that component.
6. Add optional runtime surfaces only after deciding that the plugin needs that specific behavior:
   - Add `commands` only when the plugin ships slash commands outside the default `commands/` directory.
   - Add `hooks` only when the plugin reacts to Claude Code lifecycle events.
   - Add `mcpServers` only when the plugin ships MCP server definitions.
   - Add `lspServers` only when the plugin configures LSP servers.
   - Add root `settings.json` only when the plugin needs plugin-level settings.
   - Add manifest `userConfig` only when the plugin needs prompted install-time values.
   - Add `outputStyles` only when the plugin ships reusable output styles.
   - Add `themes` only when the plugin ships Claude Code color themes.
   - Add `monitors` only when the plugin genuinely needs monitor definitions.
   - Add `bin/` only when the plugin ships executables that should be available as bare commands from Bash.
7. Keep plugin data boundaries explicit:
   - Use `${CLAUDE_PLUGIN_ROOT}` for bundled scripts, templates, servers, and other files that ship with the plugin.
   - Use `${CLAUDE_PLUGIN_DATA}` for generated caches, logs, indexes, or other persistent runtime data.
8. Validate the manifest and filesystem:
   - Every declared string path MUST begin with `./` and resolve inside the plugin root.
   - Default component locations SHOULD remain undeclared unless they are combined with custom paths as official merge rules require.
   - Every runtime manifest directory MUST contain only `plugin.json`.

## Minimal example

Use this as the default `.claude-plugin/plugin.json` starting point:

```jsonc
{
  "$schema": "https://json.schemastore.org/claude-code-plugin-manifest.json",
  "name": "your-plugin-name",
  "description": "Plugin for a clearly bounded workflow.",
  "author": {
    "name": "your-handle"
  }
}
```

If the plugin ships skills or agents at default plugin-root locations, keep those directories in the plugin tree.
Leave default locations out of the manifest unless you need custom paths, explicit file subsets, or another official merge-rule case:

```jsonc
{
  "$schema": "https://json.schemastore.org/claude-code-plugin-manifest.json",
  "name": "your-plugin-name",
  "description": "Plugin for a clearly bounded workflow.",
  "author": {
    "name": "your-handle"
  }
}
```

Add optional keys only when the plugin needs the corresponding runtime surface.

## Ordinary component decisions

Use these defaults during normal authoring:

- `agents/`: add when the plugin ships default agents or subagents
- `skills/`: add when the plugin ships reusable skills
- `hooks/`: add when the plugin must intercept or react to tool or session events
- `.mcp.json`: add when the plugin needs MCP server registrations
- `.lsp.json`: add when the plugin needs LSP server registrations
- `settings.json`: add when the plugin needs plugin-level settings
- `output-styles/`: add when the plugin ships reusable response formatting
- `themes/`: add when the plugin ships Claude Code color themes
- `monitors/monitors.json`: add only for genuine monitor behavior, not as a default scaffold
- `bin/`: add when the plugin ships executables that should be available on Bash `PATH`

If a plugin does not need a surface yet, omit both the file or directory and the manifest key.

## Optional runtime surfaces

Add optional surfaces only when the plugin genuinely needs them.
Omit both the manifest key and filesystem artifact when a surface is not in use.

| Surface | Manifest key | Add when | Starter path |
| --- | --- | --- | --- |
| Commands | none for default `commands/` | Plugin ships slash commands outside the default directory. | `commands/` |
| | `commands` for custom paths | | |
| Skills | none for default `skills/` | Plugin ships skills outside the default directory. | `skills/` |
| | `"skills": ["./custom/skills/"]` for custom paths | | |
| Agents | none for default `agents/` | Plugin ships agents or subagents. | `agents/` |
| | `"agents": ["./custom/agents/reviewer.md"]` for custom paths | | |
| Hooks | none for default `hooks/hooks.json` | Plugin intercepts Claude Code lifecycle events. | `hooks/hooks.json` |
| | `hooks` for custom path or inline config | | |
| MCP | none for default `.mcp.json`; `mcpServers` for custom path or inline config | Plugin registers MCP servers. | `.mcp.json` |
| LSP | none for default `.lsp.json`; `lspServers` for custom path or inline config | Plugin configures LSP servers. | `.lsp.json` |
| Settings | none for default `settings.json` | Plugin exposes plugin-level settings. | `settings.json` |
| | use `userConfig` for prompted values | | |
| Output styles | none for default `output-styles/` | Plugin ships reusable output styles. | `output-styles/` |
| | `outputStyles` for custom paths | | |
| Themes | none for default `themes/`; `themes` for custom paths | Plugin ships Claude Code color themes. | `themes/` |
| Monitors | none for default `monitors/monitors.json`; `monitors` for custom path or inline config | Plugin ships genuine monitor behavior, not a default scaffold. | `monitors/monitors.json` |
| Executables | none | Plugin ships executables that should be available to Bash as bare executables. | `bin/` |

Open `references/plugin-runtime-components.md` for per-surface extension points, tradeoffs, and wiring guidance.

Manifest path fields are additive across the board.
When you declare `skills`, `agents`, `commands`, `outputStyles`, `themes`, or `monitors`, Claude Code loads the declared paths in addition to the matching default directory, not instead of it.
Every declared path MUST still begin with `./` and resolve inside the plugin root.

## Data boundary guidance

Keep plugin-owned data in the correct location:

- `${CLAUDE_PLUGIN_ROOT}`: read-only bundled scripts, templates, servers, and shipped files.
- `${CLAUDE_PLUGIN_DATA}`: writable generated caches, logs, indexes, and persistent runtime data.

Never treat `${CLAUDE_PLUGIN_ROOT}` as a writable data directory.
Open `references/plugin-release.md` for data split and release-review context.

## Pitfalls

- Do not place component files inside `.claude-plugin/`.
- Do not create a runtime manifest directory unless the plugin publishes to that runtime.
- Do not declare paths that do not begin with `./`.
- Do not declare default component locations in the manifest when the default location is the only value.
- Use directory form with a trailing slash for custom component directories.
- Do not declare path-valued component keys without corresponding plugin-root files.
- Prefer default `settings.json` for plugin-level settings, and use `userConfig` for prompted plugin configuration.
- Do not declare custom `agents` paths unless the files exist.
- Do not declare `version` unless the plugin has a semver release cycle.
- Do not declare `interface`.
- Do not let `plugin.json` promise components that the tree does not contain.
- Do not treat `${CLAUDE_PLUGIN_ROOT}` as a writable data directory.
- Do not require support files to complete ordinary plugin authoring.

## Validation

Use this command first when checking a real plugin root:

```sh
bun -e 'JSON.parse(await Bun.file(".claude-plugin/plugin.json").text())'
```

The command above validates JSON syntax offline.

For runtime validation with a live Claude Code installation, use:

```sh
claude --plugin-dir /absolute/path/to/your-plugin
```

The second command requires a live Claude Code installation and is optional for ordinary offline authoring.

## Output contract

Return:

1. The plugin root tree.
2. The final runtime manifest file.
3. Every plugin-level config file created.
4. A short note explaining why each optional component exists.

## Optional support files

- Open `references/plugin-layout.md` when expanded tree examples for minimal or full plugin roots are needed.
- Open `references/plugin-runtime-components.md` when a plugin needs deeper per-surface examples, extension points, or local file layout beyond the ordinary manifest path above.
- Open `references/plugin-release.md` when reviewing install scope, packaging, or release checks.
- Optionally copy `assets/plugin.json` when a starter manifest file is useful.
- Copy other files under `assets/` only when the matching optional surface is part of the plugin being authored.
