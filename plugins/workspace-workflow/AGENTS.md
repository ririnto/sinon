# Repository Guidelines

## Project Structure

`skills/pr-mr-convention/` owns host-neutral integration handoff guidance and host references.
Agents draft or route work.
They do not integrate.

## integration handoff Host Selection

Inspect `git remote -v`, `git status --short --branch`, and `git branch -vv` before host advice.
Select a host from explicit user choice, existing review metadata, policy, upstream, then remote evidence.
If hosted service and hosted service remain plausible, ask for a choice.
Load only the selected host reference and preserve the repository template.

## Commit and integration handoff

Treat force-push and history rewriting as explicit user decisions.

## Security and Configuration

Report stale remote data and template uncertainty. Do not expose credentials or run host authentication checks before host selection.

Open the selected host reference only after selection.
This file stays host-neutral.
State which host evidence selected before drafting integration handoff guidance.
