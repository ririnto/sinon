---
name: mcp-integration
description: >-
  Integrate Model Context Protocol servers into Claude Code plugins via `.mcp.json` or the `mcpServers` manifest key with stdio, SSE, or HTTP transports.
  Use when configuring MCP server entries, choosing between stdio and network transports, or wiring external tool servers into a plugin manifest.
---

# MCP Integration

Configure Model Context Protocol servers to expose external service tools within Claude Code plugins.

## Goal

Add MCP server definitions to a plugin so external services become available as tool calls within agents and plugin runtime code.

## Scope

This skill owns:

- `.mcp.json` at plugin root (dedicated configuration)
- `mcpServers` field in `plugin.json` (inline configuration)
- Environment variable expansion and token handling
- MCP tool naming and allowed-tools scoping
- Security rules for credentials and transport

## Operating rules

1. MUST use `${CLAUDE_PLUGIN_ROOT}` for all bundled paths - never hardcoded absolute paths.
2. MUST use HTTPS for remote servers.
3. MUST NOT hardcode credentials.
   - Use environment variables or OAuth flows.
4. Prefer specific tool names in `allowed-tools`.
   - A whole-server pattern such as `mcp__<server>__*` is acceptable only when you intentionally trust every tool that server exposes.
   - Never use a bare `mcp__*` that admits every tool from every server.
5. Plugin-root `.mcp.json` is auto-discovered and SHOULD stay out of `plugin.json` when it is the only MCP surface.
6. MAY use `.mcp.json` (recommended for multi-server plugins) or `mcpServers` in `plugin.json` for custom paths or inline single-server configuration.
   - When `mcpServers` declares a string path, that file MUST exist.
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
  "$schema": "https://json.schemastore.org/claude-code-plugin-manifest.json",
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

- `stdio` - Local process (custom servers).
  - Use `command` + `args` + `env`.
- `SSE` - Hosted OAuth (GitHub, Asana, etc.).
  - Use `type: "sse"` + `url`.
- `HTTP` - REST + token (custom APIs).
  - Use `type: "http"` + HTTPS URL + bearer token in `headers`.

MCP in Claude Code supports `stdio`, `sse`, and `http` transports only.
WebSocket transports are not supported.

See `references/transport-types.md` for lifecycle details, failure modes, selection decision tree, and performance characteristics.

## Environment variable expansion

All fields support `${VAR_NAME}` substitution from the user's environment.
Use `${CLAUDE_PLUGIN_ROOT}` for portable bundled paths:

```json
{
  "command": "${CLAUDE_PLUGIN_ROOT}/servers/my-server",
  "env": {
    "API_KEY": "${MY_API_KEY}",
    "LOG_FILE": "${CLAUDE_PLUGIN_ROOT}/logs/mcp.log"
  }
}
```

## MCP Tool Naming and Allowlists

MCP tools are named `mcp__<server>__<tool>`, where `<server>` is the key used for the server in `.mcp.json` or `mcpServers`.

When an allowlist accepts MCP tools, prefer exact tool names:

```text
mcp__github__search_repositories
mcp__github__create_issue
```

### Correct (Specific Tools Only)

```text
mcp__github__search_repositories
mcp__github__create_issue
```

### Acceptable (Whole Server, Intentional Trust)

```text
mcp__github__*
```

### Broken (Bare Wildcard Admits Every Server)

```text
mcp__*
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

OAuth (SSE/HTTP): Handled automatically by Claude Code.
User authenticates in browser on first use.

Token (HTTP): Pass via environment variables in headers:

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
> This example exposes credentials and uses unencrypted transport.
> Use HTTPS and environment variables instead.

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

After changing MCP configuration, reload the plugin or session so the new servers load, then run `/mcp` to confirm each server and its tools appear.

`/mcp` lists every configured server and its tools; invoke a tool from the intended plugin surface to confirm the live connection works.

## Testing checklist

- [ ] Configuration JSON is syntactically valid
- [ ] All file paths use `${CLAUDE_PLUGIN_ROOT}`
- [ ] Remote URLs use HTTPS, not HTTP
- [ ] No hardcoded credentials
- [ ] Required environment variables documented in README
- [ ] `/mcp` command shows servers and tools
- [ ] Tool calls succeed from the intended plugin surface
- [ ] Error handling for connection failures

## Pitfalls

DO: Use `${CLAUDE_PLUGIN_ROOT}` for bundled paths, document required env vars, use HTTPS, prefer specific tool names, test with `/mcp` after config changes.

DON'T: Hardcode absolute paths, commit credentials, use HTTP instead of HTTPS, use a bare `*` that admits every server, skip error handling.

## References

For detailed patterns and advanced topics, see:

- `references/transport-types.md` - Transport-specific lifecycle, failure modes, and OAuth internals.
- `references/authentication.md` - OAuth, token rotation, and secret hygiene patterns.
- `references/performance.md` - Lazy loading, connection pooling, and MCP latency debugging.
