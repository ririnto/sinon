# Tech Debt Tracker

## Purpose

Tech-debt-tracker.md records Sinon repository debt with retirement criteria: work the maintainers consciously chose not to do right now, and the specific condition under which each item should be reopened. This file is repository-owned and separate from the harness-installed tracker template under `plugins/harness/skills/harness-install/assets/common/docs/exec-plans/tech-debt-tracker.md`.

## Entries

### Active

| Type | Plan | Item | Reason for deferral | Retirement criteria | Author | Assignees | Created | Updated |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Refactor | [2026-05-23-ast-based-style-checks.md](active/2026-05-23-ast-based-style-checks.md) | Unify the rule-owned validation surface across all stack validators with DI-friendly composition. | The current dirty worktree already spans many validator files, so a full cross-stack surface rewrite should land as a deliberate canary slice rather than another broad opportunistic refactor. | Every supported stack exposes rule-owned category metadata and a single Rule-level extension surface; `plugin-self-check.sh` and native stack validators pass. | Sisyphus | ririnto | 2026-05-24 | 2026-05-24 |
| Fix | [2026-05-23-ast-based-style-checks.md](active/2026-05-23-ast-based-style-checks.md) | Rename misspelled Keepfile rule types to `RequireKeepfileInEmptyDirectoriesRule`. | The category string is already correct, and the naming cleanup is lower priority than preserving the current passing harness state. | Gradle and uv class/object names and references use `RequireKeepfileInEmptyDirectoriesRule`; `plugin-self-check.sh` passes. | Sisyphus | ririnto | 2026-05-24 | 2026-05-24 |
| Test | [2026-05-23-ast-based-style-checks.md](active/2026-05-23-ast-based-style-checks.md) | Run the installed native validator matrix for every stack mode. | Local package self-check is green, but full native validation requires installed target repositories and stack tooling availability. | Fresh or controlled installs for Gradle, Maven, uv, Bun, and shell run their documented validation commands successfully, with results recorded in the active execution plan. | Sisyphus | ririnto | 2026-05-24 | 2026-05-24 |

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
- Write retirement criteria as a condition, not a date.
- Close an entry by moving it from `Active` to `Completed`, then replace `Updated` with `Completed`.
- Re-evaluate any entry open longer than 6 months and update its retirement criteria if the condition has changed.
- Record `Author` and `Assignees` in the same spirit as a GitHub Issue: the author captures who opened the debt item, and assignees capture who owns follow-up.
- Record dates in `yyyy-MM-dd` format: active entries use `Created` and `Updated`, while completed entries use `Created` and `Completed`.

## When To Update

- When Sinon maintainers consciously defer repository work.
- When retirement criteria for an existing entry change.
- When an entry moves between `Active` and `Completed`.
- When an entry has been open long enough to need re-evaluation.

## Required Evidence

- Cite the PR thread, review comment, or design-doc decision that records the original deferral.
- When an entry is retired, link to the PR or completed `docs/exec-plans/completed/` file that retired it.
