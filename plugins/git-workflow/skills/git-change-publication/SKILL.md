---
name: git-change-publication
description: >-
  Use this skill when the user asks to decide whether changes are ready for one commit, draft a Conventional Commit message from the real diff, prepare hosted review text, or preserve GitHub/GitLab repository templates while publishing a change.
---

# Git Change Publication

## Overview

Use this skill to turn real repository state into a publishable change narrative. The common case is inspecting the working tree, deciding whether the changes are cohesive enough for one commit, drafting a Conventional Commit message from the actual diff, preparing hosted review text, and then branching into GitHub or GitLab-specific template depth only when the repository host makes that necessary.

## Use This Skill When

- You need to decide whether a change set is ready for one commit or should be split first.
- You need to draft a Conventional Commit message from staged changes.
- You need to choose a commit type or optional scope that matches the actual change intent.
- You need to prepare hosted review body text that preserves an existing repository template or falls back safely when no template exists.
- You need to branch into GitHub or GitLab-specific template handling without duplicating the shared commit workflow.
- Do not use this skill when the main issue is rewriting Git history, resolving merge conflicts, or teaching low-level Git fundamentals for beginners.

## Common-Case Workflow

1. Inspect `git status`, staged changes, unstaged changes, and recent commit history before proposing commit or review text.
2. Decide whether the change set is one logical unit or whether mixed-purpose changes should be split.
3. Map the actual intent to a Conventional Commit type and optional scope.
4. Draft an imperative subject line and a short body that explains why the change exists.
5. Detect whether the repository host is GitHub or GitLab when exact template mechanics matter.
6. Preserve an existing repository template when present, or use a stable fallback review body when none exists.
7. Validate that the final commit and hosted-review text match the actual diff rather than aspirations or future work.

## Minimal Setup

Inspect the repository state before choosing wording:

```bash
git status
git diff --cached
git diff
git log -5 --oneline
```

Use when: you need to anchor commit or hosted-review text to the real repository state before writing anything.

## Commit Drafting Core

Single logical change ready for one commit:

```bash
git status
git diff --cached
git commit -m "fix(auth): reject expired refresh tokens" -m "- Prevent expired refresh tokens from creating new sessions.
- Keep token rejection behavior aligned with the API contract."
```

Default Conventional Commit shape:

```text
<type>[optional scope]: <imperative summary>

- Explain the user-visible or maintenance reason for the change.
- Note the main constraint, compatibility point, or safety boundary.
```

Use `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `build`, `ci`, or `chore`, and prefer `feat` or `fix` whenever published behavior changes. If the diff mixes unrelated feature work, broad cleanup, or separate test concerns, split the commit before drafting final text.

Breaking change variant:

```text
feat(api)!: remove legacy session refresh endpoint

- Replace the legacy refresh endpoint with the token-rotation flow.
- Keep server behavior aligned with the new authentication contract.

BREAKING CHANGE: Clients must switch from `/v1/session/refresh` to `/v2/tokens/refresh`.
```

## Ready-to-Adapt Templates

Hosted review body that preserves an existing repository template:

```text
## Summary
- <fill with the actual change>

## Why
- <fill with the actual motivation>

## Validation
- <fill with tests, checks, or manual verification actually performed>
```

Fallback hosted review body when no repository template exists:

```text
## Summary
- <1-3 bullets describing the real diff>

## Why
- <reason the change exists>

## Validation
- <tests, lint, typecheck, or manual checks that actually ran>

## Risks
- <follow-up risks, rollout notes, or `None`>
```

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| exact GitHub pull request template paths, named template handling, or GitHub-specific preservation rules | `./references/github-pull-request-templates.md` |
| exact GitLab merge request template paths, `Default.md`, variables, or quick-action preservation rules | `./references/gitlab-merge-request-templates.md` |

## Validate the Result

Validate the common case with these checks:

- `git status` and the cited diff agree with the proposed commit message.
- The change set is still one logical unit.
- The subject stays imperative, concise, and typed correctly.
- The commit body explains why rather than restating file names.
- Hosted review body text preserves the existing repository template when present or uses a stable fallback when no template exists.
- Validation text reports checks that actually ran, not planned checks.

## Invariants

- MUST inspect real repository state before drafting commit or hosted-review text.
- MUST avoid grouping unrelated changes into one commit.
- MUST use a valid Conventional Commit type.
- MUST keep the subject line imperative and concise.
- MUST align the chosen type and scope with the actual diff.
- MUST preserve repository template structure when a hosted template exists.
- MUST NOT mark validation or checklist items as done unless they are actually done.
- MUST NOT recommend committing secrets, credentials, or unrelated generated noise by default.
- SHOULD open a host-specific reference only when exact GitHub or GitLab template mechanics are the blocker.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| mixing unrelated work into one commit | review and rollback become harder | split the changes into separate logical commits |
| writing subjects like `update files` | the commit history loses meaning | write an imperative summary tied to the actual change |
| copying the diff into hosted review text | reviewers lose the higher-level story | summarize the change, motivation, and validation instead |
| moving the common path into host-specific references | the main skill stops being self-sufficient | keep commit drafting and fallback review guidance in `SKILL.md` |
| checking boxes for work that has not been done | the review narrative becomes misleading | mark only the items that are actually true |

## Scope Boundaries

- Activate this skill for:
  - commit readiness decisions
  - Conventional Commit drafting
  - commit split guidance for mixed change sets
  - hosted review body preparation with GitHub/GitLab branching only when needed
- Do not use this skill as the primary source for:
  - Git history rewriting or interactive rebase strategy
  - merge-conflict resolution
  - low-level Git fundamentals for beginners
  - general GitHub or GitLab issue, CI, or project-management workflows
