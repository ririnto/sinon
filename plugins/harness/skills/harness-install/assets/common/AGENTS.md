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
- Treat the user-facing root session as the sole orchestrator.
  - Repository agents are leaves and do not delegate or publish.
- Classify work before assignment.
  - Use `scoped-implementer` only for an exhaustive single-file or small related-file set.
  - Use `implementation` for affected-set discovery, cross-file or cross-layer work, design choices, and integrated validation.
- Give each writer one disjoint scope and one owning worktree.
- Wait for complete fan-in; failed or missing workers block completion.
- Return review fixes to the owning writer, re-review, then validate the integrated tree.
- Use `ARCHITECTURE.md` and relevant `docs/**` files for task-specific context.
- Use `.claude/agents/**` and `.claude/skills/**` only for the scoped task they describe.
- Run the validation command selected for the task before completion, or report the blocker.
- Report changed files, validation evidence, skipped checks, and remaining risks.
- Keep Codex subagent depth at the default `max_depth = 1`; nested delegation is not part of this workflow.

## Documentation

- Keep Markdown headings in English.
- Use fenced code blocks with a language.
- Use ASCII tree markers such as `+--` and `|`; do not use Unicode box drawing characters.
- Add durable documentation under an approved `docs/` subdirectory.
- Use `docs/templates/` when creating new agents, skills, references, or execution plans.
- Generated outputs under `docs/generated/` must record source command, input files, freshness, and regeneration trigger.
- Execution plans use `docs/exec-plans/active/yyyy-MM-dd-<slug>.md` while active and move to `docs/exec-plans/completed/` without renaming when complete.
- Completed plans must contain checked task lines or no task list.
