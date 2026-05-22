---
name: transport-types
description: |-
  Transport-specific lifecycle, failure modes, and decision tree for stdio, SSE, HTTP, and WebSocket MCP servers.
---

# MCP Transport Types: Detailed Lifecycle, Failure Modes, and Selection

Open this reference when choosing between stdio, SSE, HTTP, or WebSocket transports for an MCP server, or troubleshooting transport-specific failures.

This reference covers the four MCP transport types (stdio, SSE, HTTP, WebSocket) with lifecycle details, failure modes, and decision tree for choosing the right transport.

## Transport overview

| Transport | Local/Remote | Auth | Latency | Use Case |
| --- | --- | --- | --- | --- |
| stdio | Local only | Env vars | 10-100ms | Custom servers, local tools |
| SSE | Remote | OAuth | 1-5s | Hosted services (GitHub, Asana) |
| HTTP | Remote | Token/headers | 50-500ms | REST APIs, custom backends |
| WebSocket | Remote | Token/headers | 20-200ms | Real-time, streaming, bidirectional |

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
```text

### Lifecycle

1. Claude Code starts session
2. stdio server is spawned as subprocess
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
```text

Mitigation: Log server output, add error recovery:

```json
{
  "command": "bash",
  "args": ["-c", "while true; do python -m mcp_server || sleep 2; done"]
}
```text

Stdin/stdout deadlock: Server blocks waiting for input.

```text
Timeout: Tool call hangs indefinitely
```text

Mitigation: Set explicit timeout in command hook that uses tool.

Environment variable mismatch: Server expects env var not set.

```text
Error: DATABASE_URL not set
```text

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
```text

### Best practices

- Keep server processes lightweight; avoid heavy startup
- Log server stderr for debugging
- Use `env` to pass all required credentials
- Test server locally: `npx @modelcontextprotocol/server-filesystem /tmp`

## SSE transport: hosted OAuth

Server-Sent Events: unidirectional stream from server to client. OAuth handles auth.

### Configuration

```json
{
  "github": {
    "type": "sse",
    "url": "https://mcp.github.com/sse"
  }
}
```text

### Lifecycle

1. Claude Code starts session
2. SSE connection opened to remote URL
3. Server sends tool list via event stream
4. Claude Code makes tool calls via separate HTTPS POST to `/call` endpoint (inferred)
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
```text

Mitigation: User can retry with `/mcp authorize github`.

Token expired: Stored OAuth token is stale.

```text
Error: Token expired, attempting refresh
Automatic retry with new token
```text

Claude Code handles refresh automatically. No user action needed.

Network timeout: Remote server unreachable.

```text
Error: Connection timeout to https://mcp.github.com/sse
```text

Mitigation: Check network, verify server is online. Retry with `/mcp test github`.

Unimplemented tool: Server lists tool but `/call` endpoint returns error.

```text
Error: Tool 'create_issue' not supported
```text

Fix: Verify server actually implements listed tools. Report to server maintainer.

### Best practices

- Use only for official hosted services
- Verify OAuth scope is minimal (don't over-request permissions)
- Monitor token refresh logs: `claude --debug 2>&1 | grep OAuth`
- Test connectivity: `/mcp test github`

## HTTP transport: token-based REST

HTTP: stateless requests with bearer token in headers.

### Configuration

```json
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
```text

### Lifecycle

1. Claude Code starts session
2. GET request to `https://api.example.com/mcp` with bearer token
3. Server responds with tool list (JSON)
4. Tool call: POST to `https://api.example.com/mcp/call` with tool name + args
5. Server responds with result
6. Session ends → no cleanup (stateless)

### Failure modes

Invalid token: Token in env var is wrong or expired.

```text
{
  "error": "Unauthorized",
  "code": 401
}
```text

Fix: Verify `${API_TOKEN}` env var contains valid token. Refresh if expired.

Server error: Remote server returns 500.

```text
Error: Server error (500)
```text

Mitigation: Check server logs, retry after server recovery.

Malformed response: Server returns invalid JSON.

```text
Error: Failed to parse MCP response
```text

Fix: Verify server sends valid JSON. Test with:

```sh
curl -H "Authorization: Bearer $API_TOKEN" https://api.example.com/mcp | jq .
```text

Rate limiting: Server enforces rate limits.

```text
Error: Rate limit exceeded
Retry-After: 60
```text

Mitigation: Add exponential backoff to hooks that call tools:

```bash
#!/bin/bash
for attempt in 1 2 3; do
    call_mcp_tool && break
    sleep $((2 ** attempt))
done
```text

Note: This block uses `bash` due to exponential operators, which are not available in POSIX sh.

Header injection: Custom headers passed incorrectly.

```text
Error: Invalid Authorization header
```text

Fix: Ensure headers object is valid JSON:

```json
{
  "type": "http",
  "headers": {
    "Authorization": "Bearer ${API_TOKEN}",
    "X-Custom": "value"
  }
}
```text

### Best practices

- Use HTTPS only (never HTTP)
- Store tokens in env vars, never hardcode
- Set reasonable timeouts on tool calls
- Implement exponential backoff for transient failures
- Monitor rate limits and adjust batch sizes

## WebSocket transport: real-time bidirectional

WebSocket: persistent connection with bidirectional message flow.

### Configuration

```json
{
  "realtime-service": {
    "type": "ws",
    "url": "wss://mcp.example.com/ws",
    "headers": {
      "Authorization": "Bearer ${TOKEN}"
    }
  }
}
```text

### Lifecycle

1. Claude Code starts session
2. WebSocket connection established to `wss://mcp.example.com/ws` with auth header
3. Server sends initial message with tool list
4. Bidirectional message exchange for tool calls
5. Server can send async notifications (push messages)
6. Session ends → connection closed cleanly

### Persistent connection benefits

- Streaming responses: server sends partial results as they compute
- Async notifications: server can push updates without tool call
- Lower latency: no connection overhead per request
- Stateful: server can maintain session context

### Failure modes

Connection refused: Server not listening or port wrong.

```text
Error: WebSocket connection refused
```text

Fix: Verify server is running, port is correct.

TLS certificate error: Server cert invalid or self-signed.

```text
Error: Certificate verification failed
```text

Mitigation: For self-signed certs in dev, configure trust (not recommended for prod):

```json
{
  "type": "ws",
  "url": "wss://localhost:8443/ws",
  "verify_ssl": false
}
```text

Protocol mismatch: Server doesn't speak MCP protocol.

```text
Error: Invalid MCP message received
```text

Fix: Verify server implements MCP protocol correctly.

Message timeout: Server doesn't respond within timeout.

```text
Error: Tool call timeout after 30s
```text

Mitigation: Increase timeout for slow operations:

```markdown
---
allowed-tools:
  - mcp__plugin_realtime__*
tool_timeout: 120
---
```text

Connection drop: Network interruption mid-call.

```text
Error: Connection lost, reconnecting...
```text

Mitigation: Implement exponential backoff reconnection:

```bash
max_retries=3
for attempt in $(seq 1 $max_retries); do
    call_tool && break
    sleep $((2 ** attempt))
done
```text

Note: This block uses `bash` due to exponential operators, which are not available in POSIX sh.

### Streaming example

Tool that returns results in chunks:

```javascript
// Server-side pseudocode
async function* streamData(input) {
    for (const chunk of largeDataset) {
        yield {type: "chunk", data: chunk};
    }
    yield {type: "complete"};
}
```text

Claude Code collects all chunks and assembles result.

### Best practices

- Use for long-running operations or streaming
- Implement graceful reconnection with exponential backoff
- Set per-tool timeouts if needed
- Monitor connection health: `claude --debug 2>&1 | grep WebSocket`
- Verify server closes connection cleanly on session end

## Transport selection decision tree

```text
Is the service hosted remotely?
├─ NO → Use stdio (local subprocess)
└─ YES
    ├─ Is it an official service (GitHub, Asana, etc.)?
    │  └─ YES → Use SSE (OAuth handled automatically)
    └─ Is it real-time or streaming?
       ├─ YES → Use WebSocket
       └─ NO → Use HTTP (stateless, simple)
```text

### Decision matrix

| Requirement | stdio | SSE | HTTP | WebSocket |
| --- | --- | --- | --- | --- |
| Local only | Yes | No | No | No |
| Official OAuth | No | Yes | No | No |
| Custom token | No | No | Yes | Yes |
| Real-time streaming | No | No | No | Yes |
| Simple stateless | No | Yes | Yes | No |
| Multiple servers | Yes | Yes | Yes | Yes |

## References

Refer to `SKILL.md` for configuration syntax and env var expansion.

Refer to `references/authentication.md` for detailed OAuth and token handling.

Refer to `references/performance.md` for connection pooling and timeout tuning.
