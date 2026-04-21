# CLAUDE.md Template

Copy this template when creating `CLAUDE.md` from scratch. Adjust section titles and file paths to match the repository. Keep the total under 150 lines. After creating `CLAUDE.md`, create `AGENTS.md` as a symlink pointing to it.

```markdown
# CLAUDE.md

This file is the table of contents for agent context. It points to deeper sources of truth in `docs/`. Do not add inline guidance here; update the referenced files instead.

## Architecture

See `docs/ARCHITECTURE.md` for the top-level domain map and package layering.

## Design

See `docs/DESIGN.md` for design system patterns and conventions.

## Frontend

See `docs/FRONTEND.md` for frontend architecture.

## Product

See `docs/product-specs/index.md` for feature specifications and user stories.

## Active work

See `docs/exec-plans/active/` for in-progress execution plans.

## Completed work

See `docs/exec-plans/completed/` for merged and retired execution plans.

## Design decisions

See `docs/design-docs/index.md` for the design decision catalog with verification status.

## Core beliefs

See `docs/design-docs/core-beliefs.md` for agent-first operating principles.

## Quality

See `docs/QUALITY_SCORE.md` for per-domain and per-layer quality grades.

## Technical debt

See `docs/exec-plans/tech-debt-tracker.md` for known debt items.

## Reliability

See `docs/RELIABILITY.md` for SLOs and reliability requirements.

## Security

See `docs/SECURITY.md` for the security model and threat boundaries.

## References

See `docs/references/` for third-party documentation repackaged for agent context.

## Generated artifacts

See `docs/generated/` for auto-generated documentation (schemas, API docs).
```

## Symlink setup

```sh
ln -s CLAUDE.md AGENTS.md
```

`AGENTS.md` is a symlink that resolves to `CLAUDE.md`. Both names provide the same content to their respective agent runtimes. Do not create separate files.
