# Monitor Setup

Use the installed plugin executable as the host Monitor command.
Load [the input asset](../assets/monitor.json) and supply it to the native Monitor tool.
Resolve `CLAUDE_PLUGIN_ROOT` to this plugin's installed root if the host does not provide that environment variable.
The plugin root is two directories above this skill directory.
Do not replace the executable with a custom watcher.

```sh
NODE_USE_SYSTEM_CA=1 npm exec --yes --install-links --package="${CLAUDE_PLUGIN_ROOT}" -- codex-thread-monitor --thread '*' --status active --status idle --status notLoaded --status failed --status systemError --timeout-seconds 1800
```

Quote `'*'` so the shell does not expand filenames.
Replace the wildcard with repeatable `--thread AUTHORIZED_THREAD_ID` arguments when only assigned targets should be watched.
The wildcard initially inspects every page of `thread/loaded/list`, reads each thread without resuming it, and then receives native broadcasts.
Loaded threads do not cover stored threads absent from daemon memory.
Observation of a wildcard thread grants no authority to steer or delete it.

Use repeatable `--status` filters to select relevant events.
Include `failed` and `systemError` when waiting for completion so failures cannot be mistaken for quiet success.
The command uses the same `CODEX_APP_SERVER_SOCKET` or `CODEX_HOME` selection as the MCP server.
It starts no daemon, acquires no writer, and refuses server-initiated requests.
Event intake follows stdout backpressure, while a separate read-only socket handles initial and idle inspections without blocking their responses.
Both sockets close when the watch ends.

## Deadline And Ownership

Set the native Monitor `timeout_ms` to 1800000 and the executable's `--timeout-seconds` to 1800 for a 30-minute watch.
Claude Code defaults explicit watches to five minutes and allows at most 30 minutes.
Single-prompt non-interactive runs allow at most ten minutes, so use the smaller host deadline in that mode.
At a deadline, verify the assignment and lease are still active, then re-arm one owned watch with the same selection.
Do not overlap watches or create an automatic endless restart loop.

The automatic plugin registration in `monitors/monitors.json` uses the same executable and lasts until its host stops it.
Configure `CODEX_APP_SERVER_MONITOR_THREADS='*'` and `CODEX_APP_SERVER_MONITOR_STATUSES='active,idle,notLoaded,failed,systemError'` before launching that host if automatic wildcard monitoring is needed.
Without selected threads, automatic monitoring is disabled and exits without connecting.
Reuse the automatic monitor when it already covers the assignment instead of starting a duplicate explicit watch.

Stop the owned explicit watch through the host's native cancellation tool when its assignment completes, lease expires, targets are retired, or monitoring is cancelled.
SIGINT, SIGTERM, and the executable deadline also close its socket and exit successfully.
Socket loss ends the monitor with exit code 1 instead of reconnecting.
Inspect the reported transport failure and re-arm only after the authorized connection is available again.

## Read The Events

Each stdout line is one JSON event.
`threadStatus` includes `threadId`, `status`, and `previous`, with an initial `previous: null` snapshot and later native state transitions.
Unchanged states remain quiet.
`active` means work is running, `idle` means the thread stopped being active, and `notLoaded` means it left daemon memory.
An idle event alone does not prove successful completion or delivery.
Read the tail and receipts before claiming either.

`turnError` has `status: "failed"`, `threadId`, `turnId`, and the native `error` when available.
The initial latest-turn inspection, idle inspection, terminal completion, and non-retrying error notifications can report it.
Distinct failed turns each emit an event, including consecutive failures with no native status change.
The same failed turn is not repeated.
Transient native errors marked `willRetry` are not terminal failures.
For errors such as `model_not_found`, preserve the native details, inspect the affected thread, and report the failed turn before changing the model or retrying under the current assignment.

A native `threadStatus` with `systemError` describes that thread's state.
An `inspection` event with `systemError` describes a failed read and leaves other selected threads running.
A `transport` event with `systemError` and `threadId: null` describes the monitor connection and ends the process.
Server-initiated requests cannot become status or completion events.

Monitor deadlines and cancellation behavior follow the [Claude Code Monitor contract](https://code.claude.com/docs/en/tools-reference#monitor-tool).
The packaged workflow, explicit selection, and re-arm ownership rules are local plugin requirements.
