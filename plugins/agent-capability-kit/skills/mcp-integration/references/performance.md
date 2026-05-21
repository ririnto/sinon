---
name: performance
description: |-
  Lazy loading, connection pooling, request batching, latency observability, and debugging patterns for MCP servers.
---

# MCP Performance: Lazy Loading, Connection Pooling, and Debugging

This reference covers performance optimization patterns for MCP servers: lazy loading, connection pooling, request batching, latency observability, and debugging with Claude Code built-in tools.

## Lazy loading: defer server startup

MCP servers are resource-heavy. Load them only when needed.

### Problem: all servers start at session startup

```json
{
  "github": {"type": "sse", "url": "..."},
  "slack": {"type": "sse", "url": "..."},
  "asana": {"type": "sse", "url": "..."},
  "internal-api": {"type": "http", "url": "..."}
}
```

Session startup latency: 5-10 seconds (one OAuth handshake per server).

### Solution: conditional lazy loading

Load servers only when tool is called:

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Lazy-load MCP server on first tool invocation.
#
# @param server_name Name of server to activate.
# @return Activates server; subsequent calls use cached connection.
lazy_load_mcp_server() {
    server_name="$1"
    loaded_file="${CLAUDE_PLUGIN_ROOT}/.mcp-loaded-${server_name}"
    if [ -f "$loaded_file" ]; then
        return 0
    fi
    echo "Initializing $server_name server..." >&2
    /mcp activate "$server_name" 2>&1 | grep -i "success\|ready"
    touch "$loaded_file"
}
```

Call before using server tools:

```markdown
# Command that uses GitHub MCP

Before using GitHub tools, ensure server is loaded:

\`\`\`bash
lazy_load_mcp_server github
\`\`\`
```

Trade-off: first tool call is slower (2-3s for OAuth), subsequent calls are fast.

## Connection pooling: reuse remote connections

HTTP and WebSocket connections to MCP servers can be pooled (connection reuse across tool calls).

### Problem: new connection per tool call

Each tool call opens a new connection, then closes:

```text
Tool call 1: Open → Request → Response → Close (100ms)
Tool call 2: Open → Request → Response → Close (100ms)
Total: 200ms
```

### Solution: persistent connection pool

MCP runtime maintains connection pool automatically for HTTP/WebSocket. No config needed.

Verify pooling is active:

```sh
claude --debug 2>&1 | grep -i "pool\|connection"
```

Expected output:

```text
[mcp] Creating connection pool for 'github' (size=5)
[mcp] Reusing connection from pool for tool: 'search_repositories'
[mcp] Connection pool stats: 5 active, 0 pending
```

### Connection pool tuning (advanced)

For high-volume tool use, tune pool size in hook:

`.claude/<plugin-name>.local.md:`

```markdown
---
mcp_pool_size: 10
mcp_max_requests_per_connection: 100
---
```

Hook reads and applies:

```sh
POOL_SIZE=$(grep '^mcp_pool_size:' "$SETTINGS_FILE" | sed 's/mcp_pool_size: *//')
export MCP_POOL_SIZE="$POOL_SIZE"
```

Typical settings:

- `mcp_pool_size: 5` (default)
- `mcp_pool_size: 10` (high-volume use, many concurrent calls)
- `mcp_max_requests_per_connection: 100` (close connection after 100 requests for freshness)

## Request batching: combine multiple tool calls

When using multiple MCP tools in sequence, batch them if server supports it.

### Problem: sequential calls

```sh
# shellcheck disable=SC2034
# Three separate tool calls, three separate requests
REPO1=$(call_tool list_repositories --user alice)
REPO2=$(call_tool list_repositories --user bob)
REPO3=$(call_tool list_repositories --user charlie)
```

Latency: 100ms × 3 = 300ms

### Solution: batch request

Some servers support batch API (e.g., GitHub GraphQL can query multiple users in one call):

```bash
#!/bin/bash

# Batch repository queries for multiple users.
#
# @param users Space-separated list of usernames.
# @return Outputs all repositories in one tool call.
batch_list_repositories() {
    users=("$@")
    query="
    {
        $(for user in "${users[@]}"; do
            echo "user_${user//[^a-zA-Z0-9]/}: user(login: \"${user}\") { repositories(first: 10) { nodes { name } } }"
        done)
    }
    "
    call_tool graphql_query --query "$query"
}
```

Latency: single request for all users, 100ms total.

### Example: Slack batch posting

Post to multiple channels in one batch call:

```sh
#!/bin/bash

# Post message to multiple Slack channels.
#
# @param message Message text.
# @param channels Space-separated list of channel IDs.
# @return Posts to all channels in batched requests.
batch_slack_post() {
    message="$1"
    shift
    channels=("$@")
    batch_size=10
    i=0
    while [ "$i" -lt "${#channels[@]}" ]; do
        batch=("${channels[@]:$i:$batch_size}")
        for channel in "${batch[@]}"; do
            call_tool send_message --channel "$channel" --text "$message" &
        done
        wait
        i=$((i + batch_size))
    done
}
```

## MCP latency observability

### Latency breakdown

MCP tool call latency has several components:

```text
Total = Network (10-100ms) + Server Processing (50-500ms) + Serialization (1-10ms)
```

Measure each:

```bash
#!/bin/bash

# Measure MCP tool call latency with breakdown.
#
# @param tool_name Name of MCP tool.
# @return Outputs latency breakdown.
measure_mcp_latency() {
    tool_name="$1"
    start_ns=$(date +%s%N)
    result=$(call_tool "$tool_name" --arg1 value1)
    end_ns=$(date +%s%N)
    total_ms=$(( (end_ns - start_ns) / 1000000 ))
    echo "Tool '$tool_name' latency: ${total_ms}ms"
    echo "Result size: ${#result} bytes"
}
```

Debug output shows per-tool stats:

```sh
claude --debug 2>&1 | grep "mcp\|latency"
```

Output:

```text
[mcp] Tool 'list_repositories': 45ms
[mcp] Tool 'get_repository_details': 120ms
[mcp] Average latency: 82.5ms
```

### Slow tool detection

If tool consistently takes > 500ms:

```sh
#!/bin/bash

# Identify slow MCP tools in use.
#
# @param threshold_ms Latency threshold in milliseconds.
# @return Logs tools exceeding threshold.
detect_slow_tools() {
    threshold_ms="${1:-500}"
    claude --debug 2>&1 | grep "Tool.*ms" | awk -v t="$threshold_ms" '{
        if ($NF > t) print "SLOW: " $0
    }'
}
```

Slow tools are candidates for:

- Batching (combine multiple calls)
- Caching (avoid repeated calls)
- Async processing (don't wait for result)

## Caching MCP tool results

Cache expensive tool results locally to avoid repeated calls.

### File-based cache

```bash
#!/bin/bash

# Cache MCP tool result with TTL.
#
# @param tool_name Name of tool to call.
# @param args Arguments to tool.
# @param ttl_seconds Cache time-to-live.
# @return Returns cached result if fresh; calls tool and caches otherwise.
cached_mcp_call() {
    tool_name="$1"
    args="$2"
    ttl_seconds="${3:-3600}"
    cache_dir="${CLAUDE_PLUGIN_ROOT}/.mcp-cache"
    mkdir -p "$cache_dir"
    cache_key=$(echo -n "${tool_name}:${args}" | md5sum | awk '{print $1}')
    cache_file="${cache_dir}/${cache_key}"
    if [ -f "$cache_file" ]; then
        cache_age=$(( $(date +%s) - $(stat -f%m "$cache_file" || date +%s) ))
        if [ "$cache_age" -lt "$ttl_seconds" ]; then
            cat "$cache_file"
            return 0
        fi
    fi
    result=$(call_tool "$tool_name" "$args")
    echo "$result" > "$cache_file"
    echo "$result"
}
```

Usage:

```sh
# Cache GitHub user data for 1 hour
GITHUB_USER=$(cached_mcp_call get_user --username alice 3600)
```

### Cache invalidation

Invalidate cache when data changes:

```sh
#!/bin/bash

# Clear MCP cache for a specific tool.
#
# @param tool_name Tool to invalidate.
# @return Removes cached entries.
invalidate_mcp_cache() {
    tool_name="$1"
    cache_dir="${CLAUDE_PLUGIN_ROOT}/.mcp-cache"
    rm -f "${cache_dir}/${tool_name}:*"
    echo "Cache cleared for $tool_name"
}
```

## Debugging MCP: commands and output

### Command: /mcp

List active MCP servers and tools:

```text
/mcp
```

Output:

```text
MCP Servers (3 active):
  github
    ├─ search_repositories
    ├─ create_issue
    └─ list_repositories
  slack
    ├─ send_message
    └─ create_channel
  internal-api
    ├─ query_database
    └─ submit_job
```

### Command: /mcp test `<server-name>`

Test server connectivity:

```text
/mcp test github
```

Output:

```text
Testing github server...
✓ Connection successful
✓ Tools loaded (42)
✓ Sample query executed in 45ms
✓ Server healthy
```

### Command: /mcp authorize `<server-name>`

Trigger OAuth re-authorization (for expired or wrong token):

```text
/mcp authorize github
```

Browser opens to GitHub OAuth, user approves, token updated.

### Claude --debug with MCP filter

Run with debug output filtered to MCP:

```sh
claude --debug 2>&1 | grep -E "^\[mcp\]"
```

Output:

```text
[mcp] Loading servers from .mcp.json
[mcp] Server 'github': initializing SSE connection
[mcp] Server 'github': OAuth token valid (expires in 3600s)
[mcp] Server 'github': tools loaded (42)
[mcp] Tool 'search_repositories': 45ms
[mcp] Server 'slack': initializing SSE connection
```

### Log file location

Claude Code writes detailed MCP logs to:

```sh
~/.claude/logs/mcp.log
```

View recent logs:

```sh
tail -50 ~/.claude/logs/mcp.log
```

## Performance checklist

- [ ] Enable lazy loading for unused servers (save startup time)
- [ ] Monitor connection pool stats (reuse vs new connections)
- [ ] Batch requests when possible (combine multiple calls)
- [ ] Cache expensive tool results with appropriate TTL
- [ ] Invalidate cache on data changes
- [ ] Monitor per-tool latency with `/mcp`
- [ ] Identify slow tools (> 500ms) and optimize
- [ ] Test server connectivity regularly: `/mcp test <server>`
- [ ] Review MCP debug logs for errors: `tail -f ~/.claude/logs/mcp.log`
- [ ] Tune connection pool size for high-volume use

## References

Refer to `SKILL.md` for MCP configuration and env var expansion.

Refer to `references/transport-types.md` for transport-specific performance characteristics.

Refer to `references/authentication.md` for token caching and OAuth refresh latency.
