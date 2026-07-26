# Contributing

## Discover

Read `README.md` and `ARCHITECTURE.md` before changing the repository.
Find the canonical setup, development, and validation commands instead of inventing replacements.
Define the requested outcome, affected paths, assumptions, and evidence needed for handoff.

## Change

Keep each change bounded to the approved scope.
Preserve unrelated work and existing ownership boundaries.
Update architecture or durable documentation when the change alters a documented boundary, workflow, or invariant.
Place new durable documents under `docs/` and use the matching template when one exists.

## Validate

Run the repository's canonical checks at the smallest useful scope, then run the complete check before handoff when the repository provides one.
Review executable behavior at its user-facing boundary and inspect the exact diff for regressions, unsafe effects, and missing documentation.
Run `git diff --check` when Git is available.
Report checks that were skipped and explain why.

## Review And Handoff

Summarize the outcome, changed paths, validation evidence, assumptions, and residual risks.
Resolve review findings and repeat affected checks after every substantive change.
The person or agent with repository authority decides whether the evidence supports handoff.

## Commit And Remote Effects

Stage only files within the approved scope and use the repository's commit conventions.
Create a commit or pull request only when the user or repository process requires it.
Get explicit approval before pushing, publishing, mutating remote state, accessing credentials, activating hooks, or taking destructive action.
When approval is absent, leave local evidence and describe the required handoff instead of performing the remote effect.

## Markdown

The installed `.markdownlint-cli2.jsonc` disables `line-length`.
Use semantic line breaks with one complete sentence per physical line.
Do not column-wrap prose.
Tables, lists, fenced code, URLs, and machine-consumed literals may follow their required structure.
