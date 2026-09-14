---
name: change-description
description: >-
  Compose focused Git-contained change descriptions with rationale, validation evidence, review context, and merge handoff.
  Use when preparing a change summary, review packet, or merge handoff from repository state.
---

# Change Description

Compose one truthful change description from real repository state.
Keep the description self-contained and grounded in the diff, commit history, validation evidence, and explicit task context.

## Default Git Workflow

Run substantive repository changes through this sequence:

1. Start from the task and write a self-contained plan.
2. Implement the planned change.
3. Run the appropriate native checks.
4. Review the proportional diff for correctness, scope, and rule compliance.
5. Integrate the reviewed change with Git according to repository policy.

A simple task may omit or combine an intermediate phase when the result does not need it.
Explicitly required validation, review, approval, and safety conditions remain binding in every case.

## Scope

This skill covers:

- change title and description composition
- rationale, scope, and affected-file summaries
- validation and risk reporting
- review context and merge handoff
- Git-contained change documentation

It does not cover code-review judgment, CI design, merge strategy, history rewriting, or remote-host operations.

## Operating Rules

- Ground every claim in the actual diff, commit history, validation evidence, or explicit user context.
- Keep one cohesive change per review unit.
- Include the problem or requirement, intended result, affected boundaries, and proof.
- Describe review focus and the selected Git merge strategy when known.
- List only checks that actually ran.
- Do not publish, modify remote metadata, or assign responsibility without authorization.
- Use repository-relative paths and portable examples.
- Keep private local environment details and external work-item identifiers out of committed text.

## First Safe Checks

Inspect local state without mutating it:

```sh
git status --short --branch
git branch -vv
git log -5 --oneline
git diff --stat
git diff
```

Resolve the comparison base from the explicit task, repository policy, or the known integration target.
Do not invent a base when the repository state does not identify one.

## Drafting Procedure

1. Read repository instructions and contribution guidance.
2. Resolve the target branch and comparison base independently.
3. Inspect feature-only commits and the merge-base diff.
4. Determine whether the change is one cohesive review unit.
5. Draft the title, rationale, changes, validation, risks, and merge handoff from evidence.
6. Mark unverified items instead of implying that unavailable evidence exists.
7. Return the description to the caller without publishing or changing remote state.

Safe comparison commands after resolving `<base>`:

```sh
git log <base>..HEAD --oneline
git diff <base>...HEAD --stat
git diff <base>...HEAD
```

## Title

Prefer the repository's established convention.
When it uses Conventional Commits, use:

```text
type(scope): imperative description
```

- Use one of `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, or `revert`.
- Add scope only when it clarifies the affected subsystem.
- Keep the description imperative, specific, and without a trailing period.
- Follow repository length policy.
  When none exists, keep the title within 72 characters when practical.

## Change Description Template

Use this template when no repository-specific format applies:

```markdown
## Summary

- <one to three evidence-backed changes>

## Why

- <problem, requirement, or explicit author context>

## Changes

- <grouped implementation facts and affected repository-relative paths>

## Validation

- <checks that actually ran, with result>

## Review Focus

- <behavior, boundary, or risk areas that deserve review>

## Merge Handoff

- <selected Git integration strategy and any required cleanup>

## Risks

- <specific compatibility, rollout, dependency, or performance risk, or None>

## Unverified Items

- <pending check or None>
```

If motivation is not evident from the diff or supplied context, write `Reason not evident from available evidence; confirm with author` instead of inventing one.

## Validation Evidence

Classify every check as:

- passed: command ran and succeeded
- failed: command ran and failed
- pending: not run or result unavailable
- not applicable: reason recorded

Do not convert a planned check into a completed checklist item.
Name exact commands, test cases, or manual actions when known.

## Review And Merge Readiness

Keep the change description incomplete when any of these remain:

- unresolved design decision
- incomplete implementation or dependency
- failed or missing required validation
- unresolved review finding
- unclear target branch or comparison base

A merge handoff requires a cohesive diff, appropriate passing validation, resolved blockers, and explicit integration authorization.

## Self-Review

Before returning the description, confirm:

- title describes one change
- target and comparison branches are not conflated
- summary and changes match the diff
- motivation is evidenced or marked unconfirmed
- validation is honest
- review focus, risks, and unverified items are explicit
- merge handoff matches repository policy
- no remote operation was taken

## Edge Cases

- Detached HEAD or missing upstream: rely on explicit target or repository policy.
  Do not invent a base.
- Large or multi-purpose diff: recommend split boundaries before drafting one description.
- Binary or generated changes: name their source and verification when available.
- Stale remote-tracking data: disclose that branch comparison may be incomplete.

## Output Contract

Return:

1. comparison base and target branch
2. title
3. complete change description
4. review focus and merge handoff
5. validation, risks, and unverified items
6. any integration blocker or authorized next command
