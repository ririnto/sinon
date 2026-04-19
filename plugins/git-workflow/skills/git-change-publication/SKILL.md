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
2. Check whether there are staged changes, unstaged-only changes, or a staged-and-unstaged mismatch before drafting final commit text.
3. Decide whether the change set is one logical unit or whether mixed-purpose changes should be split.
4. Map the actual intent to a Conventional Commit type and optional scope.
5. Draft an imperative subject line and a short body that explains why the change exists.
6. Detect whether the repository host is GitHub or GitLab when exact template mechanics matter, or stay host-neutral when the host is unknown.
7. Preserve an existing repository template when present, or use a stable fallback review body when none exists.
8. Validate that the final commit and hosted-review text match the actual diff rather than aspirations or future work.

## Decision Branches

- No staged changes:
  - If the user asked for a commit message, stop short of a final commit draft and say the repository has no staged changes yet.
  - If only unstaged changes exist, describe the likely commit direction as provisional until the staged diff exists.
- Staged and unstaged changes differ:
  - Draft the commit message from `git diff --cached`, not from the full working tree.
  - Call out that unstaged changes are excluded from the proposed commit and may belong in a follow-up commit.
- One commit vs split commits:
  - Use one commit only when the staged diff serves one clear purpose.
  - Split when the diff mixes feature work, unrelated cleanup, test-only changes with separate intent, or distinct rollback units.
- Conventional Commit type and scope:
  - Choose the type from the user-visible or maintenance effect, not from file extensions alone.
  - Add a scope only when it clarifies the affected surface such as `auth`, `api`, or `docs`; omit it when it adds noise.
- Host known vs unknown:
  - When the repository host is known and exact template preservation matters, use the matching host reference for mechanics.
  - When the host is unknown or template paths are irrelevant, keep the review body host-neutral in `SKILL.md`.
- Template present vs no template:
  - Preserve the existing headings, order, and required sections when a repository template exists.
  - Use the fallback structure in this file only when no repository template exists.
  - If a host-specific template exists but the exact selected template cannot be confirmed, report the template as unconfirmed instead of silently falling back.
- Validation claims:
  - Report only checks that already ran.
  - If validation is pending or unknown, say that explicitly instead of implying success.

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

If `git diff --cached` is empty, do not present a final commit message as ready-to-run. Say that no staged diff exists yet, and keep any wording provisional until the intended changes are staged.

If `git diff --cached` and `git diff` show different concerns, write the commit message from the staged diff only and note that the unstaged portion is out of scope for this commit.

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

Quick type choices:

| If the real change primarily... | Prefer... | Example |
| --- | --- | --- |
| adds or expands user-visible behavior | `feat` | `feat(search): add prefix matching for tags` |
| corrects incorrect behavior | `fix` | `fix(auth): reject expired refresh tokens` |
| changes documentation only | `docs` | `docs(api): clarify token rotation flow` |
| adds or corrects tests without changing runtime behavior | `test` | `test(auth): cover expired token rejection` |
| restructures code without changing behavior | `refactor` | `refactor(cache): extract key builder` |
| improves performance characteristics | `perf` | `perf(query): reduce duplicate index scans` |
| changes tooling, packaging, or automation | `build`, `ci`, or `chore` | `ci(actions): cache pnpm store` |

Split instead of forcing one commit when the staged diff would produce a subject with `and`, when one part could be reverted without the other, or when the validation story differs across the changes.

Breaking change variant:

```text
feat(api)!: remove legacy session refresh endpoint

- Replace the legacy refresh endpoint with the token-rotation flow.
- Keep server behavior aligned with the new authentication contract.

BREAKING CHANGE: Clients must switch from `/v1/session/refresh` to `/v2/tokens/refresh`.
```

## Ready-to-Adapt Templates

Hosted review body shape to adapt when a repository template exists but the exact body must still be filled with real change details:

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

## Output contract

Return:

1. The publication decision: one commit, split required, or blocked by missing staged changes
2. The proposed Conventional Commit message, or a clearly labeled provisional draft when staging is incomplete
3. The hosted review body text, preserving a confirmed template or using the fallback structure from this file
4. The host and template assumption used: GitHub, GitLab, or unknown; template preserved, template missing, or template unconfirmed
5. The validation statement with only checks that actually ran, plus any explicit blockers or follow-up risks

## Validate the Result

Validate the common case with these checks:

- If no staged changes exist, the result says so clearly instead of pretending a commit is ready.
- If staged and unstaged changes differ, the commit draft is anchored to the staged diff and the mismatch is called out.
- `git status` and the cited diff agree with the proposed commit message.
- The change set is still one logical unit.
- Split-vs-single guidance matches the actual rollback and review boundaries in the diff.
- The subject stays imperative, concise, and typed correctly.
- The chosen Conventional Commit type and optional scope match the real change intent.
- The commit body explains why rather than restating file names.
- Host-specific mechanics stay in references unless the exact host template workflow is the blocker.
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
