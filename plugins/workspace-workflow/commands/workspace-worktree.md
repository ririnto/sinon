---
description: >-
  Operate on git worktrees—create, list, switch, or clean up isolated worktrees for parallel branch work. Use when starting parallel branch work, isolating an experiment, or cleaning up stale worktrees.
argument-hint: Optional action (create/list/switch/remove) and branch or path
allowed-tools:
  - Bash
  - Read
  - AskUserQuestion
---

# Manage Git Worktrees

Manage git worktrees to enable parallel branch work in isolated directories. Create new worktrees, switch between them, list active ones, or clean up stale worktrees.

Initial context: $ARGUMENTS

---

## Workflow

### Step 1: Check Current Worktree State

List all active worktrees and their branches:

```sh
git worktree list
```

Output shows:
- Current worktree (marked with `*`)
- All other worktrees and their branches
- Status (detached, prunable, etc.)

Also capture the current working directory:

```sh
pwd
```

Note which worktree the user is currently in.

### Step 2: Determine User Intent

Ask or infer the user's intent from context:

1. **Create** — Start a new worktree for a new branch
2. **Switch** — Move to an existing worktree
3. **List** — Show all active worktrees (already done above)
4. **Remove** — Delete a worktree and its branch
5. **Prune** — Clean up stale/broken worktree references

If intent is ambiguous, ask:
- "Do you want to create a new worktree, switch to an existing one, or clean up old ones?"

### Step 3: Create a New Worktree

If user wants to create a worktree:

Option A: Create from existing branch

Ask:
- What branch name to check out? (e.g., `feature/auth`)
- Should the worktree be in a default location or custom path?

Verify the branch exists locally or remotely:

```sh
git branch -a | grep <branch-name>
```

Option B: Create from new branch

Ask:
- What base branch? (default: `main` or `develop`)
- What new branch name?

Then run:

```sh
git worktree add <path> -b <new-branch> <base-branch>
```

Example:

```sh
git worktree add ../feature-auth -b feature/auth main
```

Safety Checks Before Create:

1. Verify current worktree is in clean state:

```sh
git status
```

If dirty, ask: "Current worktree has uncommitted changes. Commit or stash them first?"

2. Verify the branch doesn't already have a worktree:

```sh
git worktree list | grep <branch-name>
```

3. Ensure the path is available:

```sh
[ ! -d <path> ] && echo "Path available" || echo "Path exists"
```

Execute Create:

```sh
git worktree add <path> -b <new-branch> <base-branch>
cd <path>
```

Output the new worktree info:

```sh
pwd
git status
```

### Step 4: Switch to an Existing Worktree

If user wants to switch worktrees:

List active worktrees:

```sh
git worktree list
```

Ask which one to switch to, or accept it from context.

Change directory:

```sh
cd <worktree-path>
```

Confirm the switch:

```sh
pwd
git status
git branch --show-current
```

### Step 5: Remove a Worktree

If user wants to remove a worktree:

Ask which worktree to remove (show list first).

Safety Checks:

1. Ensure it's not the current worktree:

```sh
git worktree list
```

2. Check if the worktree has uncommitted changes:

```sh
cd <worktree-path>
git status
```

If dirty, ask: "This worktree has uncommitted changes. Do you want to discard them or commit first?"

Execute Remove:

```sh
git worktree remove <path>
```

Or with force (if the worktree is locked or broken):

```sh
git worktree remove --force <path>
```

Confirm removal:

```sh
git worktree list
```

### Step 6: Prune Stale Worktrees

If user wants to clean up:

Check for stale or prunable worktrees:

```sh
git worktree prune --dry-run
```

List what would be removed.

Execute prune:

```sh
git worktree prune
```

Confirm cleanup:

```sh
git worktree list
```

### Step 7: Output Status and Next Steps

After any operation, provide clear feedback:

```text
===== WORKTREE STATUS =====
Current: /path/to/worktree (branch: feature/auth)
Active Worktrees:
  /path/to/main (main)
  /path/to/feature/auth (feature/auth)
  /path/to/experiment (experiment)

Next Steps:
- Work on branch: edit files, commit, push
- Switch worktree: cd <path> or use /worktree switch
- Clean up: /worktree remove <path> when done
===== END STATUS =====
```

---

## Key Invariants

- Always check current worktree state before operating.
- MUST NOT remove the current worktree.
- Verify branches exist before creating worktrees.
- Ensure clean working state before creating new worktrees.
- Use `git worktree list` to show active worktrees after each operation.
- Provide clear paths and branch names in output.
- Ask user confirmation before destructive operations (remove, force).

---

## Common Patterns

Parallel feature development:

```sh
git worktree add ../feature-b -b feature/b main
cd ../feature-b
# Work on feature/b in isolation
# Main worktree still at main
```

Bug fix while on feature:

```sh
git worktree add ../hotfix -b hotfix/urgent main
cd ../hotfix
# Fix bug, commit, push
git worktree remove ../hotfix
```

Experiment isolation:

```sh
git worktree add ../experiment -b exp/new-idea main
cd ../experiment
# Try idea; if it works, keep it; if not, remove it
```

---

## Scope Boundaries

This command focuses on worktree lifecycle operations. It does NOT:

- Commit changes (user does that per branch)
- Push branches (refer to `git push`)
- Merge branches (user does that via PR/MR or manual merge)
- Resolve conflicts (refer to conflict-resolution guidance)

For commit and PR workflows, refer the user to the `/workspace-commit` and `/workspace-pr` commands.
