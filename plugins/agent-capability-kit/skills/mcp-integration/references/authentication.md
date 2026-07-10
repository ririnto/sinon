---
description: >-
  OAuth, static headers, dynamic headers, environment variables, and secret boundaries for plugin MCP servers.
---

# MCP Authentication

Open this reference when a remote MCP server requires credentials or when a plugin server needs persistent credential-derived state.

## OAuth

Use OAuth only when the remote HTTP or SSE server advertises a compatible flow.
Configure the authoritative server URL, reload the plugin, then authenticate from `/mcp`.
Do not invent token files or callback paths in plugin documentation.

WebSocket does not support this flow.

## Static Environment Header

Use an environment placeholder when a user or deployment system already supplies a token:

```json
{
  "mcpServers": {
    "remote-api": {
      "type": "http",
      "url": "https://mcp.example.com/mcp",
      "headers": {
        "Authorization": "Bearer ${EXAMPLE_API_TOKEN}"
      }
    }
  }
}
```

Document the variable name and required scope without printing its value.

## Dynamic Headers

Use `headersHelper` when headers must be generated or refreshed at connection time.
The helper is an executable boundary: keep its path bundled, validate its output, set a timeout, and never log the returned credentials.
Use `${CLAUDE_PLUGIN_DATA}` if the helper needs writable persistent state.

## Stdio Environment

Pass only required variables to a stdio server:

```json
{
  "mcpServers": {
    "local-api": {
      "command": "bun",
      "args": ["${CLAUDE_PLUGIN_ROOT}/servers/local.ts"],
      "env": {
        "API_TOKEN": "${EXAMPLE_API_TOKEN}",
        "STATE_DIR": "${CLAUDE_PLUGIN_DATA}/local-api"
      }
    }
  }
}
```

## Secret Checklist

- no literal secrets in committed JSON
- no token in URL query strings
- no resolved headers in logs or errors
- no credential state under `${CLAUDE_PLUGIN_ROOT}`
- least-privilege token scope documented
- missing credential produces a clear non-secret error
- WebSocket integrations use header authentication, not OAuth claims
