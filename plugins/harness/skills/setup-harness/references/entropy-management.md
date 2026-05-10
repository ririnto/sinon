# Entropy Management

Open this reference when configuring recurring doc gardening, quality tracking, or cleanup automation for a target repository that already has a harness config.

## Inputs

Read these surfaces first:

- root `CLAUDE.md`
- root `ARCHITECTURE.md`
- `paths.harnessRoot`
- `docs.guardrails`
- `evidence.knownViolations`
- `evidence.readiness`
- `docs.updates`
- `commands.required`

## Gardening cadence

Run lightweight gardening during active development and full gardening before releases.

```text
Daily: link check, active plan freshness, generated-doc provenance
Weekly: quality score update, tech debt review, docs/script alignment
Release: full harness validation plus architecture and runtime evidence checks
```

## Readiness and ratchets

Use readiness to decide whether guardrails report `warn` findings or fail on `error` findings.

| Maturity | Meaning | Gate behavior |
| --- | --- | --- |
| 0 | Discovery only | Report inventory and conflicts |
| 1 | Visible config and docs | Validate shape and required docs |
| 2 | Advisory local checks | Warn locally and record known violations |
| 3 | Shared error gates | Fail new error-level violations in CI or configured hooks |
| 4 | Ratcheted maturity | Reduce known-violation budget and enforce freshness |

The known-violation ledger should list legacy issues that are allowed temporarily. New `error` findings should fail once Stage 3 is active.

## Quality score shape

Use the target repo's configured layers or quality categories. The default domain-layer table is only an example.

```markdown
# Quality Score

| Area | Docs | Tests | Checks | Debt | Overall |
| --- | --- | --- | --- | --- | --- |
| Auth | B | A | A | B | B |
| Billing | C | B | B | C | C |
```

## Technical debt tracker

```markdown
# Technical Debt Tracker

## Active

| ID | Description | Area | Priority | Validation Gap | Added |
| --- | --- | --- | --- | --- | --- |
| TD-001 | Checkout spec lacks hook coverage | Checkout | High | pre-push skips checkout journey | 2026-05-07 |

## Resolved

| ID | Description | Resolution | Resolved |
| --- | --- | --- | --- |
| TD-000 | Missing harness config | Added docs/harness/config.json | 2026-05-07 |
```

## Cleanup units

- One stale doc and its matching script update.
- One broken link class under configured docs paths.
- One quality grade update with evidence.
- One debt item creation or closure.
- One CI, hook, or user requirement rule drift fix.

## Common mistakes

- Gardening docs without updating scripts or checks.
- Tracking debt outside the repository.
- Updating quality grades without citing validation evidence.
- Treating generated docs as authored docs instead of regenerating them from their declared scripts.
