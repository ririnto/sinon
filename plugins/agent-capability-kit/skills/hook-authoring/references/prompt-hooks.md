---
description: >-
  Prompt and agent hook response behavior plus event-specific command-hook decision shapes.
---

# Prompt and Decision Hooks

Open this reference when a hook needs semantic evaluation, tool-using verification, or structured decision control beyond an exit code.

## Prompt Handler

Use `$ARGUMENTS` to place the input JSON in the prompt.
If it is absent, Claude Code appends the input automatically.

```json
{
  "hooks": {
    "Stop": [
      {
        "hooks": [
          {
            "type": "prompt",
            "prompt": "Decide whether the requested task is complete: $ARGUMENTS",
            "timeout": 30
          }
        ]
      }
    ]
  }
}
```

The model returns:

```json
{
  "ok": false,
  "reason": "The requested verification has not run."
}
```

`reason` is required when `ok` is `false`.
Claude Code translates the result according to the event.
For `PreToolUse`, false denies the tool; for `Stop` and `SubagentStop`, false feeds the reason back and continues work.
For `PermissionRequest` and `PermissionDenied`, prompt rejection does not perform the event-specific deny or retry action; use a command hook for that control.

## Agent Handler

Use `type: "agent"` only when the verifier must inspect files or perform multiple steps.
The handler uses the same `prompt` and optional `model` fields and returns the same `{ "ok", "reason" }` schema.
Bound its prompt, timeout, and expected evidence so the verifier does not expand scope.

## Command Decisions

Command-hook JSON is event-specific.
Do not reuse one shape across events.

Allow a `PreToolUse` call while replacing its input:

```json
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "allow",
    "permissionDecisionReason": "The normalized target remains inside the project.",
    "updatedInput": {
      "file_path": "/workspace/src/example.ts",
      "content": "export const value = 1;\n"
    }
  }
}
```

The replacement input MUST validate against the selected tool's input schema.

Block stopping:

```json
{
  "decision": "block",
  "reason": "Focused tests are still failing."
}
```

For context-only events, return the documented `hookSpecificOutput.additionalContext` shape rather than a block decision.
For side-effect-only events, emit no decision JSON.

## Concurrency

All matching handlers run in parallel.
Do not rely on one prompt or command result being available to another handler.
If multiple checks jointly own one decision, combine them into one handler or make each independently safe.
