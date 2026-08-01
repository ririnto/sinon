# Quality Score

## Purpose

`QUALITY_SCORE.md` grades each product domain and architectural layer on an ordinal scale.
Use it to steer doc-gardening, refactoring, and tech-debt prioritization from current quality gaps.

## Grading Scale

- A: meets every invariant.
  - Documentation, tests, and runtime contracts are complete and aligned.
- B: minor gaps under a remediation plan.
  - Scheduled closure is tracked in the project tracker.
- C: meaningful gaps are tracked in the project tracker.
  - They affect non-critical work but block nothing critical.
- D: actively misaligned and blocks new work.
  - Requires immediate remediation before feature expansion.

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
| Service | A | none | none | project-owner |
| Runtime | B | no chaos test suite | one chaos test green for each failure mode in RELIABILITY.md | project-owner |
| UI | B | automated a11y coverage gaps | automated a11y checks green on top routes | project-owner |

## Gap Tracking

Each gap MUST link to durable evidence with retirement criteria.
Use a project tracker item, review record, validator report, or durable design document.

- Example: `<tracker-id-or-review-url>`

## When To Update

- After each meaningful refactor that changes quality criteria or coverage.
- When a new validation surface is added that changes grading requirements.
- On a recurring doc-gardening cadence (for example quarterly).
- When a gap is closed or a new gap is discovered.

## Required Evidence

- Link to validator output, test report, or review comment that supports each score change.
- Link to the matching tracker item, review record, validator report, or durable design document for any tracked gap.
- Timestamp and author on each update so the gardening process can track velocity.
