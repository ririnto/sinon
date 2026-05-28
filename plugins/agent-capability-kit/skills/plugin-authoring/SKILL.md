---
name: plugin-authoring
description: >-
  Create or refactor a Claude Code plugin root with a manifest, README, and only the runtime components the plugin actually uses. Use when scaffolding a new plugin, validating `plugin.json` manifest fields, or trimming unused runtime surfaces from an existing plugin.
---

# Plugin Authoring

Author a Claude Code plugin root that is structurally clear, minimal, and fully authorable from local files alone.

## Goal

Create or refactor one plugin root so its Claude Code manifest, README, and optional runtime surfaces match the filesystem layout.

## Scope

This skill owns the plugin root and plugin-level runtime files:

- `.claude-plugin/plugin.json` (Claude Code runtime manifest)
- `README.md`
- `commands/`
- `agents/`
- `skills/`
- `hooks/`
- `.mcp.json`
- `.lsp.json`
- `settings.json`
- `output-styles/`
- `monitors/monitors.json`

## Operating rules

1. Only `plugin.json` belongs inside `.claude-plugin/` when that runtime directory exists.
2. Runtime components live at the plugin root.
3. Add only the directories and config files the plugin actually needs.
4. Inside `plugin.json`, every declared path MUST begin with `./`. Directory-typed fields MUST use the trailing-slash directory form (`"skills": "./skills/"`, `"commands": "./commands/"`); array-of-paths and bare `./skills` are prohibited. File-typed fields MUST point to the canonical filename at the plugin root: `"hooks": "./hooks/hooks.json"`, `"mcpServers": "./.mcp.json"`, `"lspServers": "./.lsp.json"`, `"settings": "./settings.json"`.
5. The manifest and the filesystem MUST stay bidirectionally consistent. When `lspServers` is declared, plugin-root `.lsp.json` MUST exist; when plugin-root `.lsp.json` exists, the manifest SHOULD declare `"lspServers": "./.lsp.json"`. The same bidirectional rule applies to `mcpServers` and plugin-root `.mcp.json`, to `hooks` and `hooks/hooks.json`, to `settings` and `settings.json`, and to `experimental.monitors` and `monitors/monitors.json`.
6. The manifest MUST NOT declare an `agents` key, MUST NOT declare a `version` field, and MUST NOT declare an `interface` block. These keys are rejected by current host schemas or by repository policy.
7. Keep plugin metadata concise and operational.
8. Keep bundled source files under `${CLAUDE_PLUGIN_ROOT}` and keep generated or persistent runtime data under `${CLAUDE_PLUGIN_DATA}`.
9. Keep the ordinary authoring path in this file; open support files only for named blockers, deeper examples, or release review.
10. Keep `agents/` at the plugin root whenever the plugin ships agents. Each `.md` filename under `agents/` MUST match its frontmatter `name` field exactly, and both MUST use kebab-case. Document the shipped agents in the plugin `README.md` instead of declaring them in `plugin.json`.
11. For Sinon marketplace plugins, publish only plugin roots that include `.claude-plugin/plugin.json`.
12. Ordinary authoring remains offline, but maintainers changing host-specific or schema-specific guidance should verify against official host documentation when available and record any verification blocker.

## Canonical minimal tree

Start from this tree when the plugin ships commands and skills:

```text
your-plugin/
├── .claude-plugin/
│   └── plugin.json
├── README.md
├── commands/
└── skills/
```

Use the matching subset when the plugin ships only one component type. Add `agents/` at the plugin root when the plugin ships agents or subagents, but keep it out of `plugin.json`. Remove any directory the plugin does not actually use, and add other runtime surfaces only when the plugin needs them.

## Procedure

1. Define the plugin purpose in one sentence.
2. Create `.claude-plugin/plugin.json` from `assets/plugin.json`.
3. Create `README.md` describing the plugin purpose, included skills, agents, commands, runtime model, layout, and scope notes.
4. Keep only the manifest keys that point to real component paths in the current tree.
5. Create root-level component directories only when the plugin ships that component.
6. Add optional runtime surfaces only after deciding that the plugin needs that specific behavior:
   - add `hooks` only when the plugin reacts to Claude Code lifecycle events
   - add `mcpServers` only when the plugin ships MCP server definitions
   - add `lspServers` only when the plugin configures LSP servers
   - add `settings` only when the plugin needs plugin-level settings
   - add `outputStyles` only when the plugin ships reusable output styles
   - add `experimental.monitors` only when the plugin genuinely needs monitor definitions
7. Keep plugin data boundaries explicit:
   - use `${CLAUDE_PLUGIN_ROOT}` for bundled scripts, templates, servers, and other files that ship with the plugin
   - use `${CLAUDE_PLUGIN_DATA}` for generated caches, logs, indexes, or other persistent runtime data
8. Validate bidirectionally:
   - Manifest → Filesystem: Every manifest key declaration (e.g., `lspServers: "./.lsp.json"`, `mcpServers: "./.mcp.json"`, `hooks: "./hooks/hooks.json"`, `settings: "./settings.json"`, `experimental.monitors: "./monitors/monitors.json"`) MUST have a matching plugin-root file or directory.
   - Filesystem → Manifest: Every plugin-root configuration file (`.lsp.json`, `.mcp.json`, `hooks/hooks.json`, `settings.json`, `monitors/monitors.json`) SHOULD be declared in the manifest with the correct key and exact path so the runtime knows to publish it.
   - Every declared path MUST begin with `./`, every declared component MUST exist, and every runtime manifest directory MUST contain only `plugin.json`.

## Minimal example

Use this as the default `.claude-plugin/plugin.json` starting point:

```jsonc
{
  "$schema": "https://anthropic.com/claude-code/plugin.schema.json",
  "name": "your-plugin-name",
  "description": "Plugin for a clearly bounded workflow.",
  "author": {
    "name": "your-handle"
  },
  "commands": "./commands/",
  "skills": "./skills/"
}
```

If the plugin ships agents at the plugin root, keep the directory in the plugin tree but leave it out of the manifest:

```jsonc
{
  "$schema": "https://anthropic.com/claude-code/plugin.schema.json",
  "name": "your-plugin-name",
  "description": "Plugin for a clearly bounded workflow.",
  "author": {
    "name": "your-handle"
  },
  "commands": "./commands/",
  "skills": "./skills/"
}
```

Add optional keys only when the plugin needs the corresponding runtime surface. For example:

```jsonc
{
  "$schema": "https://anthropic.com/claude-code/plugin.schema.json",
  "name": "your-plugin-name",
  "description": "Claude Code plugin for a clearly bounded workflow.",
  "author": {
    "name": "your-handle"
  },
  "commands": "./commands/",
  "skills": "./skills/",
  "hooks": "./hooks/hooks.json",
  "settings": "./settings.json"
}
```

This is valid only if `./hooks/hooks.json` and `./settings.json` exist and the plugin actually uses them.

## Ordinary component decisions

Use these defaults during normal authoring:

- `commands/`: add when the plugin ships slash commands
- `agents/`: add when the plugin ships agents or subagents, but keep it out of the runtime manifest
- `skills/`: add when the plugin ships reusable skills
- `hooks/`: add when the plugin must intercept or react to tool or session events
- `.mcp.json`: add when the plugin needs MCP server registrations
- `.lsp.json`: add when the plugin needs LSP server registrations
- `settings.json`: add when the plugin needs plugin-level settings
- `output-styles/`: add when the plugin ships reusable response formatting
- `monitors/monitors.json`: add only for genuine monitor behavior, not as a default scaffold

If a plugin does not need a surface yet, omit both the file or directory and the manifest key.

## Optional runtime surfaces

Add optional surfaces only when the plugin genuinely needs that behavior. Omit both the manifest key and the filesystem artifact when the surface is not in use.

| Surface | Manifest key | When to add | Starter |
| --- | --- | --- | --- |
| Agents | none | the plugin ships agents or subagents as a root-level directory | create `agents/` at the plugin root and document the shipped agents in the plugin README |
| Hooks | `"hooks": "./hooks/hooks.json"` | the plugin must react to Claude Code lifecycle events | copy `assets/hooks.json` + `assets/hooks/check.sh` |
| MCP | `"mcpServers": "./.mcp.json"` | the plugin ships a local MCP server | copy `assets/.mcp.json` + `assets/servers/example-mcp.py` |
| LSP | `"lspServers": "./.lsp.json"` | the plugin configures a language server | copy `assets/.lsp.json` + `assets/lsp/example-lsp.py` |
| Settings | `"settings": "./settings.json"` | the plugin needs plugin-level settings | copy `assets/settings.json` |
| Output styles | `"outputStyles": "./output-styles/"` | the plugin ships reusable response formats | copy `assets/output-style.md` |
| Monitors | `"experimental": { "monitors": "./monitors/monitors.json" }` | the plugin needs background observation | copy `assets/monitors/monitors.json` + `assets/monitors/watch.sh` |

Open `references/plugin-runtime-components.md` for per-surface extension points, tradeoffs, and deeper wiring guidance beyond the ordinary copy path above.

## Data boundary guidance

Keep these boundaries invariant across all plugin assets and starter files:

- `${CLAUDE_PLUGIN_ROOT}`: read-only for bundled scripts, templates, servers, and other shipped files
- `${CLAUDE_PLUGIN_DATA}`: writable only for generated caches, logs, indexes, or other persistent runtime data

Never treat `${CLAUDE_PLUGIN_ROOT}` as a writable data directory. Open `references/plugin-release.md` for the full example split and release-review context.

## Pitfalls

- Do not place component files inside `.claude-plugin/`.
- Do not create a runtime manifest directory unless the plugin publishes to that runtime.
- Do not declare paths that do not begin with `./`.
- Do not use array-of-paths form for `skills` or `commands`; always use the directory form with trailing slash.
- Do not declare `lspServers`, `mcpServers`, `hooks`, `settings`, or `experimental.monitors` keys without their corresponding plugin-root files.
- Do not declare `agents`, `version`, or `interface` keys.
- Do not let `plugin.json` promise components that the tree does not contain.
- Do not treat `${CLAUDE_PLUGIN_ROOT}` as a writable data directory.
- Do not require support files to complete ordinary plugin authoring.

## First safe commands

Use this command first when checking a real plugin root:

```sh
uv run -m json.tool .claude-plugin/plugin.json
```

The command above validates JSON syntax offline. For runtime validation with a live Claude Code installation, use:

```sh
claude --plugin-dir /absolute/path/to/your-plugin
```

This second command requires a live Claude Code installation and is optional for ordinary offline authoring.

## Output contract

Return:

1. The plugin root tree.
2. The final runtime manifest file.
3. Every plugin-level config file created.
4. A short note explaining why each optional component exists.

## Optional support files

- Open `references/plugin-layout.md` when you need expanded tree examples for minimal, command-only, or full plugin roots.
- Open `references/plugin-runtime-components.md` when a plugin needs deeper per-surface examples, extension points, or local file layout beyond the ordinary copy path above.
- Open `references/plugin-release.md` when reviewing install scope, packaging, or release checks.
- Copy from `assets/plugin.json` for the Claude starter manifest.
- Copy the other files under `assets/` only when the matching optional surface is part of the plugin you are authoring.
