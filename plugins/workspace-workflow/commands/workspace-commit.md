---
description: >-
  Compose a Conventional Commits-style commit message from the staged or HEAD diff. Use when committing the current change with a structured, scope-aware message that matches commit-convention rules.
argument-hint: Optional additional context about the change
allowed-tools:
  - Bash
  - Read
  - AskUserQuestion
---

# Compose Commit Message

Generate a well-structured commit message following Conventional Commits specification. Analyze the staged changes (or HEAD if nothing staged), determine commit type and scope, and output a draft message ready for review.

Initial context: $ARGUMENTS

---

## Workflow

### Step 1: Check Git Status

Run `git status` to identify what is staged:

```sh
git status --short
```

Capture the output to understand:
- Which files are staged (marked `M`, `A`, `D`, etc.)
- Which files are unstaged or untracked

If nothing is staged, ask the user whether they want to:
- Include staged changes only
- Include HEAD changes (last commit)
- Stage specific files first

### Step 2: Examine the Diff

View the staged diff to understand the nature of the change:

```sh
git diff --staged
```

Or if using HEAD:

```sh
git diff HEAD~1 HEAD
```

Read the full diff to grasp:
- What code changed
- Why it changed (logic, refactor, fix)
- Which components are affected (src/components/Button, tests/unit/, docs/, etc.)

### Step 3: Determine Type and Scope

Consult the commit-convention skill for authoritative type and scope guidance. Decide:

Type (pick one):
- `feat` — New feature
- `fix` — Bug fix
- `refactor` — Code restructuring without feature or fix
- `docs` — Documentation only
- `test` — Test addition or modification
- `chore` — Build, deps, tooling, or release
- `ci` — CI/CD configuration
- `perf` — Performance improvement

Scope (optional, in parentheses):
- A short label identifying the affected module or component (e.g., `Button`, `auth`, `CLI`, `docs`)
- Omit if the change spans multiple areas or is broadly scoped

### Step 4: Draft the Message

Compose a draft message in the following format:

```text
<type>(<scope>): <subject>

<body (optional)>

<footer (optional)>
```

Subject line:
- Imperative mood: "add", "fix", "remove", not "adds", "fixed", "removes"
- No period at end
- 50 characters or fewer (guideline)
- Lowercase unless proper nouns

Body (optional, use if change is non-trivial):
- Wrapped at ~72 characters
- Explain *what* changed and *why*, not *how*
- One blank line between subject and body

Footer (optional, for breaking changes or references):
- Prefix with `BREAKING CHANGE:` if the change breaks backwards compatibility
- Prefix with `Fixes #123` to link to issues
- One blank line before footer

### Step 5: Present Draft and Confirm

Output the draft message clearly:

```text
===== DRAFT COMMIT MESSAGE =====
<full message here>
===== END DRAFT =====
```

Ask the user to:
- Accept the draft
- Modify it before committing
- Reject and start over

### Step 6: Evaluate Multi-Commit Need

If the diff shows multiple distinct changes (e.g., refactor + feature + test), ask the user:
- Should this be split into multiple commits?
- Which changes go together logically?

If yes, iterate through the workflow for each commit.

### Step 7: Execute Commit (User Confirmation)

Guide the user through committing:

```sh
git commit -m "type(scope): subject" -m "body" -m "footer"
```

Or open an editor:

```sh
git commit
```

Output the committed state:

```sh
git log --oneline -1
```

---

## Key Invariants

- Type and scope MUST follow Conventional Commits specification.
- Subject MUST be imperative mood and lowercase.
- Body (if present) MUST explain *why*, not *how*.
- Multi-commit decisions MUST be user-driven; MUST NOT split without asking.
- Always verify `git status` before drafting.
- Always show the draft to the user before executing.

---

## Scope Boundaries

This command focuses on message composition and user confirmation. It does NOT:

- Modify files or staging
- Create branches
- Push commits
- Generate migration guides for breaking changes (user handles that)

For staging-related tasks, refer the user to `git add` or `git reset`.
