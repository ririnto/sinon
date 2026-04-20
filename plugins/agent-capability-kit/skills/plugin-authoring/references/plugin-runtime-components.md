# Plugin Runtime Components

Open this file only when `SKILL.md` leaves a blocker, tradeoff, or edge case unresolved for an already-chosen runtime surface.

## Hooks

Add detail here only when the simple matcher-and-script pattern is not enough, such as when the policy depends on repo-local helper code, multiple lifecycle events, or state that must be evaluated before a tool runs.

The starter hook in `assets/hooks.json` watches `Write|Edit` and runs `hooks/check.sh`. The starter script reads the JSON payload from stdin and blocks likely secret-file edits (`.env`, `credentials.json`, `secrets`). Extend it by changing the matcher, adding more hook groups, or replacing the shell command with another local executable under `${CLAUDE_PLUGIN_ROOT}`.

Keep the enforcement path local to the plugin root. If the check needs helper modules, bundle them with the plugin and invoke them from there rather than relying on ambient shell tools.

## MCP

The main blocker is usually state handling: caches, indexes, and logs belong under `${CLAUDE_PLUGIN_DATA}`, while bundled code and resources stay under `${CLAUDE_PLUGIN_ROOT}`. If the server needs helper modules, keep them bundled with the plugin so the stdio entrypoint remains self-contained.

The starter `.mcp.json` uses `python3` plus a local script under `${CLAUDE_PLUGIN_ROOT}`. The starter server (`assets/servers/example-mcp.py`) exposes one example tool (`read_plugin_paths`) and keeps the transport local. Extend it by renaming the server entry, adding more tools inside the Python file, or adding arguments and environment variables while keeping generated state under `${CLAUDE_PLUGIN_DATA}`.

Only widen the server surface when the client actually needs it. Advertising extra tools or environment inputs without a real consumer makes the plugin harder to reason about.

## LSP

The useful depth here is in capability negotiation and transport choice. Add only the handlers the server can genuinely satisfy, and prefer stdio unless another transport is a hard requirement.

The starter `.lsp.json` uses `python3` plus a local stdio server (`assets/lsp/example-lsp.py`). The example server handles `initialize`, `shutdown`, `exit`, and a simple `textDocument/hover` response so you have a working shape to extend offline. Replace the hover logic or add more LSP methods in the same file.

Avoid placeholder executables or names that do not correspond to bundled code. The server entrypoint should point at something the plugin ships.

## Settings

The blocker is usually ambiguity: if a value is only descriptive, documentation-like, or redundant with defaults, it does not belong here. Keep settings limited to knobs that the plugin actually reads.

The asset (`assets/settings.json`) provides a minimal starting point. Replace the placeholder values with only the keys the plugin genuinely needs at runtime. If the plugin no longer depends on a setting, remove the file rather than leaving a dead configuration surface behind.

## Output styles

Keep the file narrowly about output structure. If style text starts to explain workflow, packaging, or runtime behavior, that material belongs back in plugin documentation.

Each file in `output-styles/` is a standalone style with frontmatter (`name`, `description`, and optionally `keep-coding-instructions`) and body instructions. The `keep-coding-instructions` field tells the host whether to preserve coding-style instructions in the response; omit it or set it to `false` when the style applies to non-coding output.

Split styles when the response contract diverges. Do not overload one style with incompatible audiences or responsibilities.

## Monitors

The tradeoff is operational overhead: monitors need a clear observed subsystem, persistent state only when necessary, and a reason to run in the background instead of reacting once.

The starter monitor (`assets/monitors.json` + `assets/monitors/watch.sh`) runs a local shell script from `${CLAUDE_PLUGIN_ROOT}` and writes timestamped state under `${CLAUDE_PLUGIN_DATA}/monitor-state/`. Extend it by changing the command, adding more monitor entries, or replacing the shell script with another local executable.

If the condition is transient, event-driven, or cheap to check on demand, prefer the simpler surface and leave monitors out.
