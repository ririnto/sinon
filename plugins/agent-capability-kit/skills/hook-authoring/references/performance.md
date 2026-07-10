---
description: >-
  Performance guidance for frequent, parallel, or side-effect-only Claude Code hooks.
---

# Hook Performance

Open this reference when a hook runs on a high-frequency event, invokes a model or network service, or writes persistent state.

## Cost Model

Every matching handler adds work at its lifecycle point, and matching handlers run in parallel.
Reduce cost in this order:

1. Choose the narrowest event.
2. Add a supported event matcher.
3. Add a handler-level `if` filter for tool arguments when useful.
4. Prefer a deterministic command over a model when the rule is mechanical.
5. Keep input parsing and output small.

Default handler timeouts are 600 seconds for `command`, `http`, and `mcp_tool`, 30 seconds for `prompt`, and 60 seconds for `agent`.
`UserPromptSubmit` lowers the first three defaults to 30 seconds; `MessageDisplay` lowers them to 10 seconds.
Set a shorter explicit timeout when the hook is on the interactive path.

```json
{
  "type": "command",
  "command": "bun",
  "args": ["${CLAUDE_PLUGIN_ROOT}/hooks/check-write.ts"],
  "timeout": 5
}
```

## State and Side Effects

Write generated state under `${CLAUDE_PLUGIN_DATA}`, never `${CLAUDE_PLUGIN_ROOT}`.
If concurrent handlers update the same file, use an atomic write or a process-safe store.
Keep logs bounded and omit secrets, full prompts, and environment snapshots.

Command handlers support `async: true` for non-blocking side effects.
Use it only when the handler does not need to influence the current event.
An asynchronous handler cannot return a decision to the operation that already continued.

## Verification

- Measure the matched and unmatched paths separately.
- Exercise multiple matching handlers at once.
- Confirm timeouts fail with the intended event behavior.
- Confirm persistent files are under `${CLAUDE_PLUGIN_DATA}` and remain valid after concurrent writes.
- Confirm a side-effect-only async hook is not expected to block or rewrite the current event.
