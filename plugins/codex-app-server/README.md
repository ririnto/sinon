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
    Claude Code Monitor:
      url: https://code.claude.com/docs/en/tools-reference.md#monitor-tool
    Claude Code plugin monitors:
      url: https://code.claude.com/docs/en/plugins/manifest-reference.md#monitors
    Codex native status broadcasts:
      version: rust-v0.162.1
      url: https://raw.githubusercontent.com/openai/codex/rust-v0.162.1/codex-rs/app-server/src/thread_status.rs
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
Provider gateways must support the underlying steering and interruption requests.
A gateway rejecting `response.interrupt` can fail the active turn instead of interrupting it safely.
Verify provider support before steering or interrupting valuable work.
The tools propagate daemon and provider errors without retrying.
`codex_send` relies on daemon-enforced active-turn and writer guards during resume and turn start.
It returns refusals as MCP errors without retrying or forcing a writer.
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

## Thread Status Monitor

`monitors/monitors.json` declares the packaged `codex-thread-status` monitor for Claude Code's native Monitor contract.
Claude Code starts plugin monitors in eligible interactive sessions and receives each stdout line as an event.
The monitor command uses the same local npm package execution as the MCP server.
It runs the packaged `codex-thread-monitor` executable without an external watch script.

Set `CODEX_APP_SERVER_MONITOR_THREADS` to comma-separated authorized thread IDs before starting the host session.
With no selected threads, the monitor exits successfully without connecting.
Use `*` to watch all native status broadcasts, with an initial snapshot of the first loaded-thread page.
Set `CODEX_APP_SERVER_MONITOR_STATUSES` to select statuses, defaulting to `idle,active,notLoaded,failed,systemError`.
These environment variables configure the runtime and do not change plugin installation.
Plugin monitors cannot use `${user_config.*}` under the native host contract.

The monitor emits JSON lines with `event: "threadStatus"`, `threadId`, `status`, and `previous`.
It filters unrelated threads and repeated statuses.
Native `thread/status/changed` broadcasts provide idle, active, notLoaded, and systemError.
Failed is a terminal turn outcome, so idle transitions trigger one read of the latest turn to emit a separate failed event when needed.
Terminal turn and error notifications also expose failures when the daemon sends them to this connection.
Transient errors marked `willRetry` do not produce terminal failures.
Events are serialized behind the initial snapshot, avoiding out-of-order startup notifications.
The monitor never resumes a thread, starts a turn, responds to an approval, or claims a writer.
Server requests retain the connection-layer refusal and cannot become monitor events.

For an explicit native Monitor tool call, use the packaged executable as its command and set the host deadline.
This repository-relative example also runs outside Claude Code:

```sh
NODE_USE_SYSTEM_CA=1 npm exec --yes --install-links --package=./plugins/codex-app-server -- codex-thread-monitor --thread AUTHORIZED_THREAD_ID --timeout-seconds 300
```

The host's native Monitor tool owns its watch deadline and cancellation.
The packaged plugin monitor runs until its host stops it.
Outside Claude Code, use the same executable with repeatable `--thread` and `--status` arguments.
An optional `--timeout-seconds` accepts a positive deadline up to 1800 seconds.
SIGINT, SIGTERM, and deadlines close the watcher connection and exit successfully, including during initialization.
Socket loss emits a transport event with `status: "systemError"` and `threadId: null`, then exits with code 1.
Transport errors remain distinct from thread systemError transitions.
The command does not reconnect or poll, and the host can start a new watch when needed.

## Package Contents

Thread cleanup uses native `codex archive`, `codex unarchive`, and `codex delete` with `--remote unix://`.
The plugin adds no dedicated cleanup tools.

- `.codex-plugin/plugin.json` declares Codex identity and the MCP configuration path.
- `mcp.json` and `.mcp.json` register the Codex and Claude Code stdio server.
- `package.json` declares the executable and its MCP SDK, WebSocket, and schema dependencies.
- `server.mjs` creates the MCP server, retains shared instructions, registers the feature modules, and starts stdio transport.
- `lib/connection.mjs` owns control-socket transport, JSON-RPC requests, and connection cleanup.
- `monitor.mjs` parses monitor configuration and emits JSON lines.
- `lib/monitor.mjs` watches native status broadcasts and terminal failures.
- `monitors/monitors.json` registers the native Claude Code plugin monitor.
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
