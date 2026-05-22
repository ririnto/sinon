---
description: >-
  Compose a pull/merge request body from the current branch's commits with structured Summary / Changes / Testing sections. Use when opening or updating a PR or MR and the body must follow pr-mr-convention rules.
argument-hint: Optional PR/MR title or summary hint
allowed-tools:
  - Bash
  - Read
  - AskUserQuestion
---

# Compose PR/MR Body

Generate a structured pull request or merge request body following pr-mr-convention patterns. Analyze the current branch's commits, extract the change narrative, and compose a formatted body ready for submission.

Initial context: $ARGUMENTS

---

## Workflow

### Step 1: Detect Host and Branch Context

Determine the hosting platform and branch information:

```sh
git remote -v
```

Detect:
- GitHub (origin points to github.com)
- GitLab (origin points to gitlab.com or self-hosted instance)
- Other platform (Gitea, Bitbucket, etc.)

Fetch the current branch name:

```sh
git branch --show-current
```

Verify the upstream tracking branch:

```sh
git rev-parse --abbrev-ref @{u}
```

If not set, ask the user what the target branch is (e.g., `main`, `develop`).

### Step 2: Analyze Commits on the Branch

List commits between the base branch and HEAD:

```sh
git log --oneline <base-branch>..HEAD
```

For example:

```sh
git log --oneline main..HEAD
```

Collect:
- All commit messages on the branch
- Commit count and types (feat, fix, refactor, etc.)
- Rough change narrative from commit subjects

### Step 3: Extract Change Summary

From the commits and context provided, determine:

1. What is the high-level change? (e.g., "Add user authentication", "Fix button alignment bug", "Refactor API layer")
2. Why? (e.g., "Resolves #456", "Improves performance by 20%")
3. Scope of impact (e.g., UI components, backend API, tests, documentation)
4. Breaking changes? (Check commit footers for `BREAKING CHANGE:`)

### Step 4: Identify Related Issues

Look for references in commit messages:

```sh
git log --format=%b <base-branch>..HEAD | grep -i 'fixes\|closes\|resolves'
```

Collect issue/PR references and link syntax:
- GitHub: `Fixes #123`, `Closes #456`
- GitLab: `Closes #789`, `Fixes #101`

### Step 5: Draft PR/MR Body

Compose the body with three main sections following pr-mr-convention:

```markdown
## Summary

[One-sentence description of the change and its purpose]

## Changes

- [Bullet point: what changed]
- [Bullet point: what changed]
- [Bullet point: what changed]

## Testing

- [How to test this change]
- [Manual test steps or automated test coverage]
- [Any edge cases or breaking behavior to note]
```

Summary section:
- Concise, single focus
- Include the "why" (business/technical reason)

Changes section:
- Bullet points describing each logical change
- Include file or component names if helpful
- Mention refactors, test additions, docs

Testing section:
- Describe how to verify the change works
- Link to CI/CD results if applicable
- Note any manual testing required
- Call out breaking changes here

### Step 6: Handle Breaking Changes

If commits contain `BREAKING CHANGE:` footers:

```sh
git log --format=%b <base-branch>..HEAD | grep -A2 'BREAKING CHANGE'
```

Add a **Breaking Changes** section before Testing:

```markdown
## Breaking Changes

- [Describe what breaks and migration path]
```

### Step 7: Detect Platform-Specific Syntax

GitHub (Markdown):
- Use standard Markdown
- Link syntax: `#123` or `Fixes #456`
- Checklists: `- [ ] Item`, `- [x] Done`

GitLab (Markdown):
- Use standard Markdown
- Link syntax: `#123` or `closes #456`
- Checklists: `- [ ] Item`, `- [x] Done`
- Merge train syntax if applicable

### Step 8: Present Draft and Confirm

Output the draft clearly:

```markdown
===== DRAFT PR/MR BODY =====

## Summary
...

## Changes
...

## Testing
...

===== END DRAFT =====
```

Ask the user:
- Accept the draft
- Modify before posting
- Add additional sections (e.g., Screenshots, Dependencies)
- Reject and start over

### Step 9: Provide Submission Guidance

Recommend next steps based on the host:

GitHub (gh CLI):
```sh
gh pr create --title "Your PR Title" --body-file body.md
```

GitLab (glab CLI):
```sh
glab mr create --title "Your MR Title" --body-file body.md
```

Or guide the user to the web UI to paste the body manually.

### Step 10: Post-Submission Verification

After submission, output:

```sh
git remote get-url origin
```

And provide the direct link to the created PR/MR.

---

## Key Invariants

- The Summary section MUST be concise and include the "why".
- The Changes section MUST use specific bullet points, not prose.
- The Testing section MUST explain how to verify the change.
- Breaking changes, if any, MUST be clearly called out.
- Host detection MUST be automatic (GitHub vs. GitLab).
- The body MUST follow `pr-mr-convention` patterns.
- The command MUST present the draft to the user before posting.

---

## Scope Boundaries

This command focuses on body composition and submission guidance. It does NOT:

- Create the PR/MR title (user provides or hints at it).
- Push the branch (the user SHOULD push first).
- Merge the PR/MR (user does that through UI or CLI).
- Review the PR/MR code (reviewers handle that).

For branch management, refer the user to `git push` or the worktree command.
