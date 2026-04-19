# Spring Shell interactive flows and terminal UI

Open this reference when the normal command-and-option path in `SKILL.md` is not enough and the task needs guided flows, selection widgets, confirmation steps, or richer terminal UI behavior.

## Guided flow components

Use flow components when a command must lead the operator through several dependent answers and free-form command options would be error-prone.

- String input: use for names, namespaces, and labels.
- Path input: use for file and directory targets that benefit from shell-style completion.
- Confirmation input: use before destructive actions such as delete, revoke, or reset.
- Single-select and multi-select input: use when the choice set is bounded and operator discoverability matters.

Keep the resulting flow output small and explicit so that automation wrappers can still reason about the final result.

## Terminal UI views

Reserve TUI views for cases where operators must watch or edit structured state interactively.

- Good fit: browsing jobs, reviewing queues, confirming staged changes, or stepping through a setup wizard.
- Poor fit: single-shot commands that already work well as normal shell output.

When adding TUI behavior, keep a non-TUI command path available for automation and remote execution.

## Gotchas

- Do not force flow components onto commands that already work as simple options.
- Do not make a TUI-only path the sole way to perform an operational action.
- Do not forget that terminal UI behavior increases test and accessibility cost.
