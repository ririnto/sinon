# Tech Debt Tracker

## Purpose

Tech-debt-tracker.md records the deferred-work backlog with retirement criteria: work the team consciously chose not to do right now, and the specific condition under which each item should be reopened. This is distinct from `docs/exec-plans/active/` (work currently in progress) and `docs/PLANS.md` (project-wide roadmap of intended work).

## Entries

Delete the example rows below when you record your own entries.

| ID | Item | Reason for deferral | Retirement criteria | Owner | Added |
| --- | --- | --- | --- | --- | --- |
| TD-1 | Replace ad-hoc retry helper in domains/notifications with shared utility from packages/util/retry. | Shared util landed after the notifications domain shipped; the local helper is functional but diverges on jitter strategy. | Notifications domain uses packages/util/retry; local helper deleted. | {{owner}} | 2026-04-15 |
| TD-2 | Replace fetch-based webhook client with typed SDK. | Typed SDK was not yet published when the integration shipped. | SDK v1.0 published and pinned in package.json; webhook client uses SDK. | {{owner}} | 2026-04-22 |
| TD-3 | Add structural test for "Providers is the only cross-cutting entry into a domain". | Existing reviewer-driven enforcement caught regressions, but a structural test would be cheaper. | tests/structure/providers-only.test.ts green on CI for three consecutive weeks. | {{owner}} | 2026-05-02 |

## Conventions

- Assign a stable `TD-{{n}}` ID to each entry; IDs increment monotonically and are never reused once an entry is retired.
- Write retirement criteria as a condition, not a date (e.g., "when usage exceeds X" rather than "in Q3").
- Close an entry by deleting the row and linking the PR or completed exec-plan that retired it in the commit message.
- Re-evaluate any entry open longer than 6 months and update its retirement criteria if the condition has changed.
- New entries SHOULD include an `Added` date in `yyyy-MM-dd` format.

## When To Update

- When a piece of work is consciously deferred.
- When retirement criteria for an existing entry change.
- When an entry is retired.
- When an entry has been open long enough to need re-evaluation.

## Required Evidence

- Cite the PR thread, review comment, or design-doc decision that records the original deferral.
- When an entry is retired, link to the PR or completed `docs/exec-plans/completed/` file that retired it.
