---
description: >-
  Stdio MCP access to the running Codex app-server for Claude Code and Codex.
metadata:
  reference:
    Codex app-server:
      url: https://learn.chatgpt.com/docs/app-server.md
    npm exec:
      url: https://docs.npmjs.com/cli/v11/commands/npm-exec
    npm install-links:
      url: https://docs.npmjs.com/cli/v11/using-npm/config#install-links
    MCP TypeScript SDK:
      version: 1.32.1
      url: https://github.com/modelcontextprotocol/typescript-sdk/tree/v1.x
    Node.js system certificates:
      url: https://nodejs.org/docs/latest-v24.x/api/cli.html#node_use_system_ca1
    Codex plugin MCP loading:
      url: https://raw.githubusercontent.com/openai/codex/main/codex-rs/codex-mcp/src/plugin_config.rs
    Claude Code plugin MCP:
      url: https://code.claude.com/docs/en/mcp.md
---

# Codex App-Server

This plugin exposes the running Codex app-server through six stdio MCP tools.
The server instructions, tool descriptions, and input descriptions carry usage and safety guidance without a separate skill.

Install `codex-app-server` from the Sinon marketplace in Claude Code:

```sh
claude plugin marketplace add ririnto/sinon
claude plugin install codex-app-server@sinon
```

In Codex, install `codex-app-server` from the Sinon repository marketplace and trust its MCP server.

## Runtime

Use Node.js 24.15 or newer on the 24.x line, npm, and a platform supporting Unix sockets.
The Codex app-server daemon must already be running with its Unix control socket available.
The plugin starts no daemon and schedules no work.

Each tool call connects to `CODEX_APP_SERVER_SOCKET` when set.
Otherwise, it uses `app-server-control/app-server-control.sock` under `CODEX_HOME`, defaulting to `~/.codex`.
Each call initializes its own connection and closes it after the result.
The runtime keeps the existing MCP SDK v1 API.
Upstream maintains v1 for bug fixes and security updates for at least six months after the July 27, 2026 v2 release.
Server-initiated requests, including approval requests, receive a refusal from this client.

Claude Code expands `CLAUDE_PLUGIN_ROOT` to the installed plugin root and launches:

```sh
NODE_USE_SYSTEM_CA=1 npm exec --yes --install-links --package="${CLAUDE_PLUGIN_ROOT}" -- codex-app-server
```

Codex resolves `cwd: "./"` relative to the installed plugin root and launches this command there:

```sh
NODE_USE_SYSTEM_CA=1 npm exec --yes --install-links --package=. -- codex-app-server
```

Codex's native manifest loader does not expand Claude path placeholders in MCP arguments.
`codex_mcp_tool` roots its ephemeral thread at the server's working directory, which is the installed package for Codex.
Claude Code uses the working directory supplied by its host.

Npm installs a copy of the local package and its declared dependencies into its execution cache.
The `--install-links` option installs file dependencies as packages instead of symlinking them.
The copied server resolves dependencies there, without requiring repository-local `node_modules` or a preparatory install.
The standalone npm manifest uses npm-compatible dependency versions and requires no pnpm workspace catalog at runtime.
The first launch requires registry access unless the dependencies are already cached.
`NODE_USE_SYSTEM_CA=1` enables Node.js system certificates alongside bundled certificates.

## Components

| Tool | Operation |
| --- | --- |
| `codex_threads` | List a newest-first page of non-archived threads from all supported source kinds using state metadata, without rollout repair. |
| `codex_tail` | Read trailing user and agent messages without resuming the thread. |
| `codex_start` | Create a thread, optionally name it, and optionally send its first turn. |
| `codex_send` | Resume a thread, start a turn, and optionally wait for its completion. |
| `codex_mcp_tool` | Call a configured MCP tool inside a temporary ephemeral thread. |
| `codex_call` | Send a raw app-server request, refusing reset-credit consumption before connecting. |

Thread cleanup uses native `codex archive`, `codex unarchive`, and `codex delete` with `--remote unix://`.
The plugin adds no dedicated cleanup tools.

- `.codex-plugin/plugin.json` declares Codex identity and the MCP configuration path.
- `mcp.json` and `.mcp.json` register the Codex and Claude Code stdio server.
- `package.json` declares the executable and its MCP SDK, WebSocket, and schema dependencies.
- `server.mjs` implements the six tools and their shared instructions.
- `tests/` verifies the stdio server against a disposable Unix-socket fixture.
- The Sinon marketplace entry declares Claude Code identity and the package root.

Run `pnpm run check:codex-app-server` from the repository to test the runtime.
The check installs the package's pinned dependencies in a temporary directory and removes that directory afterward.
It exercises a fixture daemon only.
