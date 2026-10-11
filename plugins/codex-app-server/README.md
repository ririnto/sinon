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

This plugin exposes the running Codex app-server through guarded stdio MCP tools and an installed-version method catalog.
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
| `codex_threads` | List stored, non-archived threads using state metadata without rollout repair. |
| `codex_tail` | Read trailing messages with each turn's ID and status without resuming the thread. |
| `codex_start` | Create a thread and optionally name it or send its first turn. |
| `codex_send` | Resume a thread, start a turn, and optionally wait for completion. |
| `codex_steer` | Read the active turn and send steering text with its expected turn ID. |
| `codex_interrupt` | Read the active turn and interrupt it by ID. |
| `codex_fork` | Copy a thread's history into a new thread without starting a turn. |
| `codex_rename` | Set a thread's display name. |
| `codex_goal` | Read, set, or clear a thread's goal. |
| `codex_loaded` | List a page of thread IDs loaded in daemon memory. |
| `codex_compact` | Request context compaction without waiting for completion. |
| `codex_mcp_tool` | Call a configured MCP tool inside a temporary ephemeral thread. |
| `codex_call` | Send a raw request while refusing reset-credit consumption and thread cleanup. |
| `codex_methods` | Discover installed-version methods and inspect their parameter schemas when generation succeeds. |

`codex_steer` and `codex_interrupt` refuse when the last turn is not `inProgress`.
They read the turn ID themselves and never resume the thread or start a new turn.
The daemon checks `expectedTurnId` for steering, preventing a stale read from steering a replacement turn.
Read the thread first and use these tools only under authorized steering instructions.
Keep one writer per thread.
On an active-writer error, let the existing writer release the thread instead of deleting its lock.

## Installed-Version Catalog

At startup, the server runs `codex app-server generate-json-schema` into a disposable directory.
It reads `ClientRequest.json` and removes the directory after reading the schema.
Set `CODEX_BIN` to an executable path when `codex` is not available through `PATH`.
The executable must match the daemon version, especially when the socket points to a different installation.
The generated ClientRequest catalog contains client requests, not server-initiated approval requests.
No daemon schema-discovery request is needed.
Restart the MCP server after upgrading Codex to regenerate the catalog without a plugin release.

By default, the server exposes 13 curated tools plus `codex_methods` when generation succeeds.
The catalog does not add every protocol method to the default tool list.
Use `codex_methods` to list names, or supply `method` to retrieve one parameter schema for `codex_call`.
Set `CODEX_APP_SERVER_METHODS` to comma-separated method names or namespace prefixes to expose generated tools.
For example, `fs,config/read` exposes filesystem methods and the configuration read method.
Generated names replace slashes with underscores, such as `codex_fs_readFile`.
Restart the MCP server after changing this setting.
The plugin does not edit host configuration or credentials.

The startup schema from Codex 0.162.1 contains 105 client methods.
Enabling entire namespaces increases tool count and tool-schema context size.
Keep the allowlist narrow and fetch individual schemas through `codex_methods` when needed.
Read-only annotations use a reviewed method allowlist.
Unknown methods conservatively carry a mutating annotation, regardless of their name.
Annotations describe behavior and do not authorize calls.
Generated mutations require a direct human instruction or an assigned contract authorizing the operation.

Curated operations never get duplicate generated tools, even when their namespaces are enabled.
Reset-credit consumption is always refused, and thread archive, unarchive, and delete remain native CLI operations.
Usage methods are not exposed as generated tools and remain reserved for the Orchestrator through `codex_call`.
Server-initiated requests, including approvals, remain refused by the connection layer.

If schema generation, parsing, or conversion fails, startup reports the reason on stderr.
Only curated tools remain available in that case.
The generator has a 30-second deadline and never starts a daemon.

## Package Contents

Thread cleanup uses native `codex archive`, `codex unarchive`, and `codex delete` with `--remote unix://`.
The plugin adds no dedicated cleanup tools.

- `.codex-plugin/plugin.json` declares Codex identity and the MCP configuration path.
- `mcp.json` and `.mcp.json` register the Codex and Claude Code stdio server.
- `package.json` declares the executable and its MCP SDK, WebSocket, and schema dependencies.
- `server.mjs` creates the MCP server, retains shared instructions, registers the feature modules, and starts stdio transport.
- `lib/connection.mjs` owns control-socket transport, JSON-RPC requests, and per-call connection cleanup.
- `lib/thread-tools.mjs` registers thread inspection and steering tools, including turn completion handling.
- `lib/mcp-tool.mjs` registers the configured MCP tool proxy.
- `lib/raw-call.mjs` registers raw requests with the shared refusals.
- `lib/turn-tools.mjs` owns active-turn guards for steering and interruption.
- `lib/thread-admin.mjs` registers small thread administration tools.
- `lib/catalog.mjs` generates, converts, filters, and exposes installed-version method schemas.
- `lib/policy.mjs` separates curated, refused, native CLI, and usage methods.
- `AGENTS.md` records catalog and policy maintenance requirements.
- `tests/` verifies the stdio server against a disposable Unix-socket fixture.
- The Sinon marketplace entry declares Claude Code identity and the package root.

Run `pnpm run check:codex-app-server` from the repository to test the runtime.
The check installs the package's pinned dependencies in a temporary directory and removes that directory afterward.
It exercises a fixture daemon only.
