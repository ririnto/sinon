---
name: performance
description: |-
  Parallel execution model, timeout tuning, caching patterns, hot-path optimization, and debugging strategies for hooks.
---

# Hook Performance: Execution Model, Caching, and Debugging

Open this reference when tuning hook timeouts, implementing caching patterns, optimizing hot-path hooks for latency, or debugging hook execution behavior.

Hooks run in parallel by default with no guaranteed order. This reference covers execution semantics, timeout tuning, caching patterns, hot-path optimization, and debugging strategies.

## Parallel execution model

Multiple hooks on the same event fire simultaneously:

```json
{
  "PreToolUse": [
    {
      "matcher": "Write",
      "hooks": [
        {"type": "command", "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/check-path.sh"},
        {"type": "command", "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/check-permissions.sh"},
        {"type": "prompt", "prompt": "Validate write safety: $TOOL_INPUT"}
      ]
    }
  ]
}
```

All three hooks run in parallel. If ANY hook denies (exit code 2 or `permissionDecision: deny`), the tool is blocked.

### Independence requirement

Hooks MUST NOT depend on side effects of other hooks:

#### Broken: implicit ordering dependency

```
{
  "SessionStart": [
    {
      "hooks": [
        {"type": "command", "command": "bash ... > /tmp/config.json"},
        {"type": "command", "command": "bash ... < /tmp/config.json"}
      ]
    }
  ]
}
```

Risk: second hook may run before first hook writes file, causing parse error.

#### Correct: each hook is self-sufficient

Each hook reads from its own source or environment variables:

```
{
  "SessionStart": [
    {
      "hooks": [
        {"type": "command", "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/init-env.sh"},
        {"type": "command", "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/validate-config.sh"}
      ]
    }
  ]
}
```

Scripts read from `${CLAUDE_PROJECT_DIR}` or `${CLAUDE_PLUGIN_ROOT}`, not from each other's outputs.

## Timeout tuning

Timeouts apply per hook. If hook exceeds timeout, it is terminated:

| Hook Type | Default | Range | Guidance |
| --- | --- | --- | --- |
| Prompt | 30s | 10-120s | LLM latency: 2-10s typical; use 30s default |
| Command (fast) | 10s | 1-60s | File checks, regex matching, simple I/O |
| Command (I/O) | 60s | 10-120s | Network calls, remote API, large file ops |
| SessionStart | 60s | 10-120s | Initialization is not time-critical |

### Example: tuning per operation

```
{
  "PreToolUse": [
    {
      "matcher": "Write",
      "hooks": [
        {
          "type": "command",
          "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/fast-path-check.sh",
          "timeout": 2
        },
        {
          "type": "prompt",
          "prompt": "Validate write safety: $TOOL_INPUT",
          "timeout": 30
        }
      ]
    },
    {
      "matcher": "Bash",
      "hooks": [
        {
          "type": "command",
          "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/remote-api-call.sh",
          "timeout": 60
        }
      ]
    }
  ]
}
```

### Timeout errors

If hook times out, it is logged and subsequent behavior depends on hook type:

- `PreToolUse` timeout: tool proceeds as if hook absent (no deny)
- `Stop` timeout: stop is allowed (no block)
- `PostToolUse` timeout: hook output ignored, tool result stands
- `SessionStart` timeout: session continues but environment may be incomplete

Monitor timeouts with:

```
claude --debug 2>&1 | grep -i timeout
```

## Caching patterns

### Pattern 1: File-based cache in hook directory

Cache is written by one hook, read by others. Safe because hooks in same event run in parallel but file creation is atomic:

```
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Cache validation result to avoid repeated checks.
#
# @param input Hook input JSON.
# @return Reads cached result if present; performs check and caches if not.
cache_based_check() {
    input=$(cat)
    cache_file="${CLAUDE_PLUGIN_ROOT}/.hook-cache/validation.json"
    mkdir -p "${CLAUDE_PLUGIN_ROOT}/.hook-cache"
    file_path=$(printf '%s' "$input" | jq -r '.tool_input.file_path')
    cache_key=$(printf '%s' "$file_path" | md5sum | awk '{print $1}')
    if [ -f "$cache_file" ]; then
        cached_result=$(grep "\"$cache_key\"" "$cache_file" || echo "")
        if [ -n "$cached_result" ]; then
            printf '%s' "$cached_result" | jq '.result'
            return 0
        fi
    fi
    result=$(validate_path "$file_path")
    printf '{"%s": {"result": "%s", "timestamp": %s}}\n' "$cache_key" "$result" "$(date +%s)" >> "$cache_file"
    echo "$result"
}
```

### Pattern 2: environment variable cache within SessionStart

SessionStart hooks run sequentially in a single session. Computed values can be written to `$CLAUDE_ENV_FILE` for reuse:

```
#!/usr/bin/env bash
# -*- coding: utf-8 -*-
set -e

# Compute and cache project metadata for session.
#
# @return Exports PROJECT_TYPE and PROJECT_VERSION to env.
cache_project_metadata() {
    PROJECT_TYPE=$(grep -r '^type:' .project.config | sed 's/type: *//' | head -1)
    PROJECT_VERSION=$(grep -r '^version:' .project.config | sed 's/version: *//' | head -1)
    printf 'export PROJECT_TYPE=%q\n' "$PROJECT_TYPE" >> "$CLAUDE_ENV_FILE"
    printf 'export PROJECT_VERSION=%q\n' "$PROJECT_VERSION" >> "$CLAUDE_ENV_FILE"
}
cache_project_metadata
```

Note: This example uses bash for `printf %q` (argument escaping). If POSIX sh is required, use `sed` or `printf '%s'` escaping instead.

All subsequent hooks and tools can access via `$PROJECT_TYPE` and `$PROJECT_VERSION`.

### Cache invalidation

Add timestamps to cached data. If data is stale (> 1 hour), recompute:

```
timestamp=$(jq -r '.timestamp' "$cache_file" || echo "0")
current=$(date +%s)
age=$((current - timestamp))
if [ "$age" -gt 3600 ]; then
    result=$(perform_expensive_check)
    printf '{"result": "%s", "timestamp": %s}\n' "$result" "$current" > "$cache_file"
else
    result=$(jq -r '.result' "$cache_file")
fi
```

## Hot-path optimization

Hooks on critical paths (`PreToolUse` with `Write|Edit|Bash` matchers) should be fast (< 5s).

### Example: fast-path + fallback pattern

```
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Quick check with fallback to detailed validation.
#
# @param input Hook input JSON.
# @return Fast deny for obvious cases; prompt hook for edge cases.
quick_validation() {
    input=$(cat)
    file_path=$(printf '%s' "$input" | jq -r '.tool_input.file_path')
    if [ "$file_path" != "${file_path%..*}" ] || printf '%s' "$file_path" | grep -qE '\.(env|aws|pem)$'; then
        printf '{"permissionDecision": "deny", "systemMessage": "Obvious security issue"}\n' >&2
        exit 2
    fi
    printf '{"permissionDecision": "allow", "systemMessage": "Passed quick check"}\n' >&2
    exit 0
}
quick_validation
```

Then add a second, slower prompt hook for deep validation only when needed:

```
{
  "PreToolUse": [
    {
      "matcher": "Write",
      "hooks": [
        {
          "type": "command",
          "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/quick-check.sh",
          "timeout": 2
        },
        {
          "type": "prompt",
          "prompt": "Perform deep security analysis: $TOOL_INPUT. Validate code style, performance, correctness.",
          "timeout": 30
        }
      ]
    }
  ]
}
```

First hook blocks obvious issues quickly. Second hook runs in parallel and can take longer because first hook already filtered obvious cases.

### Avoid Expensive Operations in Hot Paths

#### Broken

```
# PreToolUse hook
input=$(cat)
file_path=$(printf '%s' "$input" | jq -r '.tool_input.file_path')
# Expensive: network call on every tool use
curl -s "https://api.example.com/check?path=$file_path"
```

#### Correct

Move expensive work to SessionStart

```
{
  "SessionStart": [
    {
      "hooks": [
        {
          "type": "command",
          "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/download-policies.sh"
        }
      ]
    }
  ],
  "PreToolUse": [
    {
      "matcher": "Write",
      "hooks": [
        {
          "type": "command",
          "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/check-policy.sh"
        }
      ]
    }
  ]
}
```

Policies are downloaded once at session start. PreToolUse hook reads local policy (fast).

## Debugging hooks

### Command: /hooks

List active hooks in current session:

```
/hooks
```

Output:

```
Loaded hooks (session abc123):
  SessionStart (1 hook)
    - command: bash ${CLAUDE_PLUGIN_ROOT}/hooks/init.sh
  PreToolUse (2 hooks)
    - Write: command timeout 5s
    - Write: prompt timeout 30s
  Stop (1 hook)
    - *: prompt timeout 30s
```

### Command: claude --debug

Run Claude Code with debug output:

```
claude --debug
```

Look for hook-related output:

```
[hooks] Loading hooks from /path/to/plugin/hooks/hooks.json
[hooks] Validating hook syntax...
[hooks] SessionStart: executing 1 hook (0.5s)
[hooks] PreToolUse (Write): executing 2 hooks (timeout 5s + 30s)
[hooks] Hook result: permissionDecision=allow
```

### Test hook with sample input

Create test input and run hook directly:

```
cat > /tmp/test-input.json << 'EOF'
{
  "session_id": "test",
  "tool_name": "Write",
  "tool_input": {"file_path": "/tmp/test.txt"},
  "hook_event_name": "PreToolUse"
}
EOF

sh hooks/validate.sh < /tmp/test-input.json
echo "Exit code: $?"
```

Verify output is valid JSON:

```
sh hooks/validate.sh < /tmp/test-input.json | python3 -m json.tool
```

### Logging from hooks

Write logs to file for debugging:

```
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Perform check and log results.
#
# @param input Hook input JSON.
# @return Outputs hook decision; logs to file.
check_with_logging() {
    input=$(cat)
    log_file="${CLAUDE_PLUGIN_ROOT}/logs/hook.log"
    mkdir -p "$(dirname "$log_file")"
    printf '[%s] Hook input: %s\n' "$(date)" "$input" >> "$log_file"
    tool_name=$(printf '%s' "$input" | jq -r '.tool_name')
    if [ "$tool_name" = "Write" ]; then
        printf '[%s] Validating write...\n' "$(date)" >> "$log_file"
        printf '{"permissionDecision": "allow"}\n' >&2
        exit 0
    fi
}
check_with_logging
```

View logs:

```
tail -f "${CLAUDE_PLUGIN_ROOT}/logs/hook.log"
```

## Parallel execution guarantee

Hooks on the same event WILL run in parallel unless explicitly chained. There is NO way to force sequential execution within hooks.json.

To enforce sequence, use SessionStart setup:

```
{
  "SessionStart": [
    {
      "hooks": [
        {
          "type": "command",
          "command": "bash ${CLAUDE_PLUGIN_ROOT}/hooks/setup-phase-1.sh && bash ${CLAUDE_PLUGIN_ROOT}/hooks/setup-phase-2.sh"
        }
      ]
    }
  ]
}
```

Chain hooks within a single command with `&&` operator to ensure sequential execution.

## References

Refer to `SKILL.md` for hook event types and input/output contracts.

Refer to `references/lifecycle.md` for session timing and restart requirements.

Refer to `references/security-patterns.md` for safe bash practices in hooks.
