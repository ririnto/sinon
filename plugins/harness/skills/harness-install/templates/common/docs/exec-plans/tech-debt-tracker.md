# Tech Debt Tracker

Track deferred work, owner, impact, retirement criteria, and validation evidence in one place so agents and reviewers can see the standing balance.

## Purpose

Make deferred work visible and retirable. Each row MUST name what was deferred, why, who owns it, what condition retires it, and how the retirement is validated.

## When To Update

Update when a piece of work is deferred (not just postponed), when an owner changes, when impact grows or shrinks, or when the retirement condition is met.

## Required Evidence

- Cite the PR, issue, or review note that introduced the deferral.
- Link to the validation step (test, lint, metric) that confirms retirement.

## Entries

| Item | Owner | Impact | Retirement criterion | Validation |
| --- | --- | --- | --- | --- |
