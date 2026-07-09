---
name: plugin-runtime-components
description: |-
  Per-surface extension points, tradeoffs, and wiring details for hooks, MCP, LSP, settings, output styles, themes, and monitors.
  Open this file only when `SKILL.md` leaves a blocker, tradeoff, or edge case unresolved for an already-chosen runtime surface.
---

# Plugin Runtime Components

Open this file only when `SKILL.md` leaves a blocker, tradeoff, or edge case unresolved for an already-chosen runtime surface.

## Hooks

Add detail here only when the simple matcher-and-script pattern is not enough, such as when the policy depends on repository helper code, multiple lifecycle events, or state that must be evaluated before a tool runs.

The starter hook in `assets/hooks.json` watches `Write|Edit` and runs `hooks/check.ts` with Bun.
The starter script reads the JSON payload from stdin and blocks likely secret-file edits (`.env`, `credentials.json`, `secrets`).
Extend it by changing the matcher, adding more hook groups, or replacing the Bun command with another local executable under `${CLAUDE_PLUGIN_ROOT}`.

Keep the enforcement path local to the plugin root.
If the check needs helper modules, bundle them with the plugin and invoke them from there rather than relying on ambient tools.

## MCP

The main blocker is usually state handling: caches, indexes, and logs belong under `${CLAUDE_PLUGIN_DATA}`, while bundled code and resources stay under `${CLAUDE_PLUGIN_ROOT}`.
If the server needs helper modules, keep them bundled with the plugin so the stdio entrypoint remains self-contained.

The starter `.mcp.json` uses `bun` plus a local script under `${CLAUDE_PLUGIN_ROOT}`.
The starter server (`assets/servers/example-mcp.ts`) exposes one example tool (`read_plugin_paths`) and keeps the transport local.
Extend it by renaming the server entry, adding more tools inside the TypeScript file, or adding arguments and environment variables while keeping generated state under `${CLAUDE_PLUGIN_DATA}`.

JavaScript and TypeScript runtime scripts that run directly should start with `#!/usr/bin/env bun`, then `// -*- coding: utf-8 -*-`.
Scripts that use only the JavaScript standard library or Bun built-ins need no dependency metadata block.

Only widen the server surface when the client actually needs it.
Advertising extra tools or environment inputs without a real consumer makes the plugin harder to reason about.

## Executables

Use `bin/` only for bundled executables that should be invokable as bare commands from Bash while the plugin is enabled.
Keep scripts that are only called by hooks, MCP servers, monitors, or documentation under the surface that owns them.

## LSP

The useful depth here is in capability negotiation and transport choice.
Add only the handlers the server can genuinely satisfy, and prefer stdio unless another transport is a hard requirement.

The starter `.lsp.json` uses `bun` plus a local stdio server (`assets/lsp/example-lsp.ts`).
The example server handles `initialize`, `shutdown`, `exit`, and a simple `textDocument/hover` response so you have a working shape to extend offline.
Replace the hover logic or add more LSP methods in the same file.

Avoid placeholder executables or names that do not correspond to bundled code.
The server entrypoint should point at something the plugin ships.

## Settings

Plugin-root `settings.json` is for Claude Code-supported default settings, not arbitrary plugin-owned runtime configuration.
Only documented allowlisted keys are applied; consult the Claude Code settings schema for the full key set, which includes keys such as `permissions`, `env`, `model`, `statusLine`, `outputStyle`, and `hooks`.

The asset (`assets/settings.json`) provides a minimal starting point.
Use `userConfig` for prompted plugin options.
Use bundled files for static plugin data.
Use `${CLAUDE_PLUGIN_DATA}` for generated or persistent plugin runtime data.
If the plugin does not need host default settings, remove `settings.json` rather than leaving a dead configuration surface behind.

## Output styles

Keep the file narrowly about output structure.
If style text starts to explain workflow, packaging, or runtime behavior, that material belongs back in plugin documentation.

Each file in `output-styles/` is a standalone style with frontmatter (`name`, `description`, and optionally `keep-coding-instructions`) and body instructions.
The `keep-coding-instructions` field tells the host whether to preserve coding-style instructions in the response.
Omit it or set it to `false` when the style applies to non-coding output.

Split styles when the response contract diverges.
Do not overload one style with incompatible audiences or responsibilities.

## Themes

Keep theme files limited to color theme data.
Each JSON file in `themes/` defines one read-only plugin theme that appears alongside built-in and user themes.
Use the `themes` key only for custom theme paths.

## Monitors

The tradeoff is operational overhead: monitors need a clear observed subsystem, persistent state only when necessary, and a reason to run in the background instead of reacting once.

The starter monitor (`assets/monitors/monitors.json` + `assets/monitors/watch.ts`) runs a local Bun script from `${CLAUDE_PLUGIN_ROOT}` and writes timestamped state under `${CLAUDE_PLUGIN_DATA}/monitor-state/`.
The monitor file is a top-level JSON array at the default `monitors/monitors.json` path.
Declare `monitors` only for a custom monitor path or inline monitor configuration.
Extend it by changing the command, adding more monitor entries, or replacing the Bun script with another local executable.

If the condition is transient, event-driven, or cheap to check on demand, prefer the simpler surface and leave monitors out.
