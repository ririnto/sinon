---
name: hook-authoring
description: >-
  Author Claude Code plugin hooks with lifecycle events, matchers, handler contracts, and security guardrails.
  Use when configuring hooks, filtering events, returning decisions, or packaging hook scripts in a plugin.
---

# Hook Authoring

Create Claude Code hooks that react to lifecycle events using only documented reload, matcher, and output behavior.

## Owned Surface

- `hooks/hooks.json` and bundled hook programs in a plugin
- `hooks` inside user or project `settings.json`
- event and matcher selection
- `command`, `http`, `mcp_tool`, `prompt`, and `agent` handlers
- event-specific input and output contracts
- `${CLAUDE_PLUGIN_ROOT}`, `${CLAUDE_PLUGIN_DATA}`, `${CLAUDE_PROJECT_DIR}`, and `CLAUDE_ENV_FILE`

## First Safe Checks

Inspect the current files before editing:

```sh
test -f hooks/hooks.json && python3 -m json.tool hooks/hooks.json
```

After editing, validate the plugin and inspect the loaded configuration:

```sh
claude plugin validate .
```

Run `/reload-plugins` in Claude Code, then use `/hooks` as a read-only browser to confirm the source, event, matcher, and handler that loaded.

## Configuration Shape

Both plugin hooks and settings hooks use a top-level `hooks` object.
Plugin `hooks/hooks.json` MAY also include `description`.

```json
{
  "description": "Validate file writes",
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Write|Edit",
        "hooks": [
          {
            "type": "command",
            "command": "bun",
            "args": ["${CLAUDE_PLUGIN_ROOT}/hooks/check-write.ts"],
            "timeout": 10
          }
        ]
      }
    ]
  }
}
```

The equivalent project setting keeps the same envelope:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "command": "${CLAUDE_PROJECT_DIR}/.claude/hooks/check-command.sh",
            "args": []
          }
        ]
      }
    ]
  }
}
```

Plugin-root `hooks/hooks.json` is auto-discovered.
Keep it out of `plugin.json` unless the plugin uses a custom hook path or inline hook configuration.

## Choose the Event

Use the narrowest lifecycle point that owns the decision:

| Need | Event |
| --- | --- |
| Inspect or rewrite a tool call before execution | `PreToolUse` |
| React to a successful or failed tool call | `PostToolUse`, `PostToolUseFailure` |
| React after a parallel tool batch | `PostToolBatch` |
| Add context before a prompt is processed | `UserPromptSubmit` |
| Validate whether the main agent or subagent may stop | `Stop`, `SubagentStop` |
| Initialize session context or environment | `SessionStart` |
| React to configuration, directory, or watched-file changes | `ConfigChange`, `CwdChanged`, `FileChanged` |

Open `references/lifecycle.md` for the full event inventory, matcher keys, and `CLAUDE_ENV_FILE` availability.

## Choose the Handler

- Use `command` for deterministic checks and event-specific JSON decisions.
- Use `http` when an existing HTTPS service owns the decision.
- Use `mcp_tool` when an already-connected MCP server owns the operation.
- Use `prompt` for a bounded semantic judgment that can be expressed as `{ "ok": true|false, "reason": "..." }`.
- Use `agent` only when the verifier needs tools or multi-step inspection.

All matching handlers run in parallel.
They MUST be independent, safe under concurrent execution, and free of ordering assumptions.

`prompt` and `agent` are supported only for `PermissionDenied`, `PermissionRequest`, `PostToolBatch`, `PostToolUse`, `PostToolUseFailure`, `PreToolUse`, `Stop`, `SubagentStop`, `TaskCompleted`, `TaskCreated`, `TeammateIdle`, `UserPromptExpansion`, and `UserPromptSubmit`.
`SessionStart` and `Setup` support only `command` and `mcp_tool`.
For other events, use `command`, `http`, or `mcp_tool` and verify the event-specific contract.

## Match Correctly

Matcher meaning depends on the event.
Tool events match `tool_name`; subagent events match agent type; session and configuration events have their own documented values.

Do not add `matcher` to events that do not support it.
Claude Code silently ignores matchers on `UserPromptSubmit`, `PostToolBatch`, `Stop`, `TeammateIdle`, `TaskCreated`, `TaskCompleted`, `WorktreeCreate`, `WorktreeRemove`, `MessageDisplay`, and `CwdChanged`.

For tool handlers, use the handler-level `if` field for argument-aware permission patterns such as `Bash(git *)` or `Edit(*.ts)`.
Treat `if` as a process-spawn optimization, not a hard security boundary; use the permission system and script-side validation for enforcement.

## Return the Event Contract

Command hooks receive JSON on stdin.
Exit code `0` with no output means no decision.
Exit code `2` uses plain stderr according to the event-specific exit-code behavior.
Structured JSON is optional and MUST use fields supported by that event.

A `PreToolUse` denial uses this shape:

```json
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "The target path is outside the project root."
  }
}
```

An allowed `PreToolUse` hook MAY include `updatedInput` under `hookSpecificOutput` to replace the tool input.
The replacement MUST satisfy the tool's input schema.

A `Stop` command hook blocks with:

```json
{
  "decision": "block",
  "reason": "The focused validation has not passed."
}
```

A prompt hook does not emit either command-hook shape.
Claude Code asks the model for:

```json
{
  "ok": false,
  "reason": "The requested write crosses the approved boundary."
}
```

Open `references/prompt-hooks.md` when using prompt or agent handlers or when an event needs finer decision control.

## Path and State Boundaries

- Use `${CLAUDE_PLUGIN_ROOT}` only for read-only bundled programs and assets.
- Use `${CLAUDE_PLUGIN_DATA}` for caches, logs, installed dependencies, and state that must survive plugin updates.
- Use `${CLAUDE_PROJECT_DIR}` for project-owned files.
- Prefer command exec form with `args` whenever a path placeholder is present.
- Resolve and contain user-supplied paths before reading or writing them.
- Treat hook input as untrusted JSON and validate required fields and types.
- Never print secrets or full environment snapshots.

`CLAUDE_ENV_FILE` is available only to `SessionStart`, `Setup`, `CwdChanged`, and `FileChanged` hooks.
Append shell-compatible environment assignments only when later Bash commands need them.

Open `references/security-patterns.md` for a copy-adaptable containment check.

## Reload and Verify

Changes to plugin hooks do not become live immediately.
Run `/reload-plugins` or restart Claude Code after editing plugin hooks.
Use `/hooks` to inspect the loaded hook; the menu does not edit or reload configuration.

Verification MUST cover:

- JSON and plugin validation
- one event that should match
- one event that should not match
- allow and block paths when the hook can decide
- paths containing spaces
- concurrent execution when multiple handlers can match
- absence of writes under `${CLAUDE_PLUGIN_ROOT}`

## Pitfalls

- Do not put lifecycle events directly at the top level of `settings.json`.
- Do not use a prompt handler on an unsupported event such as `PreCompact`.
- Do not assume handlers run sequentially.
- Do not require `jq` unless the bundled script explicitly declares and checks that dependency.
- Do not emit generic JSON and assume every event understands it.
- Do not use `/hooks` as a mutation or reload command.

## References

- `references/lifecycle.md` - open for the complete event inventory, matcher semantics, reload behavior, or environment-file timing.
- `references/prompt-hooks.md` - open for prompt and agent handler decisions or event-specific structured output.
- `references/security-patterns.md` - open when hook input controls filesystem, command, network, or credential-sensitive behavior.
- `references/performance.md` - open when a hook is slow, runs frequently, or needs asynchronous side effects.
