---
name: prompt-hooks
description: |-
  LLM-driven validation patterns for prompt-based hooks with context-aware reasoning and natural-language examples.
---

# Prompt-Based Hooks: LLM-Driven Validation

Open this reference when implementing LLM-driven validation, using variable substitution in prompts, injecting policies into hooks, or designing multi-step decision flows.

See `SKILL.md` for basic hook structure and the 9 event types.

## Variable substitution in prompts

Prompt hooks receive the full hook event JSON through the `$ARGUMENTS` placeholder, plus two inline path expansions:

- `$ARGUMENTS` - The complete hook event JSON the model evaluates against. It carries common fields (`session_id`, `cwd`, `permission_mode`, `hook_event_name`) and event-specific fields: `tool_name` and `tool_input` for tool events, `tool_response` for `PostToolUse`, and `prompt` for `UserPromptSubmit`.
- `${CLAUDE_PLUGIN_ROOT}` - Plugin root directory path (e.g., `/project/.claude/plugins/my-plugin`)
- `${CLAUDE_PROJECT_DIR}` - Project root directory path

Ask the model to read specific fields out of `$ARGUMENTS`; there is no `$TOOL_INPUT`, `$TOOL_RESULT`, or `$USER_PROMPT` placeholder, because those values live inside the `$ARGUMENTS` JSON.
Complex escaping MUST be handled in the prompt text itself.

## Decision output schemas

These shapes mirror the contracts in `SKILL.md` and apply to prompt hooks and command hooks alike.

### PreToolUse decision output

Nest the decision under `hookSpecificOutput`:

```json
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "allow|deny|ask",
    "permissionDecisionReason": "Decision rationale"
  },
  "systemMessage": "Reason shown to Claude"
}
```

- `permissionDecision` (string) - `allow` (proceed unchanged), `deny` (block), `ask` (prompt user)
- `permissionDecisionReason` (string) - Why the decision was made
- `systemMessage` (string, optional) - Reasoning shown in Claude's transcript

### PostToolUse feedback output

```json
{
  "decision": "block",
  "reason": "Why feedback is needed"
}
```

- `decision` (string) - Omit for non-blocking feedback; set to `block` to feed the reason back to Claude
- `reason` (string) - Feedback shown to Claude when blocking

### Stop / SubagentStop decision output

Use a top-level `decision` and `reason`:

```json
{
  "decision": "block",
  "reason": "Why work should continue"
}
```

- Omit `decision` to let the agent stop; set it to `block` to force continued work
- `reason` (string) - Describes the unfinished work when blocking
- `continue: false` stops the agent outright and takes precedence over any `decision: "block"`

## Policy Injection Pattern

Read project policy from `.claude/{{plugin-name}}.local.md` frontmatter and apply it where the decision is made.
Prompt hooks do not inherit shell environment variables exported by command hooks, so a command hook emits its own decision, and a prompt hook applies policy through text embedded in the prompt.

### Command Hook Example

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Load plugin configuration and emit a permission decision.
#
# @return Outputs a JSON hook decision based on the policy file.
POLICY_FILE="${CLAUDE_PLUGIN_ROOT}/.claude/plugin-name.local.md"
if [ ! -f "$POLICY_FILE" ]; then
  echo '{"permissionDecision": "allow", "systemMessage": "No policy found"}' >&2
  exit 0
fi
FRONTMATTER=$(sed -n '/^---$/,/^---$/{ /^---$/d; p; }' "$POLICY_FILE")
VALIDATION_LEVEL=$(echo "$FRONTMATTER" | grep '^validation_level:' | sed 's/validation_level: *//' || echo "strict")
if [ "$VALIDATION_LEVEL" = "strict" ]; then
  echo '{"permissionDecision": "ask", "permissionDecisionReason": "strict policy active"}' >&2
  exit 0
fi
echo '{"permissionDecision": "allow"}' >&2
```

### Prompt Hook Example

A prompt hook reads the tool input from `$ARGUMENTS` and applies the policy in natural language:

```json
{
  "type": "prompt",
  "prompt": "Apply the project's strict validation policy. Read the tool input from $ARGUMENTS, check it, and decide allow or deny."
}
```

## Timeout and failure modes

Prompt hooks have LLM latency (2-10 seconds typical).
Timeout is in seconds (integer, default 30, min 10, max 120).

If timeout expires or LLM response does not match the expected output schema, the hook is treated as a no-op (tool/event proceeds unchanged).
Use `claude --debug` to see parse errors.

## Multi-step decision flow

For complex policies, break decisions into multiple hooks on the same event:

```json
{
  "PreToolUse": [
    {
      "matcher": "Write",
      "hooks": [
        {
          "type": "prompt",
          "prompt": "Check basic path safety using the tool input in $ARGUMENTS. Deny path traversal (..) and system paths.",
          "timeout": 10
        },
        {
          "type": "prompt",
          "prompt": "Check sensitive files using the tool input in $ARGUMENTS. Deny .env, .aws, .git.",
          "timeout": 10
        }
      ]
    }
  ]
}
```

Each hook runs sequentially.
If any hook denies, the tool is blocked.

## Concrete examples

### Security policy with multi-rule chaining

```json
{
  "PreToolUse": [
    {
      "matcher": "Write|Edit",
      "hooks": [
        {
          "type": "prompt",
          "prompt": "Check path safety using the tool input in $ARGUMENTS. Deny (..), /bin, /usr, /etc, /sys. Deny .env, .aws, .pem, .key. Return allow or deny only.",
          "timeout": 10
        },
        {
          "type": "prompt",
          "prompt": "Check file content policy using the tool input in $ARGUMENTS. Deny node_modules override, hardcoded secrets, root-level config changes. Return allow or deny.",
          "timeout": 10
        }
      ]
    }
  ]
}
```

Each hook runs sequentially.
If any denies, tool is blocked.

### Completeness validation at Stop

```json
{
  "Stop": [
    {
      "matcher": "*",
      "hooks": [
        {
          "type": "prompt",
          "prompt": "Review the stop hook event in $ARGUMENTS. Verify whether the work is complete: tests pass, code style consistent, all requirements met. Return approve or block with reason.",
          "timeout": 30
        }
      ]
    }
  ]
}
```

Claude evaluates context and decides whether work is truly complete.
