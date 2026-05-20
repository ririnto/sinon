---
name: hook-authoring
description: Author Claude Code plugin hooks with matchers, lifecycle events, and security guardrails. Use this skill when authoring or refactoring `hooks/hooks.json` and hook scripts offline.
---

# Hook Authoring

Author hooks that intercept Claude Code events and enforce deterministic checks or LLM-driven policies.

## Goal

Create hooks that validate, block, modify, or react to tool use, agent stops, session events, and user input without live host dependencies.

## Scope

This skill owns hook authoring within `${CLAUDE_PLUGIN_ROOT}`:

- `hooks/hooks.json` (plugin hook configuration)
- `hooks/` hook script files
- `hooks/matchers` patterns (exact, wildcard, regex)
- Hook input and output JSON contracts
- Environment variable access (`${CLAUDE_PLUGIN_ROOT}`, `${CLAUDE_PROJECT_DIR}`, `$CLAUDE_ENV_FILE`)
- Security guardrails: input validation, path safety, variable quoting
- Hook lifecycle: session-start loading, no hot-swap, restart requirement

Hook configuration in `~/.claude/settings.json` uses direct JSON (not wrapped), while plugin hooks.json wraps events in `"hooks"` field.

## Operating Rules

1. Hooks are loaded at session start only; configuration changes require session restart.
2. Plugin `hooks.json` MUST use the wrapper format: `{"hooks": {"PreToolUse": [...], ...}}`.
3. User settings hooks MUST use direct format: `{"PreToolUse": [...], ...}` (no wrapper).
4. Prompt-based hooks SHOULD be the default for complex logic; command hooks SHOULD be used only for deterministic checks.
5. Hook scripts MUST quote all bash variables to prevent injection.
6. Hook scripts MUST validate all JSON inputs via `jq`.
7. All hooks MUST return valid JSON on stdout or stderr (exit 0 or 2 respectively).
8. Hook commands MUST use `${CLAUDE_PLUGIN_ROOT}` for portability; hardcoded paths are forbidden.
9. Sessions may run in remote context; command hooks MUST test for `$CLAUDE_CODE_REMOTE` when I/O safety matters.
10. Hooks run in parallel; design scripts as independent and non-blocking.

## Hook Types

### Prompt-Based (Recommended)

Context-aware LLM decision via natural language. Supports variable substitution (`$TOOL_INPUT`, `$TOOL_RESULT`, `$USER_PROMPT`).

```json
{
  "type": "prompt",
  "prompt": "Evaluate if this write is safe: $TOOL_INPUT. Check for path traversal (..), sensitive files (.env, .aws), and system paths. Return 'approve' or 'deny'.",
  "timeout": 30
}
```

Use for: policy enforcement, security reasoning, context-aware validation, completeness checks.

### Command-Based

Deterministic bash checks. Read JSON from stdin, return JSON on stdout/stderr.

```json
{
  "type": "command",
  "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/validate-write.sh",
  "timeout": 10
}
```

Use for: fast file checks, system calls, external integrations, performance-critical paths.

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

## Hook Events (9 Types)

Each event is triggered by specific Claude Code lifecycle moment. Use matchers to filter which tools or contexts trigger the hooks.

### PreToolUse

Execute before any tool runs. Validate, block, or modify tool input.

```json
{
  "PreToolUse": [
    {
      "matcher": "Write|Edit",
      "hooks": [
        {
          "type": "prompt",
          "prompt": "Check if file write is safe: $TOOL_INPUT. Deny .env, .aws, path traversal (..). Return 'approve' or 'deny'."
        }
      ]
    }
  ]
}
```

Input fields: `tool_name`, `tool_input`, `permission_mode`.
Output: `{"permissionDecision": "allow|deny|ask", "updatedInput": {...}, "systemMessage": "..."}`

### PostToolUse

Execute after tool completes. React to results, provide feedback, or log outcomes.

```json
{
  "PostToolUse": [
    {
      "matcher": "Bash",
      "hooks": [
        {
          "type": "prompt",
          "prompt": "Analyze command output for errors, warnings, or security issues: $TOOL_RESULT. Provide feedback."
        }
      ]
    }
  ]
}
```

Exit 0: stdout shown in transcript. Exit 2: stderr fed back to Claude.

### Stop

Execute when main agent considers stopping. Validate completeness.

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

Output: `{"decision": "approve|block", "reason": "...", "systemMessage": "..."}`

### SubagentStop

Execute when subagent considers stopping. Ensure subagent task is complete (same logic as Stop).

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

Execute when user submits a prompt. Add context, validate, or block user input.

```json
{
  "UserPromptSubmit": [
    {
      "matcher": "*",
      "hooks": [
        {
          "type": "prompt",
          "prompt": "User asked: $USER_PROMPT. If they mention security, auth, or API keys, add security guidance."
        }
      ]
    }
  ]
}
```

Input fields: `user_prompt`.

### SessionStart

Execute when Claude Code session begins. Load project context, set environment variables.

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

```bash
echo "export PROJECT_TYPE=nodejs" >> "$CLAUDE_ENV_FILE"
```

### SessionEnd

Execute when session ends. Cleanup, logging, state preservation.

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

Execute before context compaction. Add critical information to preserve.

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

Execute when Claude sends notifications. React to user notifications.

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
| Underscore | `"mcp__plugin_asana_.*"` | Specific plugin MCP tools |

Common patterns:

```json
"matcher": "Bash"                          // Bash only
"matcher": "Write|Edit"                    // File operations
"matcher": "mcp__.*"                       // All MCP tools
"matcher": "mcp__plugin_asana_.*"          // Asana MCP tools
```

Matchers are case-sensitive and match against the full tool name.

**Matcher evaluation precedence:** Exact-match patterns are tried first, then pipe-separated alternations left-to-right, then regex (as a fallback). Wildcard `*` matches unconditionally if no prior pattern matched. Configure more specific matchers first to ensure they take precedence over broader patterns.

## Hook Input/Output Contract

### Hook Input (JSON via stdin)

All hooks receive JSON with common fields:

```json
{
  "session_id": "abc123",
  "transcript_path": "/path/to/transcript.txt",
  "cwd": "/project/root",
  "permission_mode": "ask|allow",
  "hook_event_name": "PreToolUse"
}
```

Event-specific fields:

- `PreToolUse`/`PostToolUse` — `tool_name`, `tool_input`, `tool_result`
- `UserPromptSubmit` — `user_prompt`
- `Stop`/`SubagentStop` — `reason`

### Prompt Hook Variable Substitution

In prompt-type hooks, variables are substituted before sending to LLM:

- `$TOOL_INPUT` → JSON stringified tool input
- `$TOOL_RESULT` → JSON stringified tool result
- `$USER_PROMPT` → User's submitted prompt
- `$TOOL_NAME` → Name of the tool
- `${CLAUDE_PLUGIN_ROOT}` → Plugin root directory
- `${CLAUDE_PROJECT_DIR}` → Project directory

### Hook Output (JSON via stdout or stderr)

Standard return (all hooks):

```json
{
  "continue": true,
  "suppressOutput": false,
  "systemMessage": "Message shown to Claude"
}
```

PreToolUse output:

```json
{
  "permissionDecision": "allow|deny|ask",
  "updatedInput": {"field": "modified_value"},
  "systemMessage": "Reason for decision"
}
```

Stop/SubagentStop output:

```json
{
  "decision": "approve|block",
  "reason": "Why work should continue",
  "systemMessage": "Context for Claude"
}
```

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

Hook scripts run with elevated context. Apply these rules strictly:

1. MUST validate all JSON inputs via `jq` (use `--exit-status` to detect parse errors).
2. MUST reject path traversal (`..`), sensitive files (`.env`, `.aws`, `.pem`, `.key`), and system paths (`/bin`, `/usr`, `/etc`, `/sys`).
3. MUST quote all bash variables: `"$var"` not `$var`.
4. SHOULD set explicit timeouts: prompt 30s, command 60s.

See `references/security-patterns.md` for complete working examples, broken vs. correct comparisons, and testing strategies.

## Hook Lifecycle and Limitations

### Hooks Load at Session Start

Hooks are loaded when Claude Code session starts. Changes to hook configuration or scripts are NOT applied until the session is restarted.

### Cannot Hot-Swap Hooks

- Editing `hooks/hooks.json` has no effect on running session
- Adding or modifying hook scripts has no effect
- Must exit and restart Claude Code

### To Test Changes

1. Edit `hooks/hooks.json` or hook scripts
2. Exit Claude Code session
3. Restart: `claude` or `cc`
4. New configuration loads at startup
5. Test with `claude --debug` to see hook logs

### Hook Validation at Session Start

Claude Code validates hooks when session starts:

- Invalid JSON in `hooks.json` prevents hook loading
- Missing hook scripts generate warnings
- Use `/hooks` command (if available) to list loaded hooks in current session

## First Safe Commands

Validate hook configuration offline:

```bash
python3 -m json.tool hooks/hooks.json
```

This validates JSON syntax. If output shows no errors, the file is structurally valid.

Test a command hook script with sample input:

```bash
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

```bash
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
- Edit hooks.json and expect hot-reload (requires session restart).
- Trust user input without validation in command hooks.
- Run I/O-heavy operations in PreToolUse (blocks tool execution).

## References

For advanced patterns and edge cases, see:

- `references/prompt-hooks.md` — LLM-driven validation and context injection patterns
- `references/security-patterns.md` — Path validation, injection prevention, sensitive file detection
- `references/lifecycle.md` — Session lifecycle events, timing constraints, remote context handling
- `references/performance.md` — Parallel execution design, caching, timeout tuning
