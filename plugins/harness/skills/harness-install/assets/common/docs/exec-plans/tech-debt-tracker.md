# Tech Debt Tracker

## Purpose

Tech-debt-tracker.md records the deferred-work backlog with retirement criteria: work the team consciously chose not to do right now, and the specific condition under which each item should be reopened. This is distinct from `docs/exec-plans/active/` (work currently in progress) and `docs/PLANS.md` (project-wide roadmap of intended work).

## Entries

Replace the example row below with real deferred items.

| ID | Item | Reason for deferral | Retirement criteria | Owner | Added |
| --- | --- | --- | --- | --- | --- |
| TD-1 | {{deferred-item}} | {{why-not-now}} | {{condition-to-revisit}} | {{owner}} | {{yyyy-MM-dd}} |

## Conventions

- Assign a stable `TD-{{n}}` ID to each entry.
- Write retirement criteria as a condition, not a date (e.g., "when usage exceeds X" rather than "in Q3").
- Close an entry by deleting the row and linking the PR or completed exec-plan that retired it.
- If an entry remains open longer than one year, re-evaluate and update its retirement criteria.

## When To Update

- When a piece of work is consciously deferred.
- When retirement criteria for an existing entry change.
- When an entry is retired.
- When an entry has been open long enough to need re-evaluation.

## Required Evidence

- Cite the PR thread, review comment, or design-doc decision that records the original deferral.
- When an entry is retired, link to the PR or completed `docs/exec-plans/completed/` file that retired it.
