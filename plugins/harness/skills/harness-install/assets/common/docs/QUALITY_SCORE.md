# Quality Score

## Purpose

QUALITY_SCORE.md grades each product domain and architectural layer on an ordinal scale so that doc-gardening, refactoring, and tech-debt prioritization can be steered by current quality gaps rather than gut feel.

## Grading Scale

- A: meets every invariant; documentation, tests, and runtime contracts are complete and aligned.
- B: minor gaps under a remediation plan; scheduled closure tracked in tech-debt-tracker.
- C: meaningful gaps tracked in tech-debt-tracker; affects non-critical work but blocks nothing critical.
- D: actively misaligned, blocks new work; requires immediate remediation before feature expansion.

Target teams may adjust the labels and number of levels but MUST keep them ordinal and small.

## Domain Scores

Replace the example rows below with entries for each product domain in this repository.

| Domain | Score | Gaps | Retirement criteria | Owner |
| --- | --- | --- | --- | --- |
| Identity | B | RBAC unit coverage < 80% | coverage ≥ 90% on `domains/identity/` | project-owner |
| Catalog | B | public API schema not parsed at boundary | parse-don't-validate on every public endpoint | project-owner |
| Notifications | C | no retry policy documented | runbook + dead-letter policy committed | project-owner |

## Layer Scores

Replace the example rows below with entries for each architectural layer.

| Layer | Score | Gaps | Retirement criteria | Owner |
| --- | --- | --- | --- | --- |
| Service | A | — | — | project-owner |
| Runtime | B | no chaos test suite | one chaos test green for each failure mode in RELIABILITY.md | project-owner |
| UI | B | 70% axe-core coverage | 100% axe-core green on top-10 routes | project-owner |

## Gap Tracking

Each gap MUST be either (a) linked to an entry in `docs/exec-plans/tech-debt-tracker.md` with retirement criteria, or (b) linked to an active execution plan that will close it.

- Example: [docs/exec-plans/tech-debt-tracker.md#td-1](docs/exec-plans/tech-debt-tracker.md#td-1)

## When To Update

- After each meaningful refactor that changes quality criteria or coverage.
- When a new validation surface is added that changes grading requirements.
- On a recurring doc-gardening cadence (for example quarterly).
- When a gap is closed or a new gap is discovered.

## Required Evidence

- Link to validator output, test report, or review comment that supports each score change.
- Link to the matching entry in `docs/exec-plans/tech-debt-tracker.md` for any tracked gap.
- Timestamp and author on each update so the gardening process can track velocity.
