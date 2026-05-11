# Claude Code Entry Point

Claude Code MUST use `AGENTS.md` as the primary Repository Harness Contract.

Before making changes, read:

1. `AGENTS.md`
2. `ARCHITECTURE.md`
3. The relevant file under `docs/**` for the task domain

Validation MUST use the stack-specific command documented in `.claude/harness/README.md`.

`docs/generated/` is for generated repository artifacts. It MAY be empty and retained by `.gitkeep` until generated outputs exist. `docs/generated/db-schema.md` is only an example of a possible generated artifact and MUST NOT be treated as a required file.

Harness changes MAY be made during development when the current harness no longer matches project reality. Such changes MUST be committed as versioned files and validated before merge.
