# Catalog Maintenance

Keep protocol schemas generated from the installed Codex CLI rather than committing a snapshot.
Share concurrent generation and successful schema caches between discovery and native validation.
Validate native ClientRequest parameters before connecting and preserve refusal-first behavior.
Keep all other protocol directions discovery-only.
Refresh tools on discovery when CLI versions or schema hashes change, and notify the host with tools/list_changed.
Keep reviewed curated adapter enums and prefer model/list for generic model discovery.
Expose every ClientRequest as a native-schema tool, retaining refusal-only tools for blocked methods.
Preserve curated adapters and their guards alongside native methods.
Use native host tool search without custom tool caps, paging, or generic dispatch.
Unknown methods must retain mutating annotations until their behavior is reviewed.
Keep active-turn inspection and expected turn IDs in the curated steering tools.

Maintain shared method policy in `lib/policy.mjs`.
Refused methods may have discoverable tools, but their callbacks must refuse before validation or connecting.
Keep thread archive, unarchive, and delete on the native CLI.
Server-initiated approvals remain refused by `lib/connection.mjs`.

Extend the existing disposable-socket tests for schema conversion, filtering, fallback, and guarded behavior.
Do not test mutations against the live daemon.

Keep active-thread sends on the process-local queue unless supported steering is explicitly selected.
Recheck idle state before queued delivery and preserve native writer refusals without retries.
Keep delivery receipts bounded and close queue connections on cancellation, timeout, or transport closure.
Never interpret refused server requests as completion notifications.

Keep packaged monitors on the native Claude Code Monitor contract.
Use read-only native status notifications and event-driven reads, without resuming watched threads.
Keep transport failures distinct from thread systemError and terminal failed outcomes.
Never interpret refused server requests as status notifications.
