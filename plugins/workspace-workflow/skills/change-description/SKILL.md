---
name: change-description
description: >-
  Compose hosted service change descriptions and hosted service change descriptions with disciplined titles,
  structured bodies, review checklists, and consistent metadata.
---

# change description Convention

Compose change descriptions and change descriptions that guide reviewers through a single, focused change with clear intent, structured evidence, and honest assessment of testing and risk.

## Goal

Communicate change intent, impact, and validation so reviewers can assess the work quickly and accurately.
A well-formed change description surfaces the "why" and "what," demonstrates testing discipline, and respects the reviewer's time.

## Scope

This skill covers:

- change description title composition aligned with Conventional Commits.
- Body structure with sections for Summary, Why, Changes, Testing, and Notes.
- Label and reviewer selection strategies.
- Self-review checklist before merging.

This skill does not cover:

- Code review itself (how to review code is a separate discipline).
- CI/CD pipeline configuration.
- Merge strategy selection (fast-forward, squash, no-ff; covered in git-merge-strategies).
- Commit message content for merged commits.

## Operating Rules

- **Title MUST be a single, clear statement of intent**: One line, 50–72 characters, in imperative mood.
  - Use Conventional Commits format: `type(scope): description` (e.g., `feat(api): add user authentication`).
- **Body MUST be structured**: Use consistent section headings (Summary, Why, Changes, Testing, Notes) to guide the reviewer through the change.
- **Title and body MUST convey a single, cohesive change**: Multi-purpose PRs/MRs MUST be split into separate, sequential requests.
  - Large refactors and feature additions MUST be separate.
- **Testing MUST be explicit and honest**: List only tests, lints, type checks, and manual validations that were actually performed.
  - MUST NOT claim "all tests pass" if CI is incomplete.
- **Self-review checklist MUST be completed before marking as ready**: Verify lint, type safety, unit tests, documentation, and relevant migration steps.
- **Draft status SHOULD be used when design, tests, or CI are incomplete**: Move to ready only when all checklist items pass.

## Title Convention

Use Conventional Commits format for change description titles:

```text
type(scope): description
```

Components:

- `type`: One of `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`, `ci`.
- `scope`: The module, feature, or area affected (e.g., `api`, `auth`, `ui`, `deps`).
  - MUST be lowercase, no spaces.
  - Omit if single-scope project.
- `description`: Imperative mood, no period, lowercase start.
  - Summarize the change concisely.

Examples:

- `feat(auth): implement JWT refresh token rotation`
- `fix(api): handle null customer ID in billing endpoint`
- `docs: update API authentication guide`
- `refactor(db): migrate connection pool to async-await`
- `perf(search): add query result caching`

Length: 50–72 characters (enforce via linter or manual review).

## Body Template

Use this structure for all change description bodies.
Customize section depth based on change scope, but preserve section order.

````markdown
## Summary

Brief overview of what changed. 2–4 bullet points, each starting with a verb (adds, fixes, removes, refactors, updates).

- Adds JWT refresh token endpoint with 7-day expiry.
- Fixes race condition in session cleanup on logout.
- Removes deprecated BasicAuth header support.

## Why

Context: the problem being solved or the requirement driving the change. 1–3 bullets.

- Refresh tokens reduce the window of exposure if access tokens are leaked.
- Session cleanup race allowed zombie sessions to persist for up to 30 seconds.
- BasicAuth is no longer used by any active client; removing it reduces attack surface.

## Changes

Factual description of implementation. Group related file changes.

- `src/auth/tokens.ts`: New `RefreshTokenManager` class with rotation logic.
- `src/auth/routes.ts`: New POST `/auth/refresh` endpoint; validates refresh token and returns new access token.
- `src/session/cleanup.ts`: Wrapped cleanup in mutex to prevent concurrent deletes.
- `src/types/auth.ts`: Added `RefreshTokenPayload` interface.
- Removed deprecated routes from `src/routes/legacy.ts`.

## Testing

List only validations that were actually performed.

- [x] Unit tests for RefreshTokenManager (100% coverage).
- [x] Integration test: POST /auth/refresh with valid and expired tokens.
- [x] Manual test: refresh token lifecycle with 5-minute sleep to verify expiry window.
- [x] Lint and type check pass (`npm run lint`, `npm run typecheck`).
- [ ] Load test on refresh endpoint (deferred to post-merge optimization task).

## Notes

Risks, caveats, breaking changes, deployment notes, or follow-up work.

- **Breaking change**: Clients relying on BasicAuth must migrate to Bearer token before this release.
- **Deployment note**: Refresh tokens are stored in a new database table; run migration in pre-deployment window.
- **Known limitation**: Refresh token rotation is not yet replicated across multi-region deployments (deferred to a dedicated follow-up task).
- **Follow-up**: Add refresh token analytics in next sprint to identify abuse patterns.
````

## Title and Body: Host-Specific Details

### hosted service

- **Title**: Use Conventional Commits format as shown above.
- **Body**: Use standard markdown.
  - hosted service supports GFM alerts (`> [!NOTE]`, `> [!WARNING]`, etc.) in PR descriptions.

**Minimal example using hosted service alert:**

```markdown
## Summary

- Adds rate limit header to all API responses.

## Why

- Client SDKs need visibility into rate limit consumption.

## Testing

- [x] Unit tests pass.
- [x] Integration tests pass.

> [!WARNING]
>
> Rate limit defaults have changed. Clients MUST update their backoff logic.
```

**Create command:**

```sh
  --body-file body.md \
  --draft
```

### hosted service

- **Title**: Use Conventional Commits format as shown above.
- **Body**: Use standard markdown.

**Minimal example using quick actions:**

```markdown
## Summary

- Adds rate limit header to all API responses.

## Why

- Client SDKs need visibility into rate limit consumption.

## Testing

- [x] Unit tests pass.
- [x] Integration tests pass.

## Notes

**Breaking change**: Clients MUST update their backoff logic.

/assign @reviewer-name
/label ~type:feature ~priority:high
```

**Create command:**

```sh
  --description "$(cat body.md)" \
  --draft
```

## Decision: Draft versus Ready

Move a change description from draft to ready only when:

- **All title and body sections are complete** and truthful.
- **CI pipeline is passing** (lint, type check, tests).
- **Test coverage is appropriate** for the change type (100% for critical paths; ≥80% for features; optional for docs).
- **Self-review checklist is complete** (see below).
- **Design is finalized** (no open questions or unresolved decisions in the change description comments).

**Keep as draft when:**

- CI is not yet passing.
- Tests are pending or incomplete.
- Design or implementation is still being debated.
- Waiting for dependent change description to merge.

**Example workflow:**

1. Create change description in draft status.
2. Push commits, run CI, fix failures.
3. When CI passes and tests are complete, complete the self-review checklist.
4. Mark as ready for review.

## Labels and Metadata


Use a consistent label taxonomy.
Recommended categories:

| Category | Examples | Purpose |
| --- | --- | --- |
| **type** | `type:feature`, `type:fix`, `type:docs`, `type:refactor` | Classify change kind. |
| **scope** | `scope:auth`, `scope:api`, `scope:ui`, `scope:deps` | Identify affected subsystem. |
| **priority** | `priority:high`, `priority:medium`, `priority:low` | Signal urgency. |
| **status** | `status:ready-to-merge`, `status:needs-revision`, `status:blocked` | Communicate blockers. |
| **effort** | `effort:small`, `effort:medium`, `effort:large` | Estimate reviewer time. |

**Apply labels when opening the PR:**

```sh
```


Use consistently:

```sh
```

Use this to prevent conflicting labels.

## Reviewer and Assignee Strategy

- **Assign MUST include at least one reviewer** before marking ready.
  - Assign to the person directly responsible for review.
- **Reviewers SHOULD be from the same team or subsystem** when possible (domain knowledge reduces review time).
- **Codeowners SHOULD be used** to auto-request reviewers if the repository defines a `CODEOWNERS` file.
- **Round-robin assignment SHOULD rotate reviewers** across the team to distribute load and knowledge.

**hosted service example:**

```sh
```


```markdown
/assign @reviewer-name
/reviewer @code-owner
```

## Self-Review Checklist

Complete this checklist before marking your change description as ready for review.
Do not rely on reviewers to catch these items.

- [ ] **Title follows Conventional Commits**: Type, scope, description in imperative mood.
- [ ] **Body is complete**: Summary, Why, Changes, Testing, and Notes sections are filled with truthful details.
- [ ] **Single, cohesive change**: If multiple features or major refactors, split into separate PRs/MRs.
- [ ] **Lint passes**: Run linter; fix all violations.
- [ ] **Type check passes**: No `any` types; resolve all type errors.
- [ ] **Unit tests added**: New public functions have corresponding tests; coverage ≥80%.
- [ ] **Integration tests pass**: Feature or fix is validated end-to-end.
- [ ] **Documentation updated**: Comments, README, or user-facing docs reflect the change.
- [ ] **No debug code**: Remove `console.log`, `debugger`, or commented-out code.
- [ ] **Breaking changes listed**: If API changes, incompatible behavior shifts, or migration steps are required, document them in Notes.
- [ ] **Dependencies reviewed**: New or updated deps have been checked for security, size, and maintenance status.
- [ ] **Commit history is clean**: Squash merge-in commits or intermediate debugging commits (unless rebasing is not allowed).
- [ ] **Labels assigned**: Type, scope, priority applied appropriately.
- [ ] **Reviewers assigned**: At least one domain expert assigned.

## Pitfalls

- **Large change description (>400 lines)**: Split into smaller, focused requests.
  - Reviewers will skim large diffs and miss issues.
  - Exception: generated code or large refactors; document the rationale in Notes.
- **Vague titles**: avoid "Updates", "Fixes bug", or "WIP".
  - Be specific.
  - "fix(auth): prevent session fixation attack" is better than "Fixes auth bug".
- **Fabricated validation**: MUST NOT claim "all tests pass" if you have not run tests.
  - Write "tests pending CI confirmation" instead.
- **No Why section**: Reviewers may not understand why the change is necessary.
  - Always explain the problem, requirement, or context.
- **Multiple concerns in one change description**: Feature + refactor + dependency bump = hard to review and harder to revert.
  - Keep each change separate.
- **Unresolved conflicts in commit history**: MUST NOT merge with rebase conflicts, merge conflicts, or unmerged dependencies.
- **Reviewer assignment to inactive accounts**: Verify assignee is active and available before sending for review.

## First Safe Commands

**hosted service workflow:**

```sh
# Create PR in draft status with title and body file
host-cli pr create --draft \
  --title "feat(api): add user authentication" \
  --body-file pr-body.md

# Add labels after creation

# Assign reviewer

# Convert to ready when tests pass and checklist is complete
host-cli pr ready <number>
```

**hosted service workflow:**

```sh
# Create MR in draft status with title and body (including quick actions)
host-cli mr create --draft \
  --title "feat(api): add user authentication" \
  --description "$(cat mr-body.md)"

# The MR body includes quick actions like /assign, /label, /reviewer
# After CI passes and checklist is complete, mark ready via web UI or:
host-cli mr update <number> --ready
```

## hosted service CLI (host-cli) Cheat Sheet

### Create a change description

```sh
# Create with title and body file (most common)
  --body-file body.md \
  --draft

# Create with explicit title and inline body
host-cli pr create --title "fix(db): handle null connection" \

# Auto-fill from commits (use with caution)
host-cli pr create --fill --draft

# Create and assign reviewers/labels immediately
host-cli pr create --title "feat(auth): JWT refresh token" \
  --body-file body.md \
  --label "type:feature,scope:auth,priority:high" \
  --reviewer @alice,@bob \
  --assignee @maintainer
```

### Edit an Existing PR

```sh
# Update title and body from file
host-cli pr edit 42 --title "New title" --body-file body.md

# Add/remove labels

# Add/remove reviewers

# Add/remove assignees
```

### Check PR Status and Details

```sh
# View PR details (title, body, CI status)
host-cli pr view 42

# View PR with comments
host-cli pr view 42 --comments

# List open PRs (default)
host-cli pr list

# List all PRs with filters
host-cli pr list --search "status:success review:required"
host-cli pr list --draft
host-cli pr list --author "@me" --state closed
```

### Mark as Ready / Convert to Draft

```sh
# Convert from draft to ready
host-cli pr ready 42

# Convert from ready to draft
host-cli pr ready 42 --undo
```

### Merge a PR

```sh
# Standard merge (create merge commit)
host-cli pr merge 42 --merge

# Squash and merge (flatten commits into one)
host-cli pr merge 42 --squash

# Rebase and merge (reapply commits on base branch)
host-cli pr merge 42 --rebase

# Auto-merge when checks pass (do not merge immediately)
host-cli pr merge 42 --auto --squash

# Merge and delete source branch
host-cli pr merge 42 --squash --delete-branch
```

### Recommended Option Combinations

| Scenario | Command |
| --- | --- |
| Ready after tests pass | `host-cli pr ready <n>` |
| Merge when approved (avoid manual merge button) | `host-cli pr merge <n> --squash --auto --delete-branch` |

## hosted service CLI (host-cli) Cheat Sheet

### Create a change description

```sh
# Create with title and description from file (most common)
  --description "$(cat body.md)" \
  --draft

# Create with inline description
host-cli mr create --title "fix(db): handle null connection" \

# Auto-fill from commits
host-cli mr create --fill --draft

# Create with labels, reviewers, assignees (via flags or quick actions)
host-cli mr create --title "feat(auth): JWT refresh token" \
  --description "$(cat body.md)" \
  --label "type::feature,scope::auth" \
  --reviewer @alice,@bob \
  --assignee @maintainer
```

### Edit an Existing MR

```sh
# Update title and description
host-cli mr update 42 --title "New title" \
  --description "$(cat body.md)"

# Add/remove labels

# Add/remove reviewers (prefix with '+' to add, '-' to remove)

# Add/remove assignees
```

### Check MR Status and Details

```sh
# View MR details (title, body, CI status, discussions)
host-cli mr view 42

# View MR with comments and discussions
host-cli mr view 42 --comments

# View only resolved discussions
host-cli mr view 42 --resolved

# List open MRs (default)
host-cli mr list

# List with filters
host-cli mr list --draft
host-cli mr list --all

# Search in title and description
host-cli mr list --search "adds feature X"

# Filter by branch
host-cli mr list --source-branch "new-feature" --target-branch "main"
```

### Mark as Ready / Convert to Draft

```sh
# Convert from draft to ready for review
host-cli mr update 42 --ready

# Convert from ready to draft
host-cli mr update 42 --draft
```

### Merge an MR

```sh
# Standard merge (create merge commit)
host-cli mr merge 42

# Squash commits before merge
host-cli mr merge 42 --squash

# Rebase and merge
host-cli mr merge 42 --rebase

# Merge when pipeline succeeds (do not merge immediately)
host-cli mr merge 42 --auto-merge

# Merge and delete source branch
host-cli mr merge 42 --remove-source-branch

# Custom commit message for merge
host-cli mr merge 42 --message "Merge feature X"
```

### Recommended Option Combinations

| Scenario | Command |
| --- | --- |
| Ready after tests pass | `host-cli mr update <n> --ready` |
| Merge when approved | `host-cli mr merge <n> --squash --auto-merge` |

## Using External Body Files

Both `host-cli` and `host-cli` support reading change description body text from files.
This pattern enables pre-writing a body offline and using it repeatedly.

**Prepare body file:**

```markdown
## Summary

- Adds JWT refresh token endpoint.
- Fixes session cleanup race condition.

## Why

- Refresh tokens reduce exposure window if access tokens leak.
- Session cleanup race allowed zombie sessions up to 30 seconds.

## Testing

- [x] Unit tests (100% coverage).
- [x] Integration tests with valid/expired tokens.
- [x] Manual test: 5-minute refresh lifecycle validation.
```

**hosted service (host-cli):**

```sh
# Use --body-file to read from file
host-cli pr create --title "feat(auth): JWT refresh" --body-file body.md --draft

# Update existing PR body from file
host-cli pr edit 42 --body-file body.md
```

**hosted service (host-cli):**

```sh
# Pass file content to --description using command substitution
host-cli mr create --title "feat(auth): JWT refresh" \
  --description "$(cat body.md)" \
  --draft

# Update existing MR description from file
host-cli mr update 42 --description "$(cat body.md)"
```

## Output Contract

When composed correctly, a change description output satisfies these invariants:

- **Title**: 50–72 characters, Conventional Commits format, single intent.
- **Body**: Markdown with sections (Summary, Why, Changes, Testing, Notes), all populated with truthful details extracted from diff.
- **Labels**: Type, scope, priority; no conflicting or redundant labels.
- **Assignees**: At least one reviewer from the affected team or subsystem.
- **Status**: Draft if CI incomplete or design unresolved; ready if all checklist items passed.
- **No placeholder text**: All sections include concrete details, not "TBD" or "[fill in]".

## References

- None yet.
  - All common-case guidance is contained in `SKILL.md`.
