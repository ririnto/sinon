---
name: thread-control
description: Use when an authorized task needs to inspect, steer, monitor, or retire Codex threads through the running app-server, including coordinating active writers and verifying message delivery.
compatibility: Requires this plugin's MCP tools, Node.js 24.x, npm, the installed Codex CLI, and an existing Unix app-server control socket.
---

# Thread Control

Use the plugin's curated tools for thread control and native per-method tools when their full schema is needed.
Load unfamiliar methods through the host's tool search or `codex_methods`.
Tool availability and annotations do not grant authorization.
Mutation requires a direct human instruction or an assigned contract covering the operation and selected thread.
Reset-credit consumption remains refused, and only the Orchestrator reads account usage.

## Lease And Writer Ownership

Verify the current assignment or lease identifies the thread, permitted writes, owner, and stopping condition before writing.
A readable thread, loaded thread, or worktree attachment does not establish writer ownership.
Keep one writer per thread and one writer per shared file set across hosts.
When the daemon reports an active writer, identify its holder through `thread-writer-locks/THREAD_ID.lock` under `CODEX_HOME`, defaulting to `~/.codex`.
Let that host release the thread before continuing.
Never delete a writer lock, force a second writer, restart a shared daemon, or treat a refusal as authority to take over.
Release only resources owned by the completed assignment.

## Read, Steer, Verify

1. Use `codex_threads` to identify the authorized target, then `codex_tail` to read its latest messages, last turn ID, and status.
2. For an active turn, verify provider support and use `codex_steer` with explicit `mode: "steer"`, the thread ID, and the authorized text.
3. Read `codex_tail` again to verify the intended input reached the expected thread and turn.
   A `steered` acknowledgment establishes app-server acceptance, not completion or provider success.
4. Queue only after steering is refused and the refusal establishes that the text was not delivered.
   Use `codex_steer` with `mode: "queue"` for an active turn, then `codex_queue` and a fresh tail to verify delivery.
   An ambiguous response, socket loss, or accepted steer with a later error requires inspection before any resend.
5. If the adapter selects an existing or known-unsupported queue even with `mode: "steer"`, inspect its returned receipt instead of adding the text again.
   A writer refusal requires the owner to release its lease, not a queue retry.

For an idle thread, start a fresh authorized turn with `codex_send` after confirming that no host holds the writer.
Do not use `codex_send` on active work as a substitute for the steer-first workflow.
Do not interrupt an active turn merely to make delivery easier.
Queues live only in the MCP process, expire after 30 minutes, and do not survive its shutdown.

## Monitor Active Work

Orchestrators use this plugin's packaged `codex-thread-monitor` and `monitors/monitors.json`, rather than ad-hoc watcher scripts.
Before starting a watch, read [Monitor Setup](references/monitor.md) and load its ready-to-use [Monitor input](assets/monitor.json).
Use the packaged npm command with explicit thread selection and status filters.
The wildcard `--thread '*'` covers every loaded-thread page initially and subsequent status broadcasts.
Re-arm an explicit watch after its 30-minute deadline only while the authorized assignment remains active.
Use events to decide when a fresh tail is needed, rather than polling thread status.
Stop the owned watch when the assignment finishes, its lease ends, its target is retired, or monitoring is cancelled.

## Archive, Then Delete

Confirm pending work has ended, receipts are resolved, required source is preserved, and the owner has released the selected thread.
Archive through the native CLI using `codex archive --remote unix:// AUTHORIZED_THREAD_ID`.
Verify the archived state before considering deletion.
Permanent deletion requires explicit human authority for the selected scope and confirmation that the transcript is no longer needed.
Then use `codex delete --remote unix:// AUTHORIZED_THREAD_ID` and verify the native result.
For a custom socket, use `--remote unix://SOCKET_PATH` pointing to the same socket as `CODEX_APP_SERVER_SOCKET`.
Keep exact private IDs and socket paths in transient execution context.
The MCP archive, unarchive, and delete methods are refusal-only tools.
Native lifecycle state, worktree removal, and attachment retirement are separate operations with separate ownership checks.
