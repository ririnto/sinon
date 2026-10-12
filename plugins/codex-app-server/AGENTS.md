# Catalog Maintenance

Keep protocol schemas generated from the installed Codex CLI rather than committing a snapshot.
Keep generated tools opt-in so newly added protocol methods do not expand the default tool list.
Unknown methods must retain mutating annotations until their behavior is reviewed.
Keep active-turn inspection and expected turn IDs in the curated steering tools.

Maintain shared method policy in `lib/policy.mjs`.
Never expose refused methods through generated tools or raw calls.
Keep thread archive, unarchive, and delete on the native CLI.
Server-initiated approvals remain refused by `lib/connection.mjs`.

Extend the existing disposable-socket tests for schema conversion, filtering, fallback, and guarded behavior.
Do not test mutations against the live daemon.

Keep packaged monitors on the native Claude Code Monitor contract.
Use read-only native status notifications and event-driven reads, without resuming watched threads.
Keep transport failures distinct from thread systemError and terminal failed outcomes.
Never interpret refused server requests as status notifications.
