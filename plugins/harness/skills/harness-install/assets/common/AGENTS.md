# Repository Instructions

Keep it short, practical, and focused on behavior that applies to every task.
Child `AGENTS.md` files may add narrower rules for their directories.

## Working Rules

- Keep changes scoped to the user request and the files that request requires.
- Prefer the repository's existing patterns over new abstractions.
- Surface unclear requirements before editing.
- Preserve user changes present in the worktree.
- Do not edit secrets, credentials, local environment files, dependency caches, or vendored code unless the task names them.
- Update documentation, templates, agents, skills, validation helpers, or generated-artifact metadata when a change makes them inaccurate.
- Leave ignored byproducts alone unless they are tracked, break validation, or hide a source-tree problem.

## Workflow

- Use `WORKFLOW.md` for branch, worktree, validation, review, and publication decisions.
- Use `ARCHITECTURE.md` and relevant `docs/**` files for task-specific context.
- Use `.claude/agents/**` and `.claude/skills/**` only for the scoped task they describe.
- Run the validation command selected for the task before completion, or report the blocker.
- Report changed files, validation evidence, skipped checks, and remaining risks.

## Documentation

- Keep Markdown headings in English.
- Use fenced code blocks with a language.
- Use ASCII tree markers such as `+--` and `|`; do not use Unicode box drawing characters.
- Add durable documentation under an approved `docs/` subdirectory.
- Use `docs/templates/` when creating new agents, skills, references, or execution plans.
- Generated outputs under `docs/generated/` must record source command, input files, freshness, and regeneration trigger.
- Execution plans use `docs/exec-plans/active/yyyy-MM-dd-<slug>.md` while active and move to `docs/exec-plans/completed/` without renaming when complete.
- Completed plans must contain checked task lines or no task list.
