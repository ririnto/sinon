---
name: mcp-integration
description: >-
  Integrate Model Context Protocol servers into Claude Code plugins via `.mcp.json` or the `mcpServers` manifest key with stdio, SSE, HTTP, or WebSocket transports.
---

# MCP Integration

Configure Model Context Protocol servers to expose external service tools within Claude Code plugins.

## Goal

Add MCP server definitions to a plugin so external services become available as tool calls within commands and agents.

## Scope

This skill owns:

- `.mcp.json` at plugin root (dedicated configuration)
- `mcpServers` field in `plugin.json` (inline configuration)
- Environment variable expansion and token handling
- MCP tool naming and allowed-tools scoping
- Security rules for credentials and transport

## Operating rules

1. MUST use `${CLAUDE_PLUGIN_ROOT}` for all bundled paths—never hardcoded absolute paths.
2. MUST use HTTPS for remote servers, WSS for WebSocket connections.
3. MUST NOT hardcode credentials; use environment variables or OAuth flows.
4. MUST NOT use wildcards in `allowed-tools`—pre-allow only specific tool names.
5. When the manifest declares `"mcpServers": "./.mcp.json"`, plugin-root `.mcp.json` MUST exist; when plugin-root `.mcp.json` exists, the manifest SHOULD declare `"mcpServers": "./.mcp.json"`.
6. MAY use `.mcp.json` (recommended for multi-server plugins) or `mcpServers` in `plugin.json` (for single-server plugins).
7. SHOULD test MCP connectivity locally with `/mcp` before publishing.

## Configuration Methods

### Dedicated .mcp.json (Recommended for Multiple Servers)

```jsonc
{
  "github": {
    "type": "sse",
    "url": "https://mcp.github.com/sse"
  },
  "database": {
    "command": "${CLAUDE_PLUGIN_ROOT}/servers/db-server",
    "env": {
      "DB_URL": "${DATABASE_URL}"
    }
  }
}
```

### Inline mcpServers in plugin.json (For Single-Server Plugins)

```jsonc
{
  "$schema": "https://anthropic.com/claude-code/plugin.schema.json",
  "name": "my-plugin",
  "author": { "name": "you" },
  "mcpServers": {
    "api": {
      "type": "http",
      "url": "https://api.example.com/mcp",
      "headers": {
        "Authorization": "Bearer ${API_TOKEN}"
      }
    }
  }
}
```

## Transport Types

- `stdio` — Local process (custom servers). Use `command` + `args` + `env`.
- `SSE` — Hosted OAuth (GitHub, Asana, etc.). Use `type: "sse"` + `url`.
- `HTTP` — REST + token (custom APIs). Use `type: "http"` + HTTPS URL + bearer token in `headers`.
- `WebSocket` — Real-time bidirectional (streaming). Use `type: "ws"` + WSS URL + auth headers.

See `references/transport-types.md` for lifecycle details, failure modes, selection decision tree, and performance characteristics.

### WebSocket (BROKEN - Uses Plaintext, Exposes Credentials)

> [!CAUTION]
>
> This example uses unencrypted plaintext transport. Use `wss://` instead of `ws://` to encrypt credentials and data.

```jsonc
{
  "realtime": {
    "type": "ws",
    "url": "ws://mcp.example.com/ws",
    "headers": { "Authorization": "Bearer ${TOKEN}" }
  }
}
```

## Environment variable expansion

All fields support `${VAR_NAME}` substitution from the user's environment. Use `${CLAUDE_PLUGIN_ROOT}` for portable bundled paths:

```json
{
  "command": "${CLAUDE_PLUGIN_ROOT}/servers/my-server",
  "env": {
    "API_KEY": "${MY_API_KEY}",
    "LOG_FILE": "${CLAUDE_PLUGIN_ROOT}/logs/mcp.log"
  }
}
```

## MCP tool naming and allowed-tools

MCP tools are prefixed: `mcp__plugin_{{plugin-name}}_{{server-name}}__{{tool-name}}`

Pre-allow specific tools in command frontmatter:

```markdown
---
allowed-tools:
  - mcp__plugin_github_github__search_repositories
  - mcp__plugin_github_github__create_issue
---
```

### Broken (Wildcard Too Permissive)

```markdown
---
allowed-tools:
  - mcp__plugin_github_github__*
---
```

### Correct (Specific Tools Only)

```markdown
---
allowed-tools:
  - mcp__plugin_github_github__search_repositories
  - mcp__plugin_github_github__create_issue
---
```

## Multi-server example

Plugin with multiple MCP servers in `.mcp.json`:

```jsonc
{
  "github": {
    "type": "sse",
    "url": "https://mcp.github.com/sse"
  },
  "slack": {
    "type": "sse",
    "url": "https://mcp.slack.com/sse"
  },
  "dev-db": {
    "command": "python3",
    "args": ["-m", "postgres_mcp"],
    "env": {
      "DATABASE_URL": "${POSTGRES_URL}"
    }
  }
}
```

## Authentication patterns

OAuth (SSE/HTTP): Handled automatically by Claude Code. User authenticates in browser on first use.

Token (HTTP/WebSocket): Pass via environment variables in headers:

```json
{
  "type": "http",
  "headers": {
    "Authorization": "Bearer ${API_TOKEN}"
  }
}
```

Environment variables (stdio): Pass to server process:

```json
{
  "command": "python",
  "args": ["-m", "mcp_server"],
  "env": {
    "DATABASE_URL": "${DB_URL}",
    "API_KEY": "${API_KEY}"
  }
}
```

## Security

### Broken (HTTP Without Encryption, Hardcoded Token)

> [!CAUTION]
>
> This example exposes credentials and uses unencrypted transport. Use HTTPS and environment variables instead.

```jsonc
{
  "type": "http",
  "url": "http://api.example.com/mcp",
  "headers": {
    "Authorization": "Bearer secret_token_12345"
  }
}
```

### Correct (HTTPS, Environment Variable, No Wildcards)

```jsonc
{
  "type": "http",
  "url": "https://api.example.com/mcp",
  "headers": {
    "Authorization": "Bearer ${API_TOKEN}"
  }
}
```

Document required environment variables in plugin README.

## First safe commands

Validate JSON syntax:

```sh
python3 -m json.tool .mcp.json
```

List active MCP servers in Claude Code:

```text
/mcp
```

Test a specific MCP server's connectivity and tools after plugin restart (required after config changes):

```text
/mcp test <server-name>
```

Distinction: `/mcp` displays all configured servers and their tools. `/mcp test {{server}}` establishes a live connection to verify the server responds correctly and tool definitions are accessible.

## Testing checklist

- [ ] Configuration JSON is syntactically valid
- [ ] All file paths use `${CLAUDE_PLUGIN_ROOT}`
- [ ] Remote URLs use HTTPS/WSS, not HTTP/WS
- [ ] No hardcoded credentials
- [ ] Required environment variables documented in README
- [ ] `/mcp` command shows servers and tools
- [ ] Tool calls succeed from commands
- [ ] Error handling for connection failures

## Pitfalls

DO: Use `${CLAUDE_PLUGIN_ROOT}` for bundled paths, document required env vars, use HTTPS/WSS, pre-allow specific tools only, test with `/mcp` after config changes.

DON'T: Hardcode absolute paths, commit credentials, use HTTP instead of HTTPS, pre-allow all tools with `*`, skip error handling.

## References

For detailed patterns and advanced topics, see:

- `references/transport-types.md` — Transport-specific lifecycle, failure modes, and OAuth internals.
- `references/authentication.md` — OAuth, token rotation, and secret hygiene patterns.
- `references/performance.md` — Lazy loading, connection pooling, and MCP latency debugging.
