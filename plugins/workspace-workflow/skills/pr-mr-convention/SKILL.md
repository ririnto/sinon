---
name: pr-mr-convention
description: >-
  Compose GitHub pull requests and GitLab merge requests with disciplined titles,
  structured bodies, review checklists, and consistent metadata. Use this skill
  when opening or updating a PR/MR, writing review-friendly descriptions, choosing
  labels and reviewers, or aligning host conventions across GitHub and GitLab.
---

# PR/MR Convention

Compose pull requests and merge requests that guide reviewers through a single, focused change with clear intent, structured evidence, and honest assessment of testing and risk.

## Goal

Communicate change intent, impact, and validation so reviewers can assess the work quickly and accurately. A well-formed PR/MR surfaces the "why" and "what," demonstrates testing discipline, and respects the reviewer's time.

## Scope

This skill covers:

- PR/MR title composition aligned with Conventional Commits.
- Body structure with sections for Summary, Why, Changes, Testing, and Notes.
- Label and reviewer selection strategies.
- Host-specific metadata (GitHub labels vs. GitLab scoped labels, draft vs. ready status).
- Self-review checklist before merging.

This skill does not cover:

- Code review itself (how to review code is a separate discipline).
- CI/CD pipeline configuration.
- Merge strategy selection (fast-forward, squash, no-ff; covered in git-merge-strategies).
- Commit message content for merged commits.

## Operating Rules

- **Title MUST be a single, clear statement of intent**: One line, 50–72 characters, in imperative mood. Use Conventional Commits format: `type(scope): description` (e.g., `feat(api): add user authentication`).
- **Body MUST be structured**: Use consistent section headings (Summary, Why, Changes, Testing, Notes) to guide the reviewer through the change.
- **Title and body MUST convey a single, cohesive change**: Multi-purpose PRs/MRs MUST be split into separate, sequential requests. Large refactors and feature additions MUST be separate.
- **Testing MUST be explicit and honest**: List only tests, lints, type checks, and manual validations that were actually performed. MUST NOT claim "all tests pass" if CI is incomplete.
- **Self-review checklist MUST be completed before marking as ready**: Verify lint, type safety, unit tests, documentation, and relevant migration steps.
- **Draft status SHOULD be used when design, tests, or CI are incomplete**: Move to ready only when all checklist items pass.

## Title Convention

Use Conventional Commits format for PR/MR titles:

```text
type(scope): description
```

**Components:**

- `type`: One of `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`, `ci`.
- `scope`: The module, feature, or area affected (e.g., `api`, `auth`, `ui`, `deps`). MUST be lowercase, no spaces. Omit if single-scope project.
- `description`: Imperative mood, no period, lowercase start. Summarize the change concisely.

**Examples:**

- `feat(auth): implement JWT refresh token rotation`
- `fix(api): handle null customer ID in billing endpoint`
- `docs: update API authentication guide`
- `refactor(db): migrate connection pool to async-await`
- `perf(search): add query result caching`

**Length:** 50–72 characters (enforce via linter or manual review).

## Body Template

Use this structure for all PR/MR bodies. Customize section depth based on change scope, but preserve section order.

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
- **Known limitation**: Refresh token rotation is not yet replicated across multi-region deployments (tracked in issue #456).
- **Follow-up**: Add refresh token analytics in next sprint to identify abuse patterns.
````

## Title and Body: Host-Specific Details

### GitHub

- **Title**: Use Conventional Commits format as shown above.
- **Body**: Use standard markdown. GitHub supports GFM alerts (`> [!NOTE]`, `> [!WARNING]`, etc.) in PR descriptions.

**Minimal example using GitHub alert:**

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
gh pr create --title "feat(api): add rate limit headers" \
  --body-file body.md \
  --draft
```

### GitLab

- **Title**: Use Conventional Commits format as shown above.
- **Body**: Use standard markdown. GitLab supports quick actions (e.g., `/assign`, `/label`) in MR descriptions.

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
glab mr create --title "feat(api): add rate limit headers" \
  --description "$(cat body.md)" \
  --draft
```

## Decision: Draft vs. Ready

Move a PR/MR from draft to ready only when:

- **All title and body sections are complete** and truthful.
- **CI pipeline is passing** (lint, type check, tests).
- **Test coverage is appropriate** for the change type (100% for critical paths; ≥80% for features; optional for docs).
- **Self-review checklist is complete** (see below).
- **Design is finalized** (no open questions or unresolved decisions in the PR/MR comments).

**Keep as draft when:**

- CI is not yet passing.
- Tests are pending or incomplete.
- Design or implementation is still being debated.
- Waiting for dependent PR/MR to merge.

**Example workflow:**

1. Create PR/MR in draft status.
2. Push commits, run CI, fix failures.
3. When CI passes and tests are complete, complete the self-review checklist.
4. Mark as ready for review.

## Labels and Metadata

### GitHub Labels

Use a consistent label taxonomy. Recommended categories:

| Category | Examples | Purpose |
| --- | --- | --- |
| **type** | `type:feature`, `type:fix`, `type:docs`, `type:refactor` | Classify change kind. |
| **scope** | `scope:auth`, `scope:api`, `scope:ui`, `scope:deps` | Identify affected subsystem. |
| **priority** | `priority:high`, `priority:medium`, `priority:low` | Signal urgency. |
| **status** | `status:ready-to-merge`, `status:needs-revision`, `status:blocked` | Communicate blockers. |
| **effort** | `effort:small`, `effort:medium`, `effort:large` | Estimate reviewer time. |

**Apply labels when opening the PR:**

```sh
gh pr create --title "..." --label "type:feature,scope:api,priority:high"
```

### GitLab Scoped Labels

GitLab supports scoped labels: `scope::value` syntax. Use consistently:

```sh
glab mr create --title "..." --label "type::feature,scope::api,priority::high"
```

**Key difference from GitHub**: Scoped labels enforce a single value per scope (e.g., only one `type::*` can be active). Use this to prevent conflicting labels.

## Reviewer and Assignee Strategy

- **Assign MUST include at least one reviewer** before marking ready. Assign to the person directly responsible for review.
- **Reviewers SHOULD be from the same team or subsystem** when possible (domain knowledge reduces review time).
- **Codeowners SHOULD be used** to auto-request reviewers if the repository defines a `CODEOWNERS` file.
- **Round-robin assignment SHOULD rotate reviewers** across the team to distribute load and knowledge.

**GitHub example:**

```sh
gh pr create --title "..." --assignee @reviewer-github-handle --reviewer @code-owner
```

**GitLab example (using quick actions in body):**

```markdown
/assign @reviewer-name
/reviewer @code-owner
```

## Self-Review Checklist

Complete this checklist before marking your PR/MR as ready for review. Do not rely on reviewers to catch these items.

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

- **Large PR/MR (>400 lines)**: Split into smaller, focused requests. Reviewers will skim large diffs and miss issues. Exception: generated code or large refactors; document the rationale in Notes.
- **Vague titles** (e.g., "Updates", "Fixes bug", "WIP"): Be specific. "fix(auth): prevent session fixation attack" is better than "Fixes auth bug".
- **Fabricated validation**: MUST NOT claim "all tests pass" if you have not run tests. Write "tests pending CI confirmation" instead.
- **No Why section**: Reviewers may not understand why the change is necessary. Always explain the problem, requirement, or context.
- **Multiple concerns in one PR/MR**: Feature + refactor + dependency bump = hard to review and harder to revert. Keep each change separate.
- **Unresolved conflicts in commit history**: MUST NOT merge with rebase conflicts, merge conflicts, or unmerged dependencies.
- **Reviewer assignment to inactive accounts**: Verify assignee is active and available before sending for review.

## First Safe Commands

**GitHub workflow:**

```sh
# Create PR in draft status with title and body file
gh pr create --draft \
  --title "feat(api): add user authentication" \
  --body-file pr-body.md

# Add labels after creation
gh pr edit <number> --add-label "type:feature,scope:api,priority:high"

# Assign reviewer
gh pr edit <number> --add-assignee @reviewer-name

# Convert to ready when tests pass and checklist is complete
gh pr ready <number>
```

**GitLab workflow:**

```sh
# Create MR in draft status with title and body (including quick actions)
glab mr create --draft \
  --title "feat(api): add user authentication" \
  --description "$(cat mr-body.md)"

# The MR body includes quick actions like /assign, /label, /reviewer
# After CI passes and checklist is complete, mark ready via web UI or:
glab mr update <number> --ready
```

## GitHub CLI (gh) Cheat Sheet

### Create a Pull Request

```sh
# Create with title and body file (most common)
gh pr create --title "feat(api): add rate limit headers" \
  --body-file body.md \
  --draft

# Create with explicit title and inline body
gh pr create --title "fix(db): handle null connection" \
  --body "Fixes #123. Adds null checks to prevent crash."

# Auto-fill from commits (use with caution)
gh pr create --fill --draft

# Create and assign reviewers/labels immediately
gh pr create --title "feat(auth): JWT refresh token" \
  --body-file body.md \
  --label "type:feature,scope:auth,priority:high" \
  --reviewer @alice,@bob \
  --assignee @maintainer
```

### Edit an Existing PR

```sh
# Update title and body from file
gh pr edit 42 --title "New title" --body-file body.md

# Add/remove labels
gh pr edit 42 --add-label "needs-review" --remove-label "draft-review"

# Add/remove reviewers
gh pr edit 42 --add-reviewer @alice --remove-reviewer @bob

# Add/remove assignees
gh pr edit 42 --add-assignee @me --remove-assignee @previous-owner
```

### Check PR Status and Details

```sh
# View PR details (title, body, CI status)
gh pr view 42

# View PR with comments
gh pr view 42 --comments

# List open PRs (default)
gh pr list

# List all PRs with filters
gh pr list --state all --assignee @me
gh pr list --label "needs-review" --state open
gh pr list --search "status:success review:required"

# Find PRs by draft status or label
gh pr list --draft
gh pr list --author "@me" --state closed
```

### Mark as Ready / Convert to Draft

```sh
# Convert from draft to ready
gh pr ready 42

# Convert from ready to draft
gh pr ready 42 --undo
```

### Merge a PR

```sh
# Standard merge (create merge commit)
gh pr merge 42 --merge

# Squash and merge (flatten commits into one)
gh pr merge 42 --squash

# Rebase and merge (reapply commits on base branch)
gh pr merge 42 --rebase

# Auto-merge when checks pass (do not merge immediately)
gh pr merge 42 --auto --squash

# Merge and delete source branch
gh pr merge 42 --squash --delete-branch
```

### Recommended Option Combinations

| Scenario | Command |
| --- | --- |
| Start new feature with discipline | `gh pr create --draft --title "feat(x): ..." --body-file body.md --label "type:feature"` |
| Add CI/review requirements | `gh pr edit <n> --add-reviewer @maintainer --add-label "needs-review"` |
| Ready after tests pass | `gh pr ready <n>` |
| Merge when approved (avoid manual merge button) | `gh pr merge <n> --squash --auto --delete-branch` |

## GitLab CLI (glab) Cheat Sheet

### Create a Merge Request

```sh
# Create with title and description from file (most common)
glab mr create --title "feat(api): add rate limit headers" \
  --description "$(cat body.md)" \
  --draft

# Create with inline description
glab mr create --title "fix(db): handle null connection" \
  --description "Fixes #123. Adds null checks to prevent crash."

# Auto-fill from commits
glab mr create --fill --draft

# Create with labels, reviewers, assignees (via flags or quick actions)
glab mr create --title "feat(auth): JWT refresh token" \
  --description "$(cat body.md)" \
  --label "type::feature,scope::auth" \
  --reviewer @alice,@bob \
  --assignee @maintainer
```

### Edit an Existing MR

```sh
# Update title and description
glab mr update 42 --title "New title" \
  --description "$(cat body.md)"

# Add/remove labels
glab mr update 42 --label "needs-review" --unlabel "wip"

# Add/remove reviewers (prefix with '+' to add, '-' to remove)
glab mr update 42 --reviewer "+@alice,-@bob"

# Add/remove assignees
glab mr update 42 --assignee "+@me,-@previous"
```

### Check MR Status and Details

```sh
# View MR details (title, body, CI status, discussions)
glab mr view 42

# View MR with comments and discussions
glab mr view 42 --comments

# View only resolved discussions
glab mr view 42 --resolved

# List open MRs (default)
glab mr list

# List with filters
glab mr list --assignee @me
glab mr list --reviewer @me
glab mr list --label "needs-review"
glab mr list --draft
glab mr list --all  # Include closed and merged

# Search in title and description
glab mr list --search "adds feature X"

# Filter by branch
glab mr list --source-branch "new-feature" --target-branch "main"
```

### Mark as Ready / Convert to Draft

```sh
# Convert from draft to ready for review
glab mr update 42 --ready

# Convert from ready to draft
glab mr update 42 --draft
```

### Merge an MR

```sh
# Standard merge (create merge commit)
glab mr merge 42

# Squash commits before merge
glab mr merge 42 --squash

# Rebase and merge
glab mr merge 42 --rebase

# Merge when pipeline succeeds (do not merge immediately)
glab mr merge 42 --when-pipeline-succeeds

# Merge and delete source branch
glab mr merge 42 --remove-source-branch

# Custom commit message for merge
glab mr merge 42 --message "Merge feature X"
```

### Recommended Option Combinations

| Scenario | Command |
| --- | --- |
| Start new feature with discipline | `glab mr create --draft --title "feat(x): ..." --description "$(cat body.md)" --label "type::feature"` |
| Add review requirements | `glab mr update <n> --reviewer @maintainer --label "needs-review"` |
| Ready after tests pass | `glab mr update <n> --ready` |
| Merge when approved | `glab mr merge <n> --squash --when-pipeline-succeeds` |

## Using External Body Files

Both `gh` and `glab` support reading PR/MR body text from files. This pattern enables pre-writing a body offline and using it repeatedly.

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

**GitHub (gh):**

```sh
# Use --body-file to read from file
gh pr create --title "feat(auth): JWT refresh" --body-file body.md --draft

# Update existing PR body from file
gh pr edit 42 --body-file body.md
```

**GitLab (glab):**

```sh
# Pass file content to --description using command substitution
glab mr create --title "feat(auth): JWT refresh" \
  --description "$(cat body.md)" \
  --draft

# Update existing MR description from file
glab mr update 42 --description "$(cat body.md)"
```

## Output Contract

When composed correctly, a PR/MR output satisfies these invariants:

- **Title**: 50–72 characters, Conventional Commits format, single intent.
- **Body**: Markdown with sections (Summary, Why, Changes, Testing, Notes), all populated with truthful details extracted from diff.
- **Labels**: Type, scope, priority; no conflicting or redundant labels.
- **Assignees**: At least one reviewer from the affected team or subsystem.
- **Status**: Draft if CI incomplete or design unresolved; ready if all checklist items passed.
- **No placeholder text**: All sections include concrete details, not "TBD" or "[fill in]".

## References

- None yet. All common-case guidance is contained in SKILL.md.
