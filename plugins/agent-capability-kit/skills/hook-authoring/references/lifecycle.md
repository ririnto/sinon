---
description: >-
  Current Claude Code hook events, matcher keys, reload behavior, and environment timing.
---

# Hook Lifecycle

Open this reference when selecting an uncommon event, writing a matcher, or deciding when a changed plugin hook becomes live.

## Event Inventory

| Phase | Events |
| --- | --- |
| Setup and session | `Setup`, `SessionStart`, `SessionEnd` |
| Prompt and display | `UserPromptSubmit`, `UserPromptExpansion`, `MessageDisplay` |
| Tool loop | `PreToolUse`, `PermissionRequest`, `PermissionDenied`, `PostToolUse`, `PostToolUseFailure`, `PostToolBatch` |
| Agents and tasks | `SubagentStart`, `SubagentStop`, `TaskCreated`, `TaskCompleted`, `TeammateIdle` |
| Compaction and stopping | `PreCompact`, `PostCompact`, `Stop`, `StopFailure` |
| Runtime changes | `Notification`, `ConfigChange`, `InstructionsLoaded`, `CwdChanged`, `FileChanged` |
| Worktrees | `WorktreeCreate`, `WorktreeRemove` |
| MCP elicitation | `Elicitation`, `ElicitationResult` |

Choose an event because its input and decision contract match the job, not because its name sounds adjacent.

## Matcher Keys

Matcher evaluation is event-specific:

- tool events match `tool_name`
- `SubagentStart` and `SubagentStop` match agent type
- `SessionStart` and `Setup` match startup source
- `Notification` matches notification type
- `PreCompact` matches compaction trigger
- `ConfigChange` matches configuration source
- `InstructionsLoaded` matches load reason
- `FileChanged` matches watched filenames
- `UserPromptExpansion` matches the command name

These events have no matcher support and always fire on every occurrence: `UserPromptSubmit`, `PostToolBatch`, `Stop`, `TeammateIdle`, `TaskCreated`, `TaskCompleted`, `WorktreeCreate`, `WorktreeRemove`, `MessageDisplay`, and `CwdChanged`.
A matcher on those events is silently ignored.

For plugin MCP tools, match the scoped name:

```text
mcp__plugin_<plugin-name>_<server-name>__<tool-name>
```

A bare server key such as `mcp__database__.*` does not match a plugin-bundled server.

## Loading and Reloading

- A plugin's `hooks/hooks.json` loads when the plugin is enabled.
- Skill changes become live immediately, but plugin hook changes do not.
- Run `/reload-plugins` or restart Claude Code after changing plugin hooks.
- `/hooks` only inspects loaded configuration.
- A plugin update keeps existing hook processes on the previous plugin path until reload.

## Environment Timing

Use `${CLAUDE_PLUGIN_ROOT}` for bundled read-only files and `${CLAUDE_PLUGIN_DATA}` for persistent writable data.

`CLAUDE_ENV_FILE` is available only for:

- `SessionStart`
- `Setup`
- `CwdChanged`
- `FileChanged`

Values appended to that file become available to later Bash commands in the session.
Do not depend on it from other events.

## Lifecycle Checks

- Confirm the selected event can produce the intended decision.
- Confirm the event supports the chosen handler type.
- Confirm the matcher filters the documented input field.
- Exercise the hook after `/reload-plugins`.
- Verify repeated events and concurrent matching handlers do not corrupt shared state.
