---
description: >-
  Supported Claude Code MCP transports, configuration fields, selection rules, and lifecycle differences.
---

# MCP Transport Types

Open this reference when the server can use more than one transport or when a remote integration has connection or authentication constraints.

## Selection

1. Use stdio for a bundled or local process.
2. Use HTTP for a new remote request-response server.
3. Keep SSE only when the server operator still exposes an SSE endpoint.
4. Use WebSocket only when the server needs persistent bidirectional delivery or server-initiated pushes.

## Stdio

```json
{
  "mcpServers": {
    "local-tools": {
      "command": "bun",
      "args": ["${CLAUDE_PLUGIN_ROOT}/servers/local.ts"],
      "env": {
        "STATE_DIR": "${CLAUDE_PLUGIN_DATA}/local-tools"
      }
    }
  }
}
```

The process uses stdin/stdout for MCP protocol traffic.
It inherits the user environment plus declared `env` values.
Keep diagnostic output off stdout because stdout belongs to the protocol.

## HTTP

```json
{
  "mcpServers": {
    "remote-tools": {
      "type": "http",
      "url": "https://mcp.example.com/mcp"
    }
  }
}
```

HTTP is the preferred remote transport and supports OAuth.
Claude Code reconnects HTTP and SSE servers with bounded exponential backoff for eligible transient failures.

## SSE

```json
{
  "mcpServers": {
    "legacy-tools": {
      "type": "sse",
      "url": "https://mcp.example.com/sse"
    }
  }
}
```

SSE remains supported but is deprecated.
Do not migrate a working server by changing only the `type`; use the endpoint the server actually exposes.

## WebSocket

```json
{
  "mcpServers": {
    "event-tools": {
      "type": "ws",
      "url": "wss://mcp.example.com/socket",
      "headers": {
        "Authorization": "Bearer ${EXAMPLE_API_TOKEN}"
      }
    }
  }
}
```

WebSocket accepts `url`, `headers`, `headersHelper`, `timeout`, and `alwaysLoad` like HTTP.
It uses header authentication only and cannot use the OAuth flow.
Configure it through `.mcp.json` or `claude mcp add-json`, not `claude mcp add --transport`.

## Transport Checks

- Confirm the endpoint and transport with the server operator.
- Require HTTPS or WSS outside localhost.
- Confirm authentication is supported by the chosen transport.
- Confirm `/reload-plugins` reconnects a changed plugin server.
- Confirm `/mcp` reports the expected connected, pending, failed, or authentication state.
