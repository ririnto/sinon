# Recovery

## Treat Interruption as Unknown

A stream, API, transport, or notification interruption is neither failure nor completion.

Inspect lifecycle metadata, worker activity, run identity, owned workspace, branch or change set, running processes, terminal-result presence, and current artifacts.

Do not start a duplicate worker while existing activity may still continue.

Do not delete or recreate a workspace merely because it has no commit, because uncommitted files or generated state may be the only recoverable work.

## Validate Identity

Correlate run identity, worker identity, declared ordering, terminal status, ownership release, and current artifacts before trusting a cached or repeated result.

Keep `UNKNOWN` when evidence cannot distinguish current completion from stale state.

## Resume, Curate, or Replace

Resume the same node when its context and ownership remain usable, and send only updated steering and remaining acceptance criteria.

Use a curator when raw transcript or journal inspection is necessary, and return only a compact compliant result to the Main Agent.

Create a replacement node when the prior context is unavailable, unsafe, corrupted, irreconcilably off track, or detached from resource ownership.

Build replacement work from the current source of truth and optional curated prior steering.

Do not force commits or broaden side-effect authority during recovery.

Ordinary source-deletion authority does not authorize deleting workspaces, history, or recovery state.

## Continuation Signals

A message such as `Goal not yet met... continuing` is control-plane output and does not create a new objective or prove completion.

Report verified completion, verified failure, a verified blocker, or the specific missing evidence.
