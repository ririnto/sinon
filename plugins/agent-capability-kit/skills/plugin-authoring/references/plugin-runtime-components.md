# Plugin Runtime Components

Open this file only when `SKILL.md` leaves a blocker, tradeoff, or edge case unresolved for an already-chosen runtime surface.

## Hooks

Add detail here only when the simple matcher-and-script pattern is not enough, such as when the policy depends on repo-local helper code, multiple lifecycle events, or state that must be evaluated before a tool runs.

Keep the enforcement path local to the plugin root. If the check needs helper modules, bundle them with the plugin and invoke them from there rather than relying on ambient shell tools.

## MCP

The main blocker is usually state handling: caches, indexes, and logs belong under `${CLAUDE_PLUGIN_DATA}`, while bundled code and resources stay under `${CLAUDE_PLUGIN_ROOT}`. If the server needs helper modules, keep them bundled with the plugin so the stdio entrypoint remains self-contained.

Only widen the server surface when the client actually needs it. Advertising extra tools or environment inputs without a real consumer makes the plugin harder to reason about.

## LSP

The useful depth here is in capability negotiation and transport choice. Add only the handlers the server can genuinely satisfy, and prefer stdio unless another transport is a hard requirement.

Avoid placeholder executables or names that do not correspond to bundled code. The server entrypoint should point at something the plugin ships.

## Settings

The blocker is usually ambiguity: if a value is only descriptive, documentation-like, or redundant with defaults, it does not belong here. Keep settings limited to knobs that the plugin actually reads.

If the plugin no longer depends on a setting, remove the file rather than leaving a dead configuration surface behind.

## Output styles

Keep the file narrowly about output structure. If style text starts to explain workflow, packaging, or runtime behavior, that material belongs back in plugin documentation.

Split styles when the response contract diverges. Do not overload one style with incompatible audiences or responsibilities.

## Monitors

The tradeoff is operational overhead: monitors need a clear observed subsystem, persistent state only when necessary, and a reason to run in the background instead of reacting once.

If the condition is transient, event-driven, or cheap to check on demand, prefer the simpler surface and leave monitors out.
