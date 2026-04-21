# Entropy Management

Open this reference when setting up doc-gardening, quality grading, or cleanup automation beyond the common-path guidance in `SKILL.md`.

## Why entropy management matters

See SKILL.md Step 5 for the core entropy management workflow. This reference provides the detailed cadence, grading rubrics, and automation patterns. Agents replicate existing patterns including suboptimal ones; without continuous correction, drift compounds like a high-interest loan.

## Doc-gardening

Schedule a recurring agent task that scans the repository for documentation drift.

### Cadence

Run doc-gardening at least once per day in active development periods. The gardening agent performs these checks:

1. Scan `docs/design-docs/index.md` for entries marked `stale` or older than 30 days since last review.
2. Compare documented interfaces against actual code exports. Flag mismatches.
3. Verify cross-links in `CLAUDE.md` and index files resolve to existing files.
4. Check `docs/exec-plans/active/` for plans with no progress updates in the past 7 days.
5. Open targeted fix-up pull requests for each issue found.

### Gardening agent prompt sketch

```text
Scan the docs/ directory for stale or obsolete documentation:

1. Check every index.md entry where status is not "verified".
2. For each design doc, verify that referenced code paths still exist.
3. For each execution plan in active/, check for a progress update within
   the last 7 days.
4. For each generated artifact, verify the generation script is still
   present and passing.
5. Open one pull request per issue with a clear description of what drifted
   and the recommended fix.
```

### Fix-up PR contract

Each fix-up pull request MUST:

- Contain a single focused change (one doc or one set of related docs).
- Include a description explaining what drifted and why.
- Be reviewable in under one minute.
- Be eligible for auto-merge if no human objects within a set window.

## Quality grading

Maintain per-domain and per-layer quality grades in `docs/QUALITY_SCORE.md`.

### Grading scale

| Grade | Meaning |
| --- | --- |
| A | Fully documented, tested, and lint-clean. No known debt. |
| B | Documented and tested. Minor debt items tracked. |
| C | Partially documented or tested. Debt items present. |
| D | Undocumented or undertested. Significant debt. |
| F | No documentation or tests. Critical debt. |

### Grade tracking

```markdown
# Quality Score

| Domain | Types | Config | Repo | Service | Runtime | UI | Overall |
| --- | --- | --- | --- | --- | --- | --- | --- |
| App Settings | A | A | B | B | A | B | B |
| Auth | A | A | A | B | B | C | B |
| Connectors | B | B | C | C | D | D | C |
```

The grading agent updates this table on each run. Domains at C or below are prioritized for cleanup.

### Grading criteria

- **Types**: schema completeness, validation coverage, naming conventions.
- **Config**: environment variable documentation, default values, type safety.
- **Repo**: query coverage, migration status, index documentation.
- **Service**: error handling, boundary validation, structured logging.
- **Runtime**: observability hooks, health checks, graceful shutdown.
- **UI**: accessibility, design system compliance, test coverage.

## Golden principles enforcement

On each gardening run, scan for violations of the golden principles:

1. **Shared utilities over hand-rolled helpers** -- flag duplicate logic that should be centralized.
2. **Parse at boundaries** -- flag raw data access without schema validation.
3. **Structured logging** -- flag string-formatted log calls.
4. **Naming conventions** -- flag types missing the `*Schema` or `*Type` suffix where required.
5. **File size limits** -- flag files exceeding the configured maximum.
6. **Internalizable dependencies** -- flag opaque third-party packages that could be replaced with a simple internal implementation.

When a violation is found, open a targeted refactoring pull request rather than a broad cleanup. Keep changes small and reviewable.

## Technical debt tracker

Maintain `docs/exec-plans/tech-debt-tracker.md` as a living document.

```markdown
# Technical Debt Tracker

## Active

| ID | Description | Domain | Priority | Owner | Added |
| --- | --- | --- | --- | --- | --- |
| TD-001 | Missing validation on connector input | Connectors | High | agent | 2026-04-15 |
| TD-002 | Log formatting inconsistent in Runtime | Runtime | Medium | agent | 2026-04-18 |

## Resolved

| ID | Description | Resolution | Resolved |
| --- | --- | --- | --- |
| TD-003 | Duplicate string helpers in Service | Extracted to shared util | 2026-04-20 |
```

Debt items are reviewed on each gardening run. Items at High priority are scheduled for the next cleanup cycle. Items at Medium or Low are addressed as capacity allows.

## Cleanup automation pattern

```text
Schedule:
  - Daily: doc-gardening scan + quality grade update
  - Weekly: golden principles scan + tech debt review
  - Per-release: full architecture compliance check

Each run:
  1. Scan for violations.
  2. Open targeted fix-up PRs.
  3. Update quality grades.
  4. Update tech debt tracker.
  5. Merge auto-eligible PRs after review window.
```

## Common mistakes

- Spending a fixed day (e.g., "Fix-it Friday") on cleanup instead of continuous gardening. Dedicated cleanup days do not scale.
- Opening large refactoring PRs instead of small targeted fixes. Keep each PR reviewable in under one minute.
- Tracking debt in an external system. The tracker MUST live in the repository so agents can access it.
- Updating quality grades manually. Automate the grading so it stays current.
- Ignoring C-grade domains. A domain at C degrades quickly; prioritize it before it drops to D.
