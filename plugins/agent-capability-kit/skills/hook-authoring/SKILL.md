---
name: hook-authoring
description: >-
  Author Claude Code plugin hooks with matchers, lifecycle events, and security guardrails.
  Triggers on PreToolUse or PostToolUse hook setup.
  Also triggers on event matcher configuration or safety boundaries around tool execution in a plugin.
---

# Hook Authoring

Author hooks that intercept Claude Code events and enforce deterministic checks or LLM-driven policies.

## Goal

Create hooks that validate, block, modify, or react to tool use.
Hooks may also handle agent stops, session events, and user input without live host dependencies.

## Scope

This skill owns hook authoring within `${CLAUDE_PLUGIN_ROOT}`:

- `hooks/hooks.json` (plugin hook configuration)
- `hooks/` hook script files
- `hooks/matchers` patterns (exact, wildcard, regex)
- Hook input and output JSON contracts
- Environment variable access (`${CLAUDE_PLUGIN_ROOT}`, `${CLAUDE_PROJECT_DIR}`, `$CLAUDE_ENV_FILE`)
- Security guardrails: input validation, path safety, variable quoting
- Hook lifecycle: load on plugin enable, settings reload behavior, restart to guarantee a change is live

Hook configuration in `~/.claude/settings.json` uses direct JSON.
Plugin `hooks.json` wraps events in the `"hooks"` field.

## Operating Rules

1. Plugin `hooks.json` is loaded when the plugin is enabled, typically at session start.
   - Settings-defined hooks are snapshotted at session start; mid-session edits require `/hooks` review to take effect.
   - Restart the session when a hook change must be guaranteed live.
2. Plugin `hooks.json` MUST use the wrapper format: `{"hooks": {"PreToolUse": [...], ...}}`.
3. User settings hooks MUST use direct format: `{"PreToolUse": [...], ...}` (no wrapper).
4. Plugin-root `hooks/hooks.json` is auto-discovered and SHOULD stay out of `plugin.json` when it is the only hook configuration.
   - Use `hooks` in `plugin.json` only for custom paths or inline hook configuration.
   - When `hooks` declares a string path, that file MUST exist.
5. Prompt-based hooks SHOULD be the default for complex logic.
   - Command hooks SHOULD be used only for deterministic checks.
6. Hook scripts MUST quote all bash variables to prevent injection.
7. Hook scripts MUST validate all JSON inputs via `jq`.
8. All hooks MUST return valid JSON on stdout or stderr (exit 0 or 2 respectively).
9. Hook commands MUST use `${CLAUDE_PLUGIN_ROOT}` for portability.
   - Hardcoded paths are forbidden.
10. Sessions may run in remote context.
    - Command hooks MUST test for `$CLAUDE_CODE_REMOTE` when I/O safety matters.
11. Hooks run in parallel.
    - Design scripts as independent and non-blocking.

## Hook Types

### Prompt-Based (Recommended)

Context-aware LLM decision via natural language.
The full hook input JSON is injected through the `$ARGUMENTS` placeholder.

```json
{
  "type": "prompt",
  "prompt": "Evaluate whether the write described in the hook input JSON ($ARGUMENTS) is safe. Check for path traversal (..), sensitive files (.env, .aws), and system paths. Return 'approve' or 'deny'.",
  "model": "claude-haiku-4-5-20251001",
  "timeout": 30
}
```

Use for: policy enforcement, security reasoning, context-aware validation, completeness checks.

### Command-Based

Deterministic bash checks.
Read JSON from stdin, return JSON on stdout/stderr.

```json
{
  "type": "command",
  "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/validate-write.sh",
  "timeout": 10
}
```

Use for: fast file checks, system calls, external integrations, performance-critical paths.

### Other Handler Types

Claude Code also supports `http`, `mcp_tool`, and `agent` hook handlers for advanced routing such as calling an HTTP endpoint, invoking an MCP tool, or delegating to an agent.
These are additive to the common `command` and `prompt` paths above.
Reach for them only when a deterministic command or an LLM prompt cannot express the check.

## Plugin hooks.json Format

Wrapper structure required for plugins:

```json
{
  "description": "Validation hooks for code safety",
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Write|Edit",
        "hooks": [
          {
            "type": "prompt",
            "prompt": "Validate write safety. Deny .env, .aws, system paths, and path traversal (..)."
          }
        ]
      }
    ],
    "Stop": [
      {
        "matcher": "*",
        "hooks": [
          {
            "type": "prompt",
            "prompt": "Verify task completion: tests pass, code builds, requirements met. Return 'approve' or 'block'."
          }
        ]
      }
    ],
    "SessionStart": [
      {
        "matcher": "*",
        "hooks": [
          {
            "type": "command",
            "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/load-project-context.sh"
          }
        ]
      }
    ]
  }
}
```

Key points:

- `description` is optional.
- `hooks` object is required wrapper.
- Each event is an array of matcher + hooks pairs.

## Settings.json Format (Direct, No Wrapper)

User settings `.claude/settings.json` use flat format:

```json
{
  "PreToolUse": [
    {
      "matcher": "Bash",
      "hooks": [
        {
          "type": "prompt",
          "prompt": "Confirm bash command safety before execution."
        }
      ]
    }
  ]
}
```

No wrapper, no description field, events at top level.

## Common Hook Events

Each event is triggered by specific Claude Code lifecycle moment.
Use matchers to filter which tools or contexts trigger the hooks.

### PreToolUse

Execute before any tool runs.
Validate, block, or modify tool input.

```json
{
  "PreToolUse": [
    {
      "matcher": "Write|Edit",
      "hooks": [
        {
          "type": "prompt",
          "prompt": "Check whether the file write in the hook input JSON ($ARGUMENTS) is safe. Deny .env, .aws, path traversal (..). Return 'approve' or 'deny'."
        }
      ]
    }
  ]
}
```

Input fields: `tool_name`, `tool_input` (plus common fields such as `permission_mode`).
Output: `{"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "allow|deny|ask", "permissionDecisionReason": "..."}, "systemMessage": "..."}`

### PostToolUse

Execute after tool completes.
React to results, provide feedback, or log outcomes.

```json
{
  "PostToolUse": [
    {
      "matcher": "Bash",
      "hooks": [
        {
          "type": "prompt",
          "prompt": "Analyze the command output in the hook input JSON ($ARGUMENTS) for errors, warnings, or security issues. Provide feedback."
        }
      ]
    }
  ]
}
```

Exit 0: stdout shown in transcript.
Exit 2: stderr fed back to Claude.

### Stop

Execute at the stop boundary.
Validate completeness.

```json
{
  "Stop": [
    {
      "matcher": "*",
      "hooks": [
        {
          "type": "prompt",
          "prompt": "Verify task is complete: tests pass, build succeeds, answer addresses the question. Return 'approve' to allow stop or 'block' with reason to continue work."
        }
      ]
    }
  ]
}
```

Output: `{"decision": "block", "reason": "Why work should continue"}` (omit `decision` to let the agent stop)

### SubagentStop

Execute when subagent considers stopping.
Ensure subagent task is complete (same logic as Stop).

```json
{
  "SubagentStop": [
    {
      "matcher": "*",
      "hooks": [
        {
          "type": "prompt",
          "prompt": "Verify subagent task completed successfully. Return 'approve' or 'block'."
        }
      ]
    }
  ]
}
```

### UserPromptSubmit

Execute when user submits a prompt.
Add context, validate, or block user input.

```json
{
  "UserPromptSubmit": [
    {
      "matcher": "*",
      "hooks": [
        {
          "type": "prompt",
          "prompt": "Read the submitted prompt in the hook input JSON ($ARGUMENTS). If it mentions security, auth, or API keys, add security guidance."
        }
      ]
    }
  ]
}
```

Input fields: `prompt`.

### SessionStart

Execute when Claude Code session begins.
Load project context, set environment variables.

```json
{
  "SessionStart": [
    {
      "matcher": "*",
      "hooks": [
        {
          "type": "command",
          "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/load-project-context.sh"
        }
      ]
    }
  ]
}
```

Special: command hooks can write to `$CLAUDE_ENV_FILE` to persist environment variables across the session:

```sh
echo "export PROJECT_TYPE=nodejs" >> "$CLAUDE_ENV_FILE"
```

### SessionEnd

Execute when session ends.
Cleanup, logging, state preservation.

```json
{
  "SessionEnd": [
    {
      "matcher": "*",
      "hooks": [
        {
          "type": "command",
          "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/cleanup.sh"
        }
      ]
    }
  ]
}
```

### PreCompact

Execute before context compaction.
Add critical information to preserve.

```json
{
  "PreCompact": [
    {
      "matcher": "*",
      "hooks": [
        {
          "type": "prompt",
          "prompt": "Identify essential information to preserve before context compaction."
        }
      ]
    }
  ]
}
```

### Notification

Execute when Claude sends notifications.
React to user notifications.

```json
{
  "Notification": [
    {
      "matcher": "*",
      "hooks": [
        {
          "type": "command",
          "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/log-notification.sh"
        }
      ]
    }
  ]
}
```

## Matchers

Match tools, events, or patterns to control which hooks fire.

| Pattern | Example | Behavior |
| --- | --- | --- |
| Exact | `"Write"` | Match Write tool only |
| Pipe | `"Write\|Edit\|Read"` | Match any in list (OR) |
| Wildcard | `"*"` | Match all tools/events |
| Regex | `"mcp__.*"` | Match MCP tools (greedy) |
| Underscore | `"mcp__asana__.*"` | Specific server MCP tools |

Common patterns:

```json
"matcher": "Bash"                          // Bash only
"matcher": "Write|Edit"                    // File operations
"matcher": "mcp__.*"                       // All MCP tools
"matcher": "mcp__asana__.*"               // Asana MCP tools
```

Matchers apply to `PreToolUse` and `PostToolUse` only and are case-sensitive.
A plain string matches the full tool name exactly (`Write` matches only the Write tool); regex is supported for broader patterns (`Edit|Write`, `Notebook.*`); `*`, an empty string, or a blank matcher matches every tool.

Every hook entry under an event whose matcher matches the current tool or event runs.
There is no precedence ordering between matcher entries: do not rely on a specific entry winning over a broader one.
If two entries can both match, both fire, so keep matchers disjoint or merge them into a single entry.

## Hook Input/Output Contract

### Hook Input (JSON via stdin)

All hooks receive JSON with common fields:

```json
{
  "session_id": "abc123",
  "transcript_path": "/path/to/transcript.txt",
  "cwd": "/project/root",
  "permission_mode": "default|acceptEdits|plan|bypassPermissions",
  "hook_event_name": "PreToolUse"
}
```

Event-specific fields:

- `PreToolUse` - `tool_name`, `tool_input`
- `PostToolUse` - `tool_name`, `tool_input`, `tool_response`
- `UserPromptSubmit` - `prompt`
- `Stop`/`SubagentStop` - `stop_hook_active`

### Prompt Hook Input

Prompt-type hooks receive the full hook event JSON through the `$ARGUMENTS` placeholder.
The fields documented under Hook Input live inside that JSON: `tool_name`, `tool_input`, and `tool_response` for tool events, and `prompt` for `UserPromptSubmit`.
The `prompt` field is the instruction the LLM evaluates against that JSON.
`${CLAUDE_PLUGIN_ROOT}` and `${CLAUDE_PROJECT_DIR}` are expanded in the prompt text for portable path references.

### Hook Output (JSON via stdout or stderr)

Standard return (all hooks):

```json
{
  "continue": true,
  "suppressOutput": false,
  "systemMessage": "Message shown to Claude"
}
```

PreToolUse output (nest the decision under `hookSpecificOutput`):

```json
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "allow|deny|ask",
    "permissionDecisionReason": "Reason for decision"
  },
  "systemMessage": "Reason shown to Claude"
}
```

Stop/SubagentStop output (use top-level `decision` and `reason`):

```json
{
  "decision": "block",
  "reason": "Why work should continue"
}
```

Omit `decision` to let the agent stop.
`continue: false` stops the agent outright and takes precedence over any `decision: "block"`.

Exit codes:

- `0` - Success (stdout shown in transcript)
- `2` - Blocking error (stderr fed back to Claude)
- Other - Non-blocking error

## Environment Variables

Available in command hooks:

- `${CLAUDE_PLUGIN_ROOT}` - Plugin root directory (use for portable paths)
- `${CLAUDE_PROJECT_DIR}` - Project root directory
- `$CLAUDE_ENV_FILE` - SessionStart only: persist env vars here
- `$CLAUDE_CODE_REMOTE` - Set if running in remote context

Always use `${CLAUDE_PLUGIN_ROOT}` in hook commands:

```json
{
  "type": "command",
  "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/validate.sh"
}
```

## Security Rules

Hook scripts run with elevated context.
Apply these rules strictly:

1. MUST validate all JSON inputs via `jq` (use `--exit-status` to detect parse errors).
2. MUST reject unsafe paths:
   - Path traversal (`..`).
   - Sensitive files (`.env`, `.aws`, `.pem`, `.key`).
   - System paths (`/bin`, `/usr`, `/etc`, `/sys`).
3. MUST quote all bash variables: `"$var"` not `$var`.
4. SHOULD set explicit timeouts: prompt 30s, command 60s.

See `references/security-patterns.md` for complete working examples.
Also see it for broken-vs-correct comparisons and testing strategies.

## Hook Lifecycle and Reload

### When Hooks Load

- Plugin `hooks/hooks.json` is loaded when the plugin is enabled, typically at session start.
- Hooks defined in `settings.json` are snapshotted at session start; mid-session edits require `/hooks` review before they take effect.

### Applying Changes

- Edits to plugin `hooks.json` take effect on the next plugin load; restart the session to be certain.
- Edits to a command hook script take effect the next time the hook fires, because command hooks invoke the script fresh each run.
- When a configuration change must be live, restart the session rather than relying on reload timing.

### To Test Changes

1. Edit `hooks/hooks.json` or hook scripts.
2. Restart the session (`claude` or `cc`) so plugin configuration reloads.
3. Run `claude --debug` to see hook execution logs.

### Hook Validation at Session Start

Claude Code validates hooks when session starts:

- Invalid JSON in `hooks.json` prevents hook loading
- Missing hook scripts generate warnings
- Use the `/hooks` command to list loaded hooks in the current session

## First Safe Commands

Validate hook configuration offline:

```sh
python3 -m json.tool hooks/hooks.json
```

This validates JSON syntax.
If output shows no errors, the file is structurally valid.

Test a command hook script with sample input:

```sh
cat > /tmp/test-input.json << 'EOF'
{
  "session_id": "test",
  "tool_name": "Write",
  "tool_input": {"file_path": "/tmp/test.txt"},
  "hook_event_name": "PreToolUse"
}
EOF

bash hooks/validate.sh < /tmp/test-input.json
```

Verify output is valid JSON:

```sh
bash hooks/validate.sh < /tmp/test-input.json | python3 -m json.tool
```

## Output Contract

Return:

1. The final `hooks/hooks.json` with complete event and matcher configuration.
2. All hook script files created in `hooks/`.
3. Integration notes: which hooks fire on which events, matchers used, and expected side effects.
4. Any environment variables set via `$CLAUDE_ENV_FILE` in SessionStart hooks.

## Pitfalls

### DO

- Use prompt-based hooks for complex policy logic.
- Use `${CLAUDE_PLUGIN_ROOT}` in all hook paths.
- Quote all bash variables: `"$var"` not `$var`.
- Validate JSON inputs with `jq`.
- Test hooks with `claude --debug`.
- Set explicit timeouts on long-running checks.

### DON'T

- Use hardcoded file paths in hook commands.
- Assume hook execution order (hooks run in parallel).
- Rely on side effects between hooks.
- Create long-running hooks (> 30s for prompt, > 60s for command).
- Log or expose sensitive data in hook output.
- Assume a plugin `hooks.json` edit is live without restarting (settings-defined hooks reload, but plugin `hooks.json` reloads on plugin load).
- Trust user input without validation in command hooks.
- Run I/O-heavy operations in PreToolUse (blocks tool execution).

## References

For advanced patterns and edge cases, see:

- `references/prompt-hooks.md` - LLM-driven validation and context injection patterns
- `references/security-patterns.md` - Path validation, injection prevention, sensitive file detection
- `references/lifecycle.md` - Session lifecycle events, timing constraints, remote context handling
- `references/performance.md` - Parallel execution design, caching, timeout tuning
