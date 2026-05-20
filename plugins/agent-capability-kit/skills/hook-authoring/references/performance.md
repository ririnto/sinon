---
name: performance
description: |-
  Parallel execution model, timeout tuning, caching patterns, hot-path optimization, and debugging strategies for hooks.
---

# Hook Performance: Execution Model, Caching, and Debugging

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

```json
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

```json
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

```json
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

```bash
claude --debug 2>&1 | grep -i timeout
```

## Caching patterns

### Pattern 1: File-based cache in hook directory

Cache is written by one hook, read by others. Safe because hooks in same event run in parallel but file creation is atomic:

```bash
#!/bin/bash
set -euo pipefail

# Cache validation result to avoid repeated checks.
# @param input Hook input JSON.
# @return Reads cached result if present; performs check and caches if not.
cache_based_check() {
    local input
    input=$(cat)
    local cache_file="${CLAUDE_PLUGIN_ROOT}/.hook-cache/validation.json"
    mkdir -p "${CLAUDE_PLUGIN_ROOT}/.hook-cache"
    local file_path
    file_path=$(echo "$input" | jq -r '.tool_input.file_path')
    local cache_key
    cache_key=$(echo -n "$file_path" | md5sum | awk '{print $1}')
    if [[ -f "$cache_file" ]]; then
        local cached_result
        cached_result=$(grep "\"$cache_key\"" "$cache_file" || echo "")
        if [[ -n "$cached_result" ]]; then
            echo "$cached_result" | jq '.result'
            return 0
        fi
    fi
    local result
    result=$(validate_path "$file_path")
    echo "{\"$cache_key\": {\"result\": \"$result\", \"timestamp\": $(date +%s)}}" >> "$cache_file"
    echo "$result"
}
```

### Pattern 2: environment variable cache within SessionStart

SessionStart hooks run sequentially in a single session. Computed values can be written to `$CLAUDE_ENV_FILE` for reuse:

```bash
#!/bin/bash
set -euo pipefail

# Compute and cache project metadata for session.
# @return Exports PROJECT_TYPE and PROJECT_VERSION to env.
cache_project_metadata() {
    PROJECT_TYPE=$(grep -r '^type:' .project.config | sed 's/type: *//' | head -1)
    PROJECT_VERSION=$(grep -r '^version:' .project.config | sed 's/version: *//' | head -1)
    printf 'export PROJECT_TYPE=%q\n' "$PROJECT_TYPE" >> "$CLAUDE_ENV_FILE"
    printf 'export PROJECT_VERSION=%q\n' "$PROJECT_VERSION" >> "$CLAUDE_ENV_FILE"
}
cache_project_metadata
```

All subsequent hooks and tools can access via `$PROJECT_TYPE` and `$PROJECT_VERSION`.

### Cache invalidation

Add timestamps to cached data. If data is stale (> 1 hour), recompute:

```bash
timestamp=$(jq -r '.timestamp' "$cache_file" 2>/dev/null || echo "0")
current=$(date +%s)
age=$((current - timestamp))
if [[ $age -gt 3600 ]]; then
    # Cache is stale, recompute
    result=$(perform_expensive_check)
    echo "{\"result\": \"$result\", \"timestamp\": $current}" > "$cache_file"
else
    result=$(jq -r '.result' "$cache_file")
fi
```

## Hot-path optimization

Hooks on critical paths (`PreToolUse` with `Write|Edit|Bash` matchers) should be fast (< 5s).

### Example: fast-path + fallback pattern

```bash
#!/bin/bash
set -euo pipefail

# Quick check with fallback to detailed validation.
# @param input Hook input JSON.
# @return Fast deny for obvious cases; prompt hook for edge cases.
quick_validation() {
    local input
    input=$(cat)
    local file_path
    file_path=$(echo "$input" | jq -r '.tool_input.file_path')
    if [[ "$file_path" == *".."* ]] || [[ "$file_path" =~ \.(env|aws|pem)$ ]]; then
        echo '{"permissionDecision": "deny", "systemMessage": "Obvious security issue"}' >&2
        exit 2
    fi
    echo '{"permissionDecision": "allow", "systemMessage": "Passed quick check"}' >&2
    exit 0
}
quick_validation
```

Then add a second, slower prompt hook for deep validation only when needed:

```json
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

```bash
# PreToolUse hook
input=$(cat)
file_path=$(echo "$input" | jq -r '.tool_input.file_path')
# Expensive: network call on every tool use
api_response=$(curl -s "https://api.example.com/check?path=$file_path")
```

#### Correct

Move expensive work to SessionStart

```json
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

```text
/hooks
```

Output:

```text
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

```bash
claude --debug
```

Look for hook-related output:

```text
[hooks] Loading hooks from /path/to/plugin/hooks/hooks.json
[hooks] Validating hook syntax...
[hooks] SessionStart: executing 1 hook (0.5s)
[hooks] PreToolUse (Write): executing 2 hooks (timeout 5s + 30s)
[hooks] Hook result: permissionDecision=allow
```

### Test hook with sample input

Create test input and run hook directly:

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
echo "Exit code: $?"
```

Verify output is valid JSON:

```bash
bash hooks/validate.sh < /tmp/test-input.json | python3 -m json.tool
```

### Logging from hooks

Write logs to file for debugging:

```bash
#!/bin/bash
set -euo pipefail

# Perform check and log results.
# @param input Hook input JSON.
# @return Outputs hook decision; logs to file.
check_with_logging() {
    local input
    input=$(cat)
    local log_file="${CLAUDE_PLUGIN_ROOT}/logs/hook.log"
    mkdir -p "$(dirname "$log_file")"
    echo "[$(date)] Hook input: $input" >> "$log_file"
    local tool_name
    tool_name=$(echo "$input" | jq -r '.tool_name')
    if [[ "$tool_name" == "Write" ]]; then
        echo "[$(date)] Validating write..." >> "$log_file"
        echo '{"permissionDecision": "allow"}' >&2
        exit 0
    fi
}
check_with_logging
```

View logs:

```bash
tail -f ${CLAUDE_PLUGIN_ROOT}/logs/hook.log
```

## Parallel execution guarantee

Hooks on the same event WILL run in parallel unless explicitly chained. There is NO way to force sequential execution within hooks.json.

To enforce sequence, use SessionStart setup:

```json
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
