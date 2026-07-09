---
name: transport-types
description: |-
  Transport-specific lifecycle, failure modes, and decision tree for stdio, SSE, and HTTP MCP servers.
---

# MCP Transport Types: Detailed Lifecycle, Failure Modes, and Selection

Open this reference when choosing between stdio, SSE, or HTTP transports for an MCP server, or troubleshooting transport-specific failures.

This reference covers the three MCP transport types Claude Code supports (stdio, SSE, HTTP) with lifecycle details, failure modes, and decision tree for choosing the right transport.
WebSocket transports are not supported in Claude Code MCP.

## Transport overview

| Transport | Local/Remote | Auth | Latency | Use Case |
| --- | --- | --- | --- | --- |
| stdio | Local only | Env vars | 10-100ms | Custom servers, local tools |
| SSE | Remote | OAuth | 1-5s | Hosted services (GitHub, Asana) |
| HTTP | Remote | Token/headers | 50-500ms | REST APIs, custom backends |

## Stdio transport: local process

Stdio spawns a local process and communicates via stdin/stdout.

### Configuration

```json
{
  "filesystem": {
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path/to/share"],
    "env": {
      "LOG_LEVEL": "debug"
    }
  }
}
```

### Lifecycle

1. Claude Code starts session
2. Stdio server is spawned as subprocess
3. Server writes tools and resources on stdout (MCP protocol)
4. Claude Code reads tool list
5. Tool calls are sent to server via stdin as JSON
6. Server responds via stdout
7. Session ends → subprocess is killed

### Failure modes

Process crash: Server exits unexpectedly.

```text
Error: MCP process exited with code 1
Tool calls fail: "Server unavailable"
```

Mitigation: Log server output, add error recovery:

```json
{
  "command": "bash",
  "args": ["-c", "while true; do python -m mcp_server || sleep 2; done"]
}
```

Stdin/stdout deadlock: Server blocks waiting for input.

```text
Timeout: Tool call hangs indefinitely
```

Mitigation: Set explicit timeout in command hook that uses tool.

Environment variable mismatch: Server expects env var not set.

```text
Error: DATABASE_URL not set
```

Fix: Provide all required env vars in MCP config:

```json
{
  "command": "python",
  "args": ["-m", "database_mcp"],
  "env": {
    "DATABASE_URL": "${DATABASE_URL}",
    "API_KEY": "${API_KEY}"
  }
}
```

### Best practices

- Keep server processes lightweight.
  - Avoid heavy startup.
- Log server stderr for debugging
- Use `env` to pass all required credentials
- Test server locally: `npx @modelcontextprotocol/server-filesystem /tmp`

## SSE transport: hosted OAuth

Server-Sent Events: unidirectional stream from server to client.
OAuth handles auth.

### Configuration

```jsonc
{
  "github": {
    "type": "sse",
    "url": "https://mcp.github.com/sse"
  }
}
```

### Lifecycle

1. Claude Code starts session
2. SSE connection opened to remote URL
3. Server sends tool list via event stream
4. Claude Code sends JSON-RPC tool-call requests to the server HTTP endpoint
5. Server streams response events
6. Session ends → SSE connection closes

### OAuth flow (automatic)

First use of SSE server triggers OAuth:

1. Claude Code opens browser to `https://mcp.github.com/authorize`
2. User logs in, grants permission
3. OAuth code exchanged for token (stored locally)
4. Token refreshed automatically on subsequent uses
5. Token never sent to Claude Code servers (stays local)

### Failure modes

OAuth denied: User denies permission in browser.

```text
Error: User denied access to GitHub
Tool calls fail for this session
```

Mitigation: Retry the connection with `/mcp reconnect github`.

Token expired: Stored OAuth token is stale.

```text
Error: Token expired, attempting refresh
Automatic retry with new token
```

Claude Code handles refresh automatically.
No user action needed.

Network timeout: Remote server unreachable.

```text
Error: Connection timeout to https://mcp.github.com/sse
```

Mitigation: Check network, verify server is online.
Retry with `/mcp reconnect github`.

Unimplemented tool: Server lists tool but the call returns an error.

```text
Error: Tool 'create_issue' not supported
```

Fix: Verify server actually implements listed tools.
Report to server maintainer.

### Best practices

- Use only for official hosted services
- Verify OAuth scope is minimal (don't over-request permissions)
- Monitor token refresh logs: `claude --debug 2>&1 | grep OAuth`
- Verify connectivity: run `/mcp` and confirm the server and its tools appear

## HTTP transport: token-based REST

HTTP: stateless requests with bearer token in headers.

### Configuration

```jsonc
{
  "api-service": {
    "type": "http",
    "url": "https://api.example.com/mcp",
    "headers": {
      "Authorization": "Bearer ${API_TOKEN}",
      "User-Agent": "Claude-Code"
    }
  }
}
```

### Lifecycle

1. Claude Code starts session
2. GET request to `https://api.example.com/mcp` with bearer token
3. Server responds with tool list (JSON)
4. Tool call: JSON-RPC request to the server endpoint with tool name and args
5. Server responds with result
6. Session ends → no cleanup (stateless)

### Failure modes

Invalid token: Token in env var is wrong or expired.

```json
{
  "error": "Unauthorized",
  "code": 401
}
```

Fix: Verify `${API_TOKEN}` env var contains valid token.
Refresh if expired.

Server error: Remote server returns 500.

```text
Error: Server error (500)
```

Mitigation: Check server logs, retry after server recovery.

Malformed response: Server returns invalid JSON.

```text
Error: Failed to parse MCP response
```

Fix: Verify server sends valid JSON.
Test with:

```text
curl -H "Authorization: Bearer $API_TOKEN" https://api.example.com/mcp | jq .
```

Rate limiting: Server enforces rate limits.

```text
Error: Rate limit exceeded
Retry-After: 60
```

Mitigation: Add exponential backoff to hooks that call tools:

```bash
#!/usr/bin/env bash
# -*- coding: utf-8 -*-
set -eo pipefail

for attempt in 1 2 3; do
    call_mcp_tool && break
    sleep $((2 ** attempt))
done
```

Note: This block uses `bash` due to exponential operators, which are not available in POSIX sh.

Header injection: Custom headers passed incorrectly.

```text
Error: Invalid Authorization header
```

Fix: Ensure headers object is valid JSON:

```json
{
  "type": "http",
  "headers": {
    "Authorization": "Bearer ${API_TOKEN}",
    "X-Custom": "value"
  }
}
```

### Best practices

- Use HTTPS only (never HTTP)
- Store tokens in env vars, never hardcode
- Set reasonable timeouts on tool calls
- Implement exponential backoff for transient failures
- Monitor rate limits and adjust batch sizes

## Transport selection decision tree

```text
Is the service hosted remotely?
+-- NO → Use stdio (local subprocess)
+-- YES
    +-- Is it an official service (GitHub, Asana, etc.)?
    |  +-- YES → Use SSE (OAuth handled automatically)
    +-- NO → Use HTTP (stateless, token-authenticated)
```

### Decision matrix

| Requirement | stdio | SSE | HTTP |
| --- | --- | --- | --- |
| Local only | Yes | No | No |
| Official OAuth | No | Yes | No |
| Custom token | No | No | Yes |
| Simple stateless | No | Yes | Yes |
| Multiple servers | Yes | Yes | Yes |

## References

Refer to `SKILL.md` for configuration syntax and env var expansion.

Refer to `references/authentication.md` for detailed OAuth and token handling.

Refer to `references/performance.md` for connection pooling and timeout tuning.
