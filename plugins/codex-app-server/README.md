---
description: >-
  Stdio MCP access to the running Codex app-server for Claude Code and Codex.
metadata:
  reference:
    Codex app-server:
      url: https://developers.openai.com/codex/app-server/
    Codex turn requests:
      version: rust-v0.162.1
      url: https://raw.githubusercontent.com/openai/codex/rust-v0.162.1/codex-rs/app-server/src/request_processors/turn_processor.rs
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
    Codex MCP tool discovery:
      url: https://developers.openai.com/codex/mcp/
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
The plugin starts no daemon.
Authorized messages can wait in a process-local queue for the active turn to end.

Each tool call connects to `CODEX_APP_SERVER_SOCKET` when set.
Otherwise, it uses `app-server-control/app-server-control.sock` under `CODEX_HOME`, defaulting to `~/.codex`.
Inspection and immediate calls initialize their own connection and close it after the result.
Native commands retain their connection while execution is pending, allowing concurrent write, resize, and terminate tools for that process ID.
Streamed command output is returned as native `command/exec/outputDelta` entries in the final result's `notifications` array.
Filesystem watches keep their connections until native unwatch, socket loss, or MCP transport closure.
Watch changes use standard MCP logging notifications without retaining event history.
MCP cancellation prevents dispatch after preparation and closes owned immediate or command connections.
Queued delivery keeps a separate connection until its batch ends, is cancelled, expires, or loses the socket.
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
| `codex_send` | Start an idle thread turn or queue text until active work ends. |
| `codex_steer` | Queue text by default or explicitly steer a supported active turn with its expected turn ID. |
| `codex_queue` | Inspect queue delivery receipts or cancel undelivered messages. |
| `codex_interrupt` | Read the active turn and interrupt it by ID. |
| `codex_fork` | Copy a thread's history into a new thread without starting a turn. |
| `codex_rename` | Set a thread's display name. |
| `codex_goal` | Read, set, or clear a thread's goal. |
| `codex_loaded` | List a page of thread IDs loaded in daemon memory. |
| `codex_compact` | Request context compaction without waiting for completion. |
| `codex_mcp_tool` | Call a configured MCP tool inside a temporary ephemeral thread. |
| `codex_methods` | Refresh native tools and discover installed-version methods and parameter schemas. |
| `codex_<method>` | Send one native ClientRequest with its generated parameter schema, or return its safety refusal. |

`codex_steer` and `codex_interrupt` refuse when the last turn is not `inProgress`.
They read the turn ID themselves.
The daemon checks `expectedTurnId` for explicit steering, preventing a stale read from steering a replacement turn.
Native `turn/start` can steer an active turn, so `codex_send` now checks status before choosing a fresh turn or a queue.
Native `turn/interrupt` submits the cancellation operation with the inspected active turn ID.
The plugin does not send provider WebSocket messages directly.

## Running Threads And Queues

`codex_send` queues active-thread text without sending `turn/start` or `turn/steer` to the active turn.
`codex_steer` defaults to `mode: "queue"` for the same reason.
A queued result contains `status: "queued"`, a message ID, a thread ID, and a reason.
Queued delivery resumes the thread to receive completion notifications and rechecks its last turn before each fresh `turn/start`.
Messages deliver in insertion order, waiting for each delivered turn to end before starting the next message.
Completion notifications are buffered through the delivery request, including completion before its response arrives.
A replacement active turn keeps the queue waiting.

Use `mode: "steer"` only after verifying provider support for active input.
Accepted input returns `status: "steered"`, which acknowledges app-server acceptance rather than turn completion.
A known `unsupported websocket request type: response.interrupt` error in thread history selects the queue even in explicit steering mode.
The process also remembers that rejection after a steering or interruption response.
An unsupported or ambiguous steering response returns `status: "refused"` without retrying or resending its text.
Provider errors can arrive after steering acceptance, so inspect the thread afterward when using explicit steering.
Native interruption returns `status: "interrupted"` on acknowledgment or `status: "refused"` with its failure reason.

Inspect delivery with `codex_queue` and the thread ID.
Receipts distinguish queued, delivering, started, completed, failed, interrupted, and refused messages, without returning queued text.
Use `action: "cancel"` to refuse undelivered messages and close the queue connection.
Cancellation does not interrupt a turn already started.
An interrupted socket makes any in-flight delivery unconfirmed and is never retried automatically.
Writer errors stop the remaining batch without deleting locks, forcing another writer, or repeating a write.
Queues expire after 30 minutes, accept up to 32 pending messages per thread, and retain up to 32 receipts for the latest batch per thread.
The process holds at most 128 thread queue entries, retiring closed entries when necessary.
Pending messages and receipts are lost when this MCP process stops.

Curated send, steer, interrupt, and queue delivery serialize writes per thread inside this process.
Keep one writer per thread across hosts as well.
The native fresh-turn request has no idle-only precondition, so external writers must coordinate before queued delivery.
On an active-writer error, let the existing writer release the thread instead of deleting its lock.
Read the thread first and use these tools only under authorized instructions.

## Installed-Version Catalog

At startup, the server runs `codex --version` and `codex app-server generate-json-schema` into a disposable directory.
It reads ClientRequest, ClientNotification, ServerRequest, and ServerNotification, then removes the generated files.
Every ClientRequest method becomes an individual MCP tool with its native parameter schema, including reachable definitions.
Object parameters become direct tool arguments.
Boolean property schemas become equivalent object schemas because the MCP SDK requires object-valued top-level property definitions.
`true` becomes `{}`, and `false` becomes `{ "not": {} }`, preserving validation semantics.
Null, scalar, and union parameters use a `params` argument containing the native value and schema.
The wrapper requires `params` only when the native request requires it, and dispatch unwraps it without transforming the value.
Notifications and server requests remain discovery-only.
The plugin provides no generic `codex_call` escape hatch.
The former `CODEX_APP_SERVER_METHODS` allowlist is removed, so no configuration change is needed for new methods.

Set `CODEX_BIN` to an executable path when `codex` is unavailable through `PATH`.
The executable must match the daemon version, especially when the socket points to another installation.
No daemon schema-discovery request is needed, and generation never starts a daemon.

### Stable Tool Names

For methods with alphanumeric path segments, prepend `codex_` and replace slashes with underscores, preserving case.
For example, `thread/goal/get` becomes `codex_thread_goal_get` and `fs/readFile` becomes `codex_fs_readFile`.
A name containing punctuation or literal underscores uses `codex_method_` followed by its full SHA-256 method-name digest.
The same fallback covers curated-name collisions, the reserved `codex_method_` namespace, and readable names exceeding 128 characters.
This mapping stays stable when other methods are added or removed.
Duplicate derived names reject the catalog atomically instead of overwriting another tool.

Curated names such as `codex_threads`, `codex_send`, and `codex_goal` continue working with their existing guards.
Their native methods also have individual tools accepting the full native parameter schema.
Use `codex_methods` with `method` to inspect one schema or `prefix` to filter discovery.
Use `kind` to inspect any of the four protocol directions.
These discovery filters do not restrict the registered tool list.

### Refresh And Host Loading

Each MCP `tools/list` request and `codex_methods` call regenerates discovery and compares the CLI version and schema hash.
Concurrent refreshes share generation, and unchanged schemas retain their registrations.
Changed schemas atomically replace the native tools and emit `notifications/tools/list_changed`.
The installed MCP SDK supports this notification, so refresh works without restarting or updating the plugin.
Refresh is driven by host listing or explicit discovery, with no background polling.
A schema-only change within the same CLI version is detected at the next discovery or listing.
Native calls also recheck the CLI version, refresh changed registrations, and validate their full ClientRequest envelope before opening a socket.
A version change encountered during a call updates validation and emits the tool-list notification before dispatch.

The installed Codex 0.162.1 schema contains 105 ClientRequest methods.
It produces 119 tools: 105 native methods, 13 curated adapters, and `codex_methods`.
Four native methods are refusal-only tools.
Schema parameters and descriptions increase the full catalog size as Codex adds methods.
Use the host's native deferred tool loading and tool search to load individual schemas on demand.
Claude Code's `ToolSearch` defers MCP definitions by default on supported configurations.
Codex hosts manage native tool discovery and long output storage themselves.
The plugin adds no tool-count caps, custom catalog paging, output truncation, or host configuration changes.

### Safety And Failure Behavior

Every native description names its method, read or mutating class, authorization requirements, and thread writer rules.
Read-only annotations use the existing reviewed allowlist, and unknown methods remain mutating regardless of their names.
`readOnlyHint` is true and `destructiveHint` is false for reviewed reads.
All other native tools carry `readOnlyHint: false` and `destructiveHint: true` conservatively.
Annotations describe behavior and do not authorize calls.
Mutations require a direct human instruction or assigned contract authorizing the operation.
Keep one writer per thread, inspect it before steering, and never force another writer or delete writer locks.
Prefer curated send, steer, and interrupt adapters for their active-turn guards and queue handling.

`account/rateLimitResetCredit/consume` is listed but always refused before validation or connecting.
`thread/archive`, `thread/unarchive`, and `thread/delete` are also refusal-only tools and require the native CLI.
Usage tools retain the Orchestrator-only authorization notes.
Server-initiated requests, including approvals, remain refused by the connection layer.
Invalid parameters and unavailable native validation return errors without connecting or altering supplied parameters.

A failed generation or catalog derivation reports its reason on stderr and preserves the last successful native registrations.
Discovery returns that refresh error instead of advertising unregistered tools.
Without a successful catalog, the 13 curated adapters and discovery tool remain available.
Native calls still require current schema validation, even after a failed refresh.
The generator has a 30-second deadline and writes no persistent schema cache.
Missing parameter definitions and duplicate names cause an explicit catalog error.
They never gain a generic dispatch bypass.

## Curated Adapters And Models

Curated thread tools keep reviewed source-kind and goal-status enums, convenience parameters, and active-turn guards.
Use the native per-method tools for new parameters and enum values after inspecting their descriptions and authorization notes.
Policy sets and read-only annotations remain reviewed local rules because schemas do not establish authorization or safety.

Use `codex_model_list` for the running app-server's model catalog.
Inspect its parameters with `codex_methods` or the host's native tool search first.
For CLI diagnostics, `codex debug models` returns raw model catalog JSON, and `--bundled` skips refresh.
Use `codex app-server generate-json-schema` or `codex app-server generate-ts` for native protocol schemas.

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
Failed is a terminal turn outcome, so idle transitions request one latest turn with item contents omitted to emit a separate failed event when needed.
Initial status reads exclude turn history.
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
Inspection errors emit an `inspection` systemError event for the affected thread and leave other watches running.
An initial loaded-list error uses a null thread ID and leaves wildcard broadcasts running.
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
- `lib/native-session.mjs` owns command and watch connection lifetimes, streaming output, and MCP shutdown cleanup.
- `lib/turn-tools.mjs` owns active-turn guards for steering and interruption.
- `lib/turn.mjs` shares turn-start and buffered completion handling.
- `lib/queue.mjs` owns message delivery, receipts, deadlines, and process-local writer serialization.
- `lib/thread-admin.mjs` registers small thread administration tools.
- `lib/catalog.mjs` converts, filters, and exposes installed-version method schemas.
- `lib/schema.mjs` owns schema generation, version and hash caching, parameter schemas, and native ClientRequest validation.
- `lib/policy.mjs` owns refusal, native CLI, and usage policies.
- `AGENTS.md` records catalog and policy maintenance requirements.
- `tests/` verifies the stdio server against a disposable Unix-socket fixture.
- The Sinon marketplace entry declares Claude Code identity and the package root.

Run `pnpm run check:codex-app-server` from the repository to test the runtime.
The check installs the package's pinned dependencies in a temporary directory and removes that directory afterward.
It exercises a fixture daemon only.
