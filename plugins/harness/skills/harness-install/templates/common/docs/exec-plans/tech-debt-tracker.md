# Tech Debt Tracker

Track consciously deferred work in one place so the standing balance of *postponed* work is visible. Each row MUST name what was deferred, why, who owns it, what condition retires it, and how that retirement is validated.

## Purpose

Make deferred work visible and retirable. This is distinct from `docs/PLANS.md`, which tracks work that is *in flight*; this file tracks work that was *intentionally postponed* and the conditions that close it out.

## When To Update

Update when a piece of work is consciously deferred (not merely paused), when an owner changes, when impact grows or shrinks, or when the retirement condition is met.

## Required Evidence

- Cite the PR, issue, or review note that introduced the deferral.
- Link to the validation step (test, lint, metric) that confirms retirement.

## Entries

| Item | Owner | Impact | Retirement criterion | Validation |
| --- | --- | --- | --- | --- |
