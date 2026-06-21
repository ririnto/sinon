# Repository Instructions

Keep this file short, practical, and focused on behavior that applies to every task.
Child `AGENTS.md` files may add narrower rules for their directories.

## Working Rules

- Keep changes scoped to {{scope_rule}}.
- Prefer {{local_pattern_source}} over new abstractions.
- Surface unclear requirements before editing.
- Preserve user changes present in the worktree.
- Do not edit {{protected_paths_or_assets}} unless the task names them.
- Update {{related_context_surfaces}} when a change makes them inaccurate.

## Workflow

- Use `WORKFLOW.md` for branch, worktree, validation, review, and publication decisions.
- Use `ARCHITECTURE.md` and relevant `docs/**` files for task-specific context.
- Run {{validation_command}} before completion, or report the blocker.
- Report changed files, validation evidence, skipped checks, and remaining risks.

## Documentation

- Keep Markdown headings in English.
- Use fenced code blocks with a language.
- Use ASCII tree markers such as `+--` and `|`; do not use Unicode box drawing characters.
- Add durable documentation under {{approved_docs_directory}}.
- Generated outputs under `docs/generated/` must record source command, input files, freshness, and regeneration trigger.
