---
name: mcp-integration
description: >-
  Integrate Model Context Protocol servers into Claude Code plugins through `.mcp.json` or manifest `mcpServers` configuration.
  Use when choosing stdio, HTTP, SSE, or WebSocket transport, configuring authentication, or scoping plugin MCP tools.
---

# MCP Integration

Package MCP server definitions with a Claude Code plugin using current transport, authentication, path, and tool-naming contracts.

## Owned Surface

- plugin-root `.mcp.json`
- inline or custom-path `mcpServers` in `.claude-plugin/plugin.json`
- stdio, HTTP, SSE, and WebSocket server configuration
- remote authentication and dynamic headers
- plugin MCP tool names and permission patterns
- startup, tool-call, and output limits

## First Safe Checks

Inspect and validate a dedicated configuration:

```sh
python3 -m json.tool .mcp.json
claude plugin validate .
```

After changing plugin MCP configuration, run `/reload-plugins` or restart Claude Code.
Use `/mcp` to inspect connection and authentication status, then invoke one intended tool through the plugin-scoped name.

## Configuration Shape

Plugin-root `.mcp.json` is auto-discovered and MUST wrap servers in `mcpServers`:

```json
{
  "mcpServers": {
    "local-index": {
      "command": "bun",
      "args": ["${CLAUDE_PLUGIN_ROOT}/servers/index.ts"],
      "env": {
        "STATE_DIR": "${CLAUDE_PLUGIN_DATA}/index"
      }
    },
    "remote-api": {
      "type": "http",
      "url": "https://mcp.example.com/mcp"
    }
  }
}
```

Keep the default file out of `plugin.json` when it is the only MCP source.
Use manifest `mcpServers` for inline definitions or custom paths:

```json
{
  "$schema": "https://json.schemastore.org/claude-code-plugin-manifest.json",
  "name": "example-plugin",
  "author": {
    "name": "example"
  },
  "mcpServers": {
    "remote-api": {
      "type": "http",
      "url": "https://mcp.example.com/mcp"
    }
  }
}
```

Every manifest path MUST begin with `./` and resolve inside the plugin root.

## Choose the Transport

| Transport | Configuration | Use |
| --- | --- | --- |
| stdio | `command`, optional `args` and `env` | Bundled or locally installed process |
| HTTP | `type: "http"`, `url`, optional headers | Preferred remote request-response server; supports OAuth |
| SSE | `type: "sse"`, `url`, optional headers | Legacy remote server that has not migrated to HTTP |
| WebSocket | `type: "ws"`, `url`, optional headers | Remote server that pushes events over a persistent connection |

Stdio servers MUST exchange one JSON-RPC message per line on stdin and stdout.
They MUST NOT use LSP `Content-Length` framing.

Prefer HTTP for new remote integrations.
SSE is deprecated but still supported for compatibility.
Configure WebSocket in `.mcp.json` or through `claude mcp add-json`; `claude mcp add --transport` does not accept `ws`.
WebSocket authentication is header-only and does not support the OAuth flow.

Use HTTPS for HTTP and SSE and WSS for WebSocket, except explicit localhost development.
Open `references/transport-types.md` for transport-specific failure and selection details.

## Authentication

Choose one supported boundary:

- OAuth-capable HTTP or SSE server: configure the URL, then authenticate through `/mcp`.
- Static token: reference an environment variable in `headers`.
- Rotating or computed token: use `headersHelper` to produce headers at connection time.
- stdio server: pass only the required environment variables in `env`.
- WebSocket server: use `headers` or `headersHelper`; OAuth is unavailable.

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

Never commit a token, place it in a URL, or log the resolved header set.
Open `references/authentication.md` for OAuth, header, and secure-state decisions.

## Paths and Expansion

- `${CLAUDE_PLUGIN_ROOT}` points to bundled, ephemeral plugin files and MUST NOT hold generated state.
- `${CLAUDE_PLUGIN_DATA}` is the persistent writable directory for installed dependencies, caches, and server state.
- `${CLAUDE_PROJECT_DIR}` is the stable project root.
- User environment variables may be referenced in documented MCP configuration fields such as `command`, `args`, `env`, `url`, and headers.

Do not claim substitution for an arbitrary field without verifying that field's schema.

## Plugin Tool Names

Plugin-bundled MCP tools include both plugin and server names:

```text
mcp__plugin_<plugin-name>_<server-name>__<tool-name>
```

For plugin `example-plugin`, server `remote-api`, and tool `search`, the callable name is:

```text
mcp__plugin_example-plugin_remote-api__search
```

Use the full name in agent tool lists, skill `allowed-tools`, permission rules, and hook matchers.
A bare manual-server name such as `mcp__remote-api__search` does not match the plugin tool.

Where a configured server name is required, such as an `mcp_tool` hook, use `plugin:<plugin-name>:<server-name>`.

Prefer exact tool names.
For a hook matcher, use a whole-server regular expression only when every current and future tool from that server is trusted:

```text
mcp__plugin_example-plugin_remote-api__.*
```

Agent tool lists, skill `allowed-tools`, and permission rules use their own pattern grammars.
Do not copy a hook regular expression into those surfaces without verifying that surface's syntax.

## Runtime Limits

- `MCP_TIMEOUT` controls server startup timeout.
- A server `timeout` value controls per-tool wall-clock timeout in milliseconds and overrides `MCP_TOOL_TIMEOUT` for that server.
- `CLAUDE_CODE_MCP_TOOL_IDLE_TIMEOUT` controls the no-response/no-progress idle window.
- `MAX_MCP_OUTPUT_TOKENS` raises the warning and truncation budget for tools that do not declare their own result-size limit.
- `alwaysLoad: true` loads all tools from a small server at session start and blocks startup until that server connects, subject to the connection timeout.

Do not add pooling, retry, or cache fields that are absent from the MCP configuration schema.
Open `references/performance.md` when tuning a real connection or output problem.

## Verification

- `.mcp.json` or manifest JSON parses
- `claude plugin validate .` reports no blocking error
- `/reload-plugins` reconnects changed plugin servers
- `/mcp` shows each expected server and authentication state
- one intended tool succeeds through its plugin-scoped name
- a stdio server returns a newline-delimited initialize response without `Content-Length` headers
- missing credentials fail without exposing secrets
- generated state is written only under `${CLAUDE_PLUGIN_DATA}`
- remote URLs use HTTPS or WSS outside localhost
- configured timeout and output limits match an observed need

## Pitfalls

- Do not omit the top-level `mcpServers` wrapper in `.mcp.json`.
- Do not invent provider endpoints; use the server operator's authoritative URL.
- Do not describe SSE as the preferred remote transport.
- Do not reject WebSocket; Claude Code supports `type: "ws"` through JSON configuration.
- Do not promise OAuth for WebSocket.
- Do not use unscoped MCP tool names for plugin-bundled servers.
- Do not write logs or caches under `${CLAUDE_PLUGIN_ROOT}`.
- Do not invent `/mcp test`, `/mcp activate`, or `/mcp authorize` commands.

## References

- `references/transport-types.md` - open for transport selection, supported fields, and lifecycle differences.
- `references/authentication.md` - open for OAuth, static or dynamic headers, and secret-handling boundaries.
- `references/performance.md` - open for startup, tool-call, idle, context, or output-size tuning.
