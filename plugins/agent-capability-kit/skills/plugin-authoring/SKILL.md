---
name: plugin-authoring
description: Create or refactor a Claude Code plugin root with a minimal manifest and only the runtime components the plugin actually uses. Use this skill when authoring or reorganizing a plugin root offline.
---

# Plugin Authoring

Author a Claude Code plugin root that is structurally clear, minimal, and fully authorable from local files alone.

## Goal

Create or refactor one plugin root so the manifest is minimal, the filesystem layout matches the manifest, and optional runtime surfaces appear only when the plugin truly needs them.

## Scope

This skill owns the plugin root and plugin-level runtime files:

- `.claude-plugin/plugin.json` (Claude Code runtime manifest)
- `.codex-plugin/plugin.json` (Codex runtime manifest; optional for single-runtime plugins)
- `commands/`
- `agents/`
- `skills/`
- `hooks/`
- `.mcp.json`
- `.lsp.json`
- `settings.json`
- `output-styles/`
- `monitors/`

## Operating rules

1. Only `plugin.json` belongs inside `.claude-plugin/` and `.codex-plugin/`.
2. Runtime components live at the plugin root, shared across both runtime manifests.
3. Add only the directories and config files the plugin actually needs.
4. Use relative paths beginning with `./` inside `plugin.json`, and use the trailing-slash directory form (for example `"skills": "./skills/"`, `"agents": "./agents/"`) rather than array-of-paths or bare `./skills`.
5. Keep plugin metadata concise and operational.
6. Keep bundled source files under `${CLAUDE_PLUGIN_ROOT}` and keep generated or persistent runtime data under `${CLAUDE_PLUGIN_DATA}`.
7. Keep the ordinary authoring path in this file; open support files only for named blockers, deeper examples, or release review.
8. Declare the `agents` manifest key as `"./agents/"` in both `.claude-plugin/plugin.json` and `.codex-plugin/plugin.json` whenever the plugin ships agents at the plugin root.
9. Keep `name`, `description`, `author`, `repository`, `homepage`, and `license` aligned across `.claude-plugin/` and `.codex-plugin/` manifests for the same plugin. Keep the runtime-specific marketplace block (for example Codex `interface`) only in its own manifest.

## Canonical minimal tree

Start from this tree unless the plugin needs fewer or more root-level components:

```text
your-plugin/
├── .claude-plugin/
│   └── plugin.json
├── commands/
└── skills/
```

This is the normal starter layout because a plugin usually exists to ship commands, skills, or a combination of them. Add `agents/` at the plugin root when the plugin ships agents or subagents, but keep it out of `plugin.json`. Remove any directory the plugin does not actually use, and add other runtime surfaces only when the plugin needs them.

## Procedure

1. Define the plugin purpose in one sentence.
2. Create `.claude-plugin/plugin.json` from `assets/plugin.json` or copy the minimal example below.
3. Keep only the manifest keys that point to real component paths in the current tree.
4. Create root-level component directories only when the plugin ships that component.
5. Add optional runtime surfaces only after deciding that the plugin needs that specific behavior:
   - add `hooks` only when the plugin reacts to Claude Code lifecycle events
   - add `mcpServers` only when the plugin ships MCP server definitions
   - add `lspServers` only when the plugin configures LSP servers
   - add `settings` only when the plugin needs plugin-level settings
   - add `outputStyles` only when the plugin ships reusable output styles
   - add `monitors` only when the plugin genuinely needs monitor definitions
6. Keep plugin data boundaries explicit:
   - use `${CLAUDE_PLUGIN_ROOT}` for bundled scripts, templates, servers, and other files that ship with the plugin
   - use `${CLAUDE_PLUGIN_DATA}` for generated caches, logs, indexes, or other persistent runtime data
7. Validate that every declared path begins with `./`, every declared component exists, and `.claude-plugin/` contains only `plugin.json`.

## Minimal example

Use this as the default `.claude-plugin/plugin.json` starting point:

```json
{
  "$schema": "https://anthropic.com/claude-code/plugin.schema.json",
  "name": "your-plugin-name",
  "description": "Claude Code plugin for a clearly bounded workflow.",
  "author": "your-handle",
  "commands": "./commands/",
  "skills": "./skills/"
}
```

If the plugin ships agents at the plugin root, declare them in the manifest:

```json
{
  "$schema": "https://anthropic.com/claude-code/plugin.schema.json",
  "name": "your-plugin-name",
  "description": "Claude Code plugin for a clearly bounded workflow.",
  "author": "your-handle",
  "commands": "./commands/",
  "skills": "./skills/",
  "agents": "./agents/"
}
```

When the plugin also ships a Codex-facing manifest, mirror the same fields in `.codex-plugin/plugin.json` and add the runtime-specific `interface` block there (not in `.claude-plugin/plugin.json`):

```json
{
  "name": "your-plugin-name",
  "description": "Claude Code plugin for a clearly bounded workflow.",
  "author": "your-handle",
  "skills": "./skills/",
  "agents": "./agents/",
  "interface": {
    "displayName": "Your Plugin",
    "shortDescription": "One-line summary for marketplace listings.",
    "longDescription": "Longer marketplace copy.",
    "developerName": "your-handle",
    "category": "Coding",
    "capabilities": ["Interactive", "Read"],
    "defaultPrompt": ["First representative prompt."],
    "websiteURL": "https://example.com/your-plugin"
  }
}
```

Add optional keys only when the plugin needs the corresponding runtime surface. For example:

```json
{
  "$schema": "https://anthropic.com/claude-code/plugin.schema.json",
  "name": "your-plugin-name",
  "description": "Claude Code plugin for a clearly bounded workflow.",
  "author": "your-handle",
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
- `agents/`: add when the plugin ships agents or subagents, and declare the matching `"agents": "./agents/"` manifest key in both runtime manifests
- `skills/`: add when the plugin ships reusable skills
- `hooks/`: add when the plugin must intercept or react to tool or session events
- `.mcp.json`: add when the plugin needs MCP server registrations
- `.lsp.json`: add when the plugin needs LSP server registrations
- `settings.json`: add when the plugin needs plugin-level settings
- `output-styles/`: add when the plugin ships reusable response formatting
- `monitors/`: add only for genuine monitor behavior, not as a default scaffold

If a plugin does not need a surface yet, omit both the file or directory and the manifest key.

## Optional runtime surfaces

Add optional surfaces only when the plugin genuinely needs that behavior. Omit both the manifest key and the filesystem artifact when the surface is not in use.

| Surface | Manifest key | When to add | Starter |
| --- | --- | --- | --- |
| Agents | `"agents"` | the plugin ships agents or subagents as a root-level directory | create `agents/` at the plugin root and set `"agents": "./agents/"` in both runtime manifests |
| Hooks | `"hooks"` | the plugin must react to Claude Code lifecycle events | copy `assets/hooks.json` + `assets/hooks/check.sh` |
| MCP | `"mcpServers"` | the plugin ships a local MCP server | copy `assets/.mcp.json` + `assets/servers/example-mcp.py` |
| LSP | `"lspServers"` | the plugin configures a language server | copy `assets/.lsp.json` + `assets/lsp/example-lsp.py` |
| Settings | `"settings"` | the plugin needs plugin-level settings | copy `assets/settings.json` |
| Output styles | `"outputStyles"` | the plugin ships reusable response formats | copy `assets/output-style.md` |
| Monitors | `"monitors"` | the plugin needs background observation | copy `assets/monitors.json` + `assets/monitors/watch.sh` |

Open `references/plugin-runtime-components.md` for per-surface extension points, tradeoffs, and deeper wiring guidance beyond the ordinary copy path above.

## Data boundary guidance

Keep these boundaries invariant across all plugin assets and starter files:

- `${CLAUDE_PLUGIN_ROOT}`: read-only for bundled scripts, templates, servers, and other shipped files
- `${CLAUDE_PLUGIN_DATA}`: writable only for generated caches, logs, indexes, or other persistent runtime data

Never treat `${CLAUDE_PLUGIN_ROOT}` as a writable data directory. Open `references/plugin-release.md` for the full example split and release-review context.

## Pitfalls

- Do not place component files inside `.claude-plugin/`.
- Do not declare paths that do not begin with `./`.
- Do not add every optional runtime file by default.
- Do not let `plugin.json` promise components that the tree does not contain.
- Do not treat `${CLAUDE_PLUGIN_ROOT}` as a writable data directory.
- Do not require support files to complete ordinary plugin authoring.

## First safe commands

Use these commands first when checking a real plugin root:

```bash
python3 -m json.tool .claude-plugin/plugin.json >/dev/null
```

The command above validates JSON syntax offline. For runtime validation with a live Claude Code installation, use:

```bash
claude --plugin-dir /absolute/path/to/your-plugin
```

This second command requires an online environment and is optional for ordinary offline authoring.

## Output contract

Return:

1. The plugin root tree.
2. The final `plugin.json`.
3. Every plugin-level config file created.
4. A short note explaining why each optional component exists.

## Optional support files

- Open `references/plugin-layout.md` when you need expanded tree examples for minimal, command-only, or full plugin roots.
- Open `references/plugin-runtime-components.md` when a plugin needs deeper per-surface examples, extension points, or local file layout beyond the ordinary copy path above.
- Open `references/plugin-release.md` when reviewing install scope, packaging, or release checks.
- Copy from `assets/plugin.json` for the minimal starter manifest.
- Copy the other files under `assets/` only when the matching optional surface is part of the plugin you are authoring.
