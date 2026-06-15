# Tech Debt Tracker

## Purpose

`Tech-debt-tracker.md` records the deferred-work backlog with retirement criteria: work the team consciously chose not to do right now, and the specific condition under which each item should be reopened.
This is distinct from `docs/exec-plans/active/` (work currently in progress) and `docs/PLANS.md` (project-wide roadmap of intended work).

## Entries

### Active

| Type | Plan | Item | Reason for deferral | Retirement criteria | Author | Assignees | Created | Updated |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

### Completed

| Type | Plan | Item | Reason for deferral | Retirement criteria | Author | Assignees | Created | Completed |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

## Types

- Feature
- Fix
- Refactor
- Test
- Docs
- Security
- Performance
- Reliability
- Maintenance
- Chore

## Conventions

- Use `Plan` to link the active or completed execution plan that records the deferral decision.
- Put `Type` first in each entry row.
- Write retirement criteria as a condition, not a date (e.g., "when usage exceeds X" rather than "in Q3").
- Close an entry by moving it from `Active` to `Completed`, then replace `Updated` with `Completed`.
- Re-evaluate any entry open longer than 6 months and update its retirement criteria if the condition has changed.
- Record `Author` and `Assignees` in the same spirit as a GitHub Issue: the author captures who opened the debt item, and assignees capture who owns follow-up.
- Record dates in `yyyy-MM-dd` format: active entries use `Created` and `Updated`, while completed entries use `Created` and `Completed`.

## When To Update

- When a piece of work is consciously deferred.
- When retirement criteria for an existing entry change.
- When an entry moves between `Active` and `Completed`.
- When an entry has been open long enough to need re-evaluation.

## Required Evidence

- Cite the PR thread, review comment, or design-doc decision that records the original deferral.
- When an entry is retired, link to the PR or completed `docs/exec-plans/completed/` file that retired it.
