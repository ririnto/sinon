---
description: >-
  Documented Claude Code MCP startup, tool-call, idle, context-loading, and output-size controls.
---

# MCP Performance

Open this reference only after observing slow startup, a timed-out tool, idle aborts, excessive tool context, or oversized output.

## Startup

`MCP_TIMEOUT` controls the startup connection timeout.
Increase it only when a server demonstrably needs longer to start.

`alwaysLoad: true` forces all tools from one server into the initial context and waits for that server to connect, capped by the standard connection timeout.
Use it for a small, essential tool set; otherwise leave tool search to load tools on demand.

## Tool Calls

Set a per-server `timeout` in milliseconds for a real wall-clock requirement:

```json
{
  "mcpServers": {
    "slow-report": {
      "type": "http",
      "url": "https://mcp.example.com/mcp",
      "timeout": 600000
    }
  }
}
```

This overrides `MCP_TOOL_TIMEOUT` for that server.
Values below 1000 milliseconds fall back to the environment setting or default.
Progress notifications do not extend the wall-clock timeout.

`CLAUDE_CODE_MCP_TOOL_IDLE_TIMEOUT` controls how long a call may produce neither a response nor progress notification.
Set it to `0` only when deliberately disabling idle detection.

## Output and Context

Claude Code warns when tool output exceeds the default MCP token budget.
Raise `MAX_MCP_OUTPUT_TOKENS` only when pagination, filtering, or a server-declared result-size annotation cannot solve the real use case.

Prefer:

- narrow tool schemas
- pagination and filters
- compact text results
- exact plugin tool allowlists
- `alwaysLoad` only for a small essential server

## Boundaries

Do not add undocumented pool-size, retry-count, cache, compression, or log-path fields to `.mcp.json`.
Claude Code already owns supported reconnection behavior for remote transports.
Application-level caching belongs in the server and writable state belongs under `${CLAUDE_PLUGIN_DATA}`.

## Measurement

- record connection time separately from tool-call time
- reproduce with one server and one tool
- distinguish wall-clock timeout from idle timeout
- measure output size before raising the token budget
- confirm a tuning change after `/reload-plugins`
