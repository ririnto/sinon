---
name: prompt-hooks
description: |-
  LLM-driven validation patterns for prompt-based hooks with context-aware reasoning and natural-language examples.
---

# Prompt-Based Hooks: LLM-Driven Validation

Open this reference when implementing LLM-driven validation, using variable substitution in prompts, injecting policies into hooks, or designing multi-step decision flows.

See `SKILL.md` for basic hook structure and the 9 event types.

## Variable substitution in prompts

Prompt hooks support these inline variable expansions before sending to LLM:

- `$TOOL_NAME` — Tool being invoked (e.g., `Write`, `Edit`, `Bash`)
- `$TOOL_INPUT` — JSON stringified tool input parameters
- `$TOOL_RESULT` — JSON stringified tool output (PostToolUse only)
- `$USER_PROMPT` — User's submitted prompt text (UserPromptSubmit only)
- `${CLAUDE_PLUGIN_ROOT}` — Plugin root directory path (e.g., `/project/.claude/plugins/my-plugin`)
- `${CLAUDE_PROJECT_DIR}` — Project root directory path

Variables are substituted literally; complex escaping MUST be handled in the prompt text itself. Tool inputs are already JSON, so quoting is handled automatically.

## Decision output schemas

### PreToolUse decision output

```json
{
  "permissionDecision": "allow|deny|ask",
  "updatedInput": {"field": "corrected_value"},
  "systemMessage": "Decision rationale"
}
```text

- `permissionDecision` (string) — `allow` (proceed unchanged), `deny` (block), `ask` (prompt user)
- `updatedInput` (object, optional) — Modified tool parameters; if present, tool uses this instead of original
- `systemMessage` (string) — Reasoning shown in Claude's transcript

### PostToolUse feedback output

```json
{
  "continue": true,
  "suppressOutput": false,
  "systemMessage": "Feedback or warning"
}
```text

- `continue` (boolean) — Always true for PostToolUse; signals to continue session
- `suppressOutput` (boolean) — If true, omit tool result from transcript
- `systemMessage` (string) — Feedback shown to Claude

### Stop decision output

```json
{
  "decision": "approve|block",
  "reason": "Work remaining (if block)",
  "systemMessage": "Context for Claude"
}
```text

- `decision` (string) — `approve` (allow stop), `block` (continue working)
- `reason` (string) — If blocked, describe unfinished work
- `systemMessage` (string) — Context for Claude

## Policy Injection Pattern

Read project policy from `.claude/<plugin-name>.local.md` frontmatter and pass to prompt hooks as environment variables or inline text.

### Command Hook Example

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Load plugin configuration and write policy to stdout.
#
# @return Outputs JSON hook decision with injected policy.
POLICY_FILE="${CLAUDE_PLUGIN_ROOT}/.claude/plugin-name.local.md"
if [ ! -f "$POLICY_FILE" ]; then
  echo '{"permissionDecision": "allow", "systemMessage": "No policy found"}' >&2
  exit 0
fi
FRONTMATTER=$(sed -n '/^---$/,/^---$/{ /^---$/d; p; }' "$POLICY_FILE")
VALIDATION_LEVEL=$(echo "$FRONTMATTER" | grep '^validation_level:' | sed 's/validation_level: *//' || echo "strict")
export VALIDATION_LEVEL
# Prompt hook runs with $VALIDATION_LEVEL set
```text

Then reference in prompt:

```json
{
  "type": "prompt",
  "prompt": "Apply validation_level=$VALIDATION_LEVEL policy. Check: $TOOL_INPUT. Decide allow or deny."
}
```text

## Timeout and failure modes

Prompt hooks have LLM latency (2-10 seconds typical). Timeout is in seconds (integer, default 30, min 10, max 120).

If timeout expires or LLM response does not match the expected output schema, the hook is treated as a no-op (tool/event proceeds unchanged). Use `claude --debug` to see parse errors.

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
          "prompt": "Check basic path safety: $TOOL_INPUT. Deny path traversal (..) and system paths.",
          "timeout": 10
        },
        {
          "type": "prompt",
          "prompt": "Check sensitive files: $TOOL_INPUT. Deny .env, .aws, .git.",
          "timeout": 10
        }
      ]
    }
  ]
}
```text

Each hook runs sequentially. If any hook denies, the tool is blocked.

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
          "prompt": "Check path safety: $TOOL_INPUT. Deny (..), /bin, /usr, /etc, /sys. Deny .env, .aws, .pem, .key. Return allow or deny only.",
          "timeout": 10
        },
        {
          "type": "prompt",
          "prompt": "Check file content policy: $TOOL_INPUT. Deny node_modules override, hardcoded secrets, root-level config changes. Return allow or deny.",
          "timeout": 10
        }
      ]
    }
  ]
}
```text

Each hook runs sequentially; if any denies, tool is blocked.

### Completeness validation at Stop

```json
{
  "Stop": [
    {
      "matcher": "*",
      "hooks": [
        {
          "type": "prompt",
          "prompt": "User goal: $USER_PROMPT\n\nWork done: $TOOL_RESULT\n\nVerify: tests pass, code style consistent, all requirements met. Return approve or block with reason.",
          "timeout": 30
        }
      ]
    }
  ]
}
```text

Claude evaluates context and decides whether work is truly complete.
