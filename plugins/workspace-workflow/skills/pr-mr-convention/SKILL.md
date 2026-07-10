---
name: pr-mr-convention
description: >-
  Compose focused pull request and merge request titles, bodies, review evidence, and metadata.
  Use when drafting or updating PR/MR review context, preserving repository templates, selecting draft status, or routing GitHub versus GitLab publication details.
---

# PR/MR Convention

Compose one truthful review request from real repository state.
Keep host-neutral drafting in this file and load only the selected host reference for template locations and CLI commands.

## Scope

This skill covers:

- title and body composition
- repository template preservation
- validation and risk reporting
- draft versus ready decisions
- labels, reviewers, and assignees
- GitHub or GitLab publication routing

It does not cover code-review judgment, CI design, merge strategy, history rewriting, or issue management.

## Operating Rules

- Ground every claim in the actual diff, commit history, validation evidence, or explicit user context.
- Preserve an applicable repository template exactly, including heading order.
- List only checks that actually ran.
- Keep one cohesive change per PR or MR.
- Use draft status while design, tests, validation, or dependencies remain incomplete.
- Do not publish, edit host metadata, or assign reviewers without authorization.
- Treat the target branch and repository default branch as separate values.
- Stop for clarification when more than one publication host or template remains plausible.

## First Safe Checks

Inspect local state without mutating it:

```sh
git remote -v
git status --short --branch
git branch -vv
git log -5 --oneline
```

Resolve publication context in this order:

1. explicit user choice
2. existing PR or MR metadata
3. repository policy
4. current branch upstream
5. remote URL as a clue

An upstream or remote URL is not proof when GitHub and GitLab are both plausible.
Ask one focused host question before publication when ambiguity remains.

After selecting a host, open exactly one host reference and use only its CLI and authentication checks.
Do not run both host branches speculatively.

## Drafting Procedure

1. Resolve the publication host, target branch, and repository default branch independently.
2. Load only `references/github.md` or `references/gitlab.md` when host-specific template or CLI behavior is needed.
3. Locate repository templates from the default branch.
4. Resolve the comparison base from explicit target, existing review metadata, or repository policy.
5. Inspect feature-only commits and the merge-base diff.
6. Determine whether the change is one cohesive review unit.
7. Draft the title, body, validation, risks, and metadata from evidence.
8. Preserve the selected template or use the fallback body when none applies.
9. Mark the review draft unless readiness evidence is complete.
10. Return the draft and blockers to the user-facing top-level session for publication.

Safe comparison commands after resolving `<base>`:

```sh
git log <base>..HEAD --oneline
git diff <base>...HEAD --stat
git diff <base>...HEAD
```

Report when remote-tracking refs may be stale.

## Title

Prefer the repository's established convention.
When it uses Conventional Commits, use:

```text
type(scope): imperative description
```

- Use one of `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, or `revert`.
- Add scope only when it clarifies the affected subsystem.
- Keep the description imperative, specific, and without a trailing period.
- Follow repository length policy; when none exists, keep the title within 72 characters when practical.

## Fallback Body

Use this only when no repository or host-managed template is confirmed:

```markdown
## Summary

- <one to three evidence-backed changes>

## Why

- <problem, requirement, or explicit author context>

## Changes

- <grouped implementation facts>

## Validation

- <checks that actually ran, with result>

## Risks

- <specific compatibility, rollout, dependency, or performance risk, or None>

## Unverified Items

- <pending check or None>
```

If motivation is not evident from the diff or supplied context, write `Reason not evident from available evidence; confirm with author` instead of inventing one.

## Template Selection

- Search the repository default branch, not only the feature tree.
- Preserve exact headings and required checklists.
- Distinguish optional named templates from the default template.
- Report host-managed project, group, organization, or instance templates as unconfirmed when the selected CLI cannot inspect them.
- When multiple templates apply, list them and request a choice.

Host-specific template paths and commands are in the selected reference.

## Validation Evidence

Classify every check as:

- passed: command ran and succeeded
- failed: command ran and failed
- pending: not run or result unavailable
- not applicable: reason recorded

Do not convert a planned check into a completed checklist item.
Name exact commands, test cases, CI jobs, or manual actions when known.

## Draft Versus Ready

Keep the review in draft when any of these remain:

- unresolved design decision
- incomplete implementation or dependency
- failed or missing required validation
- unresolved review finding
- placeholder text or unconfirmed template
- unclear target branch or publication host

Ready status requires complete review context, appropriate passing validation, resolved blockers, and explicit publication authorization.

## Metadata

- Reuse the repository's existing label taxonomy.
- Do not invent labels or assume handles exist.
- Prefer reviewers who own the affected subsystem or are named by `CODEOWNERS` or repository policy.
- Report missing reviewer information instead of assigning an arbitrary account.
- Keep labels, reviewers, milestones, and assignees host-specific after host selection.

## Self-Review

Before returning the draft, confirm:

- title describes one change
- target and default branches are not conflated
- template source is named
- summary and changes match the diff
- motivation is evidenced or marked unconfirmed
- validation is honest
- risks and unverified items are explicit
- draft status matches readiness
- no publication action was taken

## Edge Cases

- Multiple GitHub and GitLab remotes: ask which host owns the review unless explicit metadata resolves it.
- Unsupported forge: draft the host-neutral body and return host publication as a blocker.
- Detached HEAD or missing upstream: rely on explicit target or repository policy; do not invent a base.
- Large or multi-purpose diff: recommend split boundaries before drafting one review body.
- Binary or generated changes: name their source and verification when available.
- Stale remote refs: disclose that template and diff discovery may be incomplete.

## Output Contract

Return:

1. selected host and evidence, or the unresolved ambiguity
2. target branch and repository default branch
3. template source
4. title
5. complete body
6. draft or ready recommendation with evidence
7. proposed labels, reviewers, and assignees only when confirmed
8. validation, risks, and unverified items
9. publication blocker or authorized next command

## Optional References

- `references/github.md` - open only after GitHub is selected for template discovery or `gh` commands.
- `references/gitlab.md` - open only after GitLab is selected for template discovery or `glab` commands.
