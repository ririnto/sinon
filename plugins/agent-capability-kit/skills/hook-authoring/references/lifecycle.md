---
name: lifecycle
description: |-
  Session loading, timing constraints, environment variable scope, and conditional activation patterns for hooks.
---

# Hook Lifecycle: Session Loading and Timing

Open this reference when configuring hook timing, session lifecycle events, environment variable scope, or understanding when hook changes require session restart.

Hooks are loaded at session start only.
This reference covers exact session timing, when restart is required, environment variable scope, and conditional activation patterns.

## Session lifecycle overview

Claude Code session initialization:

1. User runs `claude` or `cc` command
2. Claude Code reads `hooks/hooks.json` from active plugins
3. Hooks are validated and loaded in memory
4. `SessionStart` hooks execute (if defined)
5. Session is ready for user input
6. During session: `PreToolUse`, `PostToolUse`, `Stop`, `UserPromptSubmit`, `PreCompact`, `Notification` hooks may fire
7. User runs `/exit` or exits session
8. `SessionEnd` hooks execute (if defined)
9. Session closes

## Critical rule: no hot-swap

Hooks loaded at step 3 remain fixed for the entire session.
Changes to `hooks/hooks.json` or hook scripts have ZERO effect until session restart.

### Broken: editing hook and expecting immediate effect

```text
User session running...
Edit hooks/hooks.json
Edit hooks/validate.sh
# Hook changes have NO effect on current session
Test hook
# Behavior unchanged
```

### Correct: restart requirement

```text
Edit hooks/hooks.json
Edit hooks/validate.sh
Exit Claude Code (or /exit)
Restart: claude or cc
Test hook
# New configuration is active
```

## Environment variables by event

### SessionStart: special access to $CLAUDE_ENV_FILE

Only `SessionStart` hooks can write to `$CLAUDE_ENV_FILE` to persist environment variables across the entire session.

```json
{
  "SessionStart": [
    {
      "matcher": "*",
      "hooks": [
        {
          "type": "command",
          "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/setup-env.sh"
        }
      ]
    }
  ]
}
```

Hook script (note: uses bash `printf %q` for safe quoting):

```sh
#!/usr/bin/env bash
# -*- coding: utf-8 -*-
set -e

# Initialize session environment from project config.
#
# @return Appends environment variables to CLAUDE_ENV_FILE.
setup_env() {
    if [ ! -f "$CLAUDE_ENV_FILE" ]; then
        echo "Error: CLAUDE_ENV_FILE not set" >&2
        exit 1
    fi
    PROJECT_TYPE=$(jq -r '.type' .project.config)
    printf 'export PROJECT_TYPE=%q\n' "$PROJECT_TYPE" >> "$CLAUDE_ENV_FILE"
    printf 'export PROJECT_ROOT=%q\n' "$(pwd)" >> "$CLAUDE_ENV_FILE"
}
setup_env
```

These variables are available to all subsequent hooks and tool execution in the session.

### All other events: no CLAUDE_ENV_FILE access

`PreToolUse`, `PostToolUse`, `Stop`, `UserPromptSubmit`, `PreCompact`, and `Notification` hooks CANNOT modify `$CLAUDE_ENV_FILE`.
They can only read environment variables set by:

- SessionStart hooks (via `$CLAUDE_ENV_FILE`)
- User's shell environment (passed at session start)
- Hooks themselves (but only local to that hook)

## Available variables by event

### All events

- `${CLAUDE_PLUGIN_ROOT}` - Plugin root directory
- `${CLAUDE_PROJECT_DIR}` - Project root directory (current working directory at session start)
- `$CLAUDE_CODE_REMOTE` - Set if running in remote context.
  - Undefined locally.

Values are constant for entire session.

### SessionStart only

- `$CLAUDE_ENV_FILE` - Path to session environment file (write-only).
  - Persist env vars here.

Available in SessionStart command hooks only.

### PreToolUse / PostToolUse / SubagentStop

All hook input fields accessible via `jq`:

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

tool_name=$(cat | jq -r '.tool_name')
tool_input=$(cat | jq -r '.tool_input')
cwd=$(cat | jq -r '.cwd')
printf 'Tool: %s, Input: %s, CWD: %s\n' "$tool_name" "$tool_input" "$cwd"
```

### UserPromptSubmit

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

user_prompt=$(cat | jq -r '.user_prompt')
echo "User prompt received: $user_prompt"
```

## Path resolution rules

### ${CLAUDE_PLUGIN_ROOT} resolution

Hook commands are evaluated in the project directory (`${CLAUDE_PROJECT_DIR}`).
Use full paths or relative-to-plugin:

```json
{
  "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/validate.sh"
}
```

Resolves to: `/path/to/plugin/hooks/validate.sh` (absolute path from environment variable)

### ${CLAUDE_PROJECT_DIR} resolution

Project directory is the working directory where Claude Code was invoked.
Use for relative path references:

```json
cd "${CLAUDE_PROJECT_DIR}" || exit
ls ./src
```

### Relative paths in hooks (avoid)

Relative paths are relative to current working directory, which is `${CLAUDE_PROJECT_DIR}`.
Avoid relying on implicit paths:

```sh
# Fragile: depends on current directory
bash hooks/validate.sh
# Robust: explicit path
bash "${CLAUDE_PLUGIN_ROOT}/hooks/validate.sh"
```

## Remote context handling

Claude Code can run in remote context (ssh, cloud environments).
Some operations are unsafe in remote context.

### Detect remote context

```sh
if [ -n "${CLAUDE_CODE_REMOTE:-}" ]; then
    echo "Running in remote context"
else
    echo "Running locally"
fi
```

### I/O safety in remote context

Some operations are unsafe or unavailable in remote:

- File I/O may be slow or unavailable
- System calls may fail (no access to `/dev`)
- tmux operations may fail (no terminal)

Pattern: conditional behavior based on context

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Log results with context-aware I/O.
#
# @return Local: writes file; Remote: outputs to stdout.
log_result() {
    message="$1"
    if [ -z "${CLAUDE_CODE_REMOTE:-}" ]; then
        echo "$message" >> "${CLAUDE_PLUGIN_ROOT}/logs/hook.log"
    else
        echo "$message"
    fi
}
```

## Temporarily active hooks (flag-file pattern)

Enable/disable hooks without restarting by using a flag file:

Hook configuration:

```json
{
  "PreToolUse": [
    {
      "matcher": "Write",
      "hooks": [
        {
          "type": "command",
          "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/conditional-validate.sh"
        }
      ]
    }
  ]
}
```

Hook script with flag file:

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Conditionally run validation based on flag file.
#
# @return Exits 0 if disabled; runs validation if enabled.
conditional_validate() {
    FLAG_FILE="${CLAUDE_PROJECT_DIR}/.hook-validation-enabled"
    if [ ! -f "$FLAG_FILE" ]; then
        exit 0
    fi
    INPUT=$(cat)
    TOOL_NAME=$(printf '%s' "$INPUT" | jq -r '.tool_name')
    if [ "$TOOL_NAME" != "Write" ]; then
        exit 0
    fi
    FILE_PATH=$(printf '%s' "$INPUT" | jq -r '.tool_input.file_path')
    if printf '%s' "$FILE_PATH" | grep -qE '\.(env|aws|pem|key)$'; then
        printf '{"permissionDecision": "deny", "systemMessage": "Sensitive file"}\n' >&2
        exit 2
    fi
    printf '{"permissionDecision": "allow"}\n'
    exit 0
}
conditional_validate
```

User can toggle validation:

```sh
touch .hook-validation-enabled      # Enable for this project
rm .hook-validation-enabled         # Disable for this project
# No session restart required
```

Hook still runs on every tool use (no restart needed), but early-exits if flag absent.

## SessionEnd: cleanup and state preservation

`SessionEnd` hooks run when session closes, before process termination.

Use for:

- Cleanup: removing temporary files
- Logging: recording final session state
- State preservation: saving agent progress for multi-session workflows

Example:

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

Script:

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Clean up session state and save logs.
#
# @return Removes temp files and archives logs.
cleanup() {
    rm -f "${CLAUDE_PROJECT_DIR}/.hook-temp-*"
    if [ -d "${CLAUDE_PLUGIN_ROOT}/logs" ]; then
        tar -czf "${CLAUDE_PLUGIN_ROOT}/logs/session-$(date +%s).tar.gz" \
            "${CLAUDE_PLUGIN_ROOT}/logs/hook.log"
        rm -f "${CLAUDE_PLUGIN_ROOT}/logs/hook.log"
    fi
}
cleanup
```

## Timeout and session end

If a hook times out:

- Hook is forcefully terminated
- Subsequent hooks may not run
- Session continues (tool may proceed depending on hook type)

Set reasonable timeouts:

```json
{
  "type": "command",
  "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/fast-check.sh",
  "timeout": 5
}
```

For `SessionEnd` hooks, timeouts are enforced at session close.
Hook MUST complete before process exits.

## Testing with session restart

Validate hook behavior with session restart:

```sh
# 1. Edit hooks/hooks.json or hook scripts

# 2. Exit current session
/exit

# 3. Restart
claude

# 4. Debug output
claude --debug
```

In debug output, look for:

```toml
[hooks] Loading hooks from .../hooks/hooks.json
[hooks] SessionStart: running 2 hooks
[hooks] PreToolUse: 1 matcher (Write)
```

If hooks fail to load, JSON syntax error or missing scripts will be reported.

## References

Refer to `SKILL.md` for hook event types and input/output contracts.

Refer to `references/security-patterns.md` for safe environment variable handling.

Refer to `references/performance.md` for parallel execution guarantees during a session.
