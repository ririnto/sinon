---
name: plugin-runtime-components
description: >-
  Wiring and boundaries for already-selected Claude Code plugin runtime components.
---

# Plugin Runtime Components

Open this reference only after deciding that the plugin genuinely needs one of these surfaces.

## Hooks

Plugin `hooks/hooks.json` MUST contain a top-level `hooks` object.
Use exec-form commands with `args` for bundled paths.
Copy `assets/hooks.json` and `assets/hooks/check.ts` together, then adapt the matcher and event-specific output.
Run `/reload-plugins` after changes.

## MCP

Plugin `.mcp.json` MUST contain a top-level `mcpServers` object.
Copy `assets/.mcp.json` with `assets/servers/example-mcp.ts` for a local stdio starter.
Stdio MCP uses one JSON-RPC message per line on stdin and stdout, without LSP `Content-Length` framing.
Bundled server code belongs under `${CLAUDE_PLUGIN_ROOT}`; generated server data belongs under `${CLAUDE_PLUGIN_DATA}`.

## LSP

Copy `assets/.lsp.json` with `assets/lsp/example-lsp.ts` only when the plugin provides real language intelligence.
Advertise only capabilities the server implements and keep protocol traffic on stdout free of diagnostics.

## Settings

Plugin-root `settings.json` currently supports only:

- `agent`: the name of an agent to run as the main thread
- `subagentStatusLine`: a command configuration that renders subagent rows

The starter `assets/settings.json` demonstrates the `agent` key and MUST name an agent the target plugin ships or resolves.
Delete the file if neither supported key is needed.

Manifest `settings` MAY provide the same allowlisted settings inline.
Use manifest `userConfig` for values Claude Code prompts users to enter.

## Output Styles

Each file under `output-styles/` is a standalone Markdown style.
Keep it about output structure; workflow instructions belong in a skill or agent.

## Themes

Default theme files live under `themes/`.
Use `experimental.themes` for custom paths.
Top-level manifest `themes` is deprecated.

## Monitors

Default monitor configuration lives at `monitors/monitors.json`.
Use `experimental.monitors` for custom paths or inline declarations.
Top-level manifest `monitors` is deprecated.
Each monitor object requires `name`, `command`, and `description`.
The command MUST remain running for the intended monitoring lifetime and write notification lines to stdout.

Monitors that write state MUST use `${CLAUDE_PLUGIN_DATA}`.
After a plugin update, monitors require a session restart to switch to the new plugin path.

## Executables

Executable files under `bin/` are added to Bash `PATH` while the plugin is enabled.
Keep programs used only by hooks, MCP, LSP, or monitors under their owning surface instead.
