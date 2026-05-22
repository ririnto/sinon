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

Recommend next steps based on the host. Save the body to a file and provide CLI commands.

**GitHub (gh CLI):**

Provide these commands in sequence:

```sh
# 1. Save body to file (if not already saved)
cat > body.md << 'EOF'
[paste the draft body from Step 8 here]
EOF

# 2. Create PR in draft status
gh pr create --title "Your PR Title" \
  --body-file body.md \
  --draft

# 3. After PR is created, add labels, reviewers, and assignees
gh pr edit <PR_NUMBER> \
  --add-label "type:feature,scope:api,priority:high" \
  --add-reviewer @alice,@bob \
  --add-assignee @maintainer

# 4. Once CI passes and checklist is complete, mark as ready
gh pr ready <PR_NUMBER>

# 5. Merge when approved (squash strategy recommended)
gh pr merge <PR_NUMBER> --squash --delete-branch
```

Alternatively, guide the user to the GitHub web UI if CLI is unavailable.

**GitLab (glab CLI):**

Provide these commands in sequence:

```sh
# 1. Save body to file (if not already saved)
cat > body.md << 'EOF'
[paste the draft body from Step 8 here, including quick actions]
EOF

# 2. Create MR in draft status
glab mr create --title "Your MR Title" \
  --description "$(cat body.md)" \
  --draft

# 3. Or, create with explicit labels and reviewers via flags
glab mr create --title "Your MR Title" \
  --description "$(cat body.md)" \
  --draft \
  --label "type::feature,scope::api,priority::high" \
  --reviewer @alice,@bob \
  --assignee @maintainer

# 4. Once CI passes and checklist is complete, mark as ready
glab mr update <MR_NUMBER> --ready

# 5. Merge when approved (squash strategy recommended)
glab mr merge <MR_NUMBER> --squash --when-pipeline-succeeds --remove-source-branch
```

Alternative: Body text can include GitLab quick actions (e.g., `/assign @user`, `/label ~type::feature`) which execute after MR creation.

**Inline Body Option (if file handling is cumbersome):**

GitHub:
```sh
gh pr create --title "feat(api): ..." --body "## Summary\n\n- Item 1\n- Item 2" --draft
```

GitLab:
```sh
glab mr create --title "feat(api): ..." --description "## Summary\n\n- Item 1\n- Item 2" --draft
```

### Step 10: Post-Submission Verification

After the PR/MR is created, verify it was successfully submitted:

```sh
# Get repository origin URL
git remote get-url origin
```

Then navigate to the created PR/MR using the reported URL or these commands:

**GitHub:**

```sh
# View the PR you just created on the current branch
gh pr view

# View a specific PR by number
gh pr view 42

# Open it in your browser
gh pr view 42 --web

# List recent PRs to find yours
gh pr list --author "@me" --state all --limit 5
```

**GitLab:**

```sh
# View the MR you just created on the current branch
glab mr view

# View a specific MR by number
glab mr view 42

# Open it in your browser
glab mr view 42 --web

# List recent MRs to find yours
glab mr list --assignee "@me" --all --per-page 5
```

**Verification Checklist:**

- [ ] PR/MR number is reported and matches the CLI output.
- [ ] Title and body match what was submitted.
- [ ] Draft status is set correctly (should be draft initially).
- [ ] Labels and assignees are applied as intended.
- [ ] CI pipeline is running (check status in PR/MR details).
- [ ] Reviewers are notified (check review request section).

**Next Steps:**

1. Monitor CI progress in the PR/MR details.
2. Address any lint, type check, or test failures.
3. Push fixes to the same branch; PR/MR updates automatically.
4. When all checks pass, mark as ready (see Step 9 commands).
5. Request review if not automatically triggered by CODEOWNERS.
6. Merge after approval using the recommended merge strategy (squash or rebase).

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
