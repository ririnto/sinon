# Repository Knowledge Structure

Open this reference when designing the `docs/` hierarchy, setting up indexing, or refining progressive disclosure mechanics beyond the common path in `SKILL.md`.

## Progressive disclosure model

The repository uses three levels of disclosure:

1. `CLAUDE.md` -- the table of contents injected into agent context at activation. Roughly 100--150 lines. Contains pointers only. `AGENTS.md` is a symlink to `CLAUDE.md`.
2. Top-level `docs/*.md` files -- domain-level maps (architecture, design, frontend, reliability, security). Each is a navigable entrypoint for one domain.
3. `docs/{design-docs,exec-plans,product-specs,references}/` -- the full knowledge base. Opened on demand for specific tasks.

Agents start with the short entrypoint and are taught where to look next, rather than being overwhelmed up front.

## Directory hierarchy

See SKILL.md Step 2 for the canonical directory layout. This reference covers the contracts and indexing rules that apply within that layout.

## Index file contract

Every subdirectory with more than three documents MUST contain an `index.md` that lists all entries with:

- Title
- One-line summary
- Verification status (`verified`, `draft`, `stale`)
- Last-reviewed date

```markdown
# Design Docs

| Title | Summary | Status | Last Reviewed |
| --- | --- | --- | --- |
| Core Beliefs | Agent-first operating principles | verified | 2026-04-15 |
| Auth Model | Token-based auth boundary design | draft | 2026-04-10 |
```

## Design document contract

Each design document MUST contain:

1. Problem statement -- what decision is being made and why.
2. Context -- constraints, dependencies, and alternatives considered.
3. Decision -- the chosen approach with rationale.
4. Consequences -- tradeoffs and follow-up actions.

Design documents SHOULD be catalogued in `design-docs/index.md` with verification status.

## Execution plan contract

Execution plans are first-class versioned artifacts. Copy `assets/execution-plan-template.md` for the full template.

Key invariants:

- Ephemeral lightweight plans are used for small changes; complex work uses full execution plans.
- Active plans live in `exec-plans/active/`; completed plans move to `exec-plans/completed/`.
- Each plan includes progress tracking and a decision log.
- Plans are checked into the repository so agents can operate without external context.

## Mechanical enforcement

Dedicated linters and CI jobs validate the knowledge base:

- Cross-link checker: every pointer in `CLAUDE.md` resolves to an existing file.
- Freshness checker: index files with entries older than 30 days are flagged.
- Structure checker: required top-level `docs/` files exist and are non-empty.
- A doc-gardening agent scans for stale or obsolete documentation and opens fix-up pull requests.

## Common mistakes

- Writing a monolithic `CLAUDE.md` that tries to contain all knowledge. This crowds out task context and rots faster than it can be maintained.
- Storing knowledge in external systems (Slack, Google Docs, wikis) without mirroring it into the repository. If it is not in the repo, it does not exist for the agent.
- Skipping index files. Without indexes, agents cannot discover relevant documents efficiently.
- Allowing generated and authored documentation to mix without clear separation. Generated artifacts belong in `generated/`.
