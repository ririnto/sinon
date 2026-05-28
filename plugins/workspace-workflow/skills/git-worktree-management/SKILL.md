---
name: git-worktree-management
description: >-
  Create and manage isolated git worktrees to work on multiple branches in parallel without switching HEAD. Use when adding, listing, pruning, or removing worktrees, checking branch-to-worktree bindings, or reasoning about worktree isolation before parallel task setup.
---

# Git Worktree Management

## Goal

Create isolated working directories tied to separate branches, allowing parallel development without changing the main repository HEAD. Each worktree is a cloned directory tree with its own staging area, working directory, and index but shares the object database and refs with the base repository.

## Common-Case Workflow

1. List existing worktrees to see which branches are currently active.
2. Decide whether to create a new worktree or reuse an existing one.
3. Create a worktree tied to a specific branch or commit.
4. Verify the new worktree is correctly bound and isolated.
5. Work inside the worktree directory independently from the base repository.
6. Remove or repair worktrees when work is complete or cleanup is needed.

## Operating Rules

The following invariants govern safe worktree use:

- **One worktree per branch**: The same branch MUST NOT be checked out in two worktrees or in the base repository simultaneously. Violation results in HEAD pointer conflicts and data loss risk.
- **Base repo independence**: The base repository (where you ran `git worktree add`) MUST remain a valid working tree. It MUST NOT be converted or reserved for listing only.
- **Shared object store**: All worktrees MUST share the same `.git/objects/` store; deleting a worktree MUST NOT delete commits.
- **Isolation**: Changes in one worktree MUST NOT automatically appear in others until committed and fetched.
- **No nested worktrees**: Worktrees MUST NOT be nested inside each other or inside the base repository directory.

## Procedure: Create an Isolated Worktree

### Setup: First-time worktree structure (optional convention)

Create a `worktrees/` directory at the repository root to co-locate all worktrees:

```sh
mkdir -p worktrees
```

This is a recommended convention but not required. Worktrees can live anywhere.

### Create a new worktree from a branch

To create a worktree for an existing local branch:

```sh
git worktree add worktrees/<branch-name> <branch-name>
```

Example:

```sh
git worktree add worktrees/feat-auth feat-auth
```

### Create a worktree from a remote-tracking branch

If the branch exists only on the remote:

```sh
git worktree add worktrees/<branch-name> <remote>/<branch-name>
```

Example:

```sh
git worktree add worktrees/feat-ui origin/feat-ui
```

### Create a worktree with automatic branch tracking

Use the `--track` flag to set up tracking to the remote:

```sh
git worktree add --track worktrees/<branch-name> <remote>/<branch-name>
```

### Create a worktree from a commit (detached HEAD)

To work in a worktree on a specific commit without a branch:

```sh
git worktree add --detach worktrees/<worktree-name> <commit-sha>
```

### Create a worktree with a new branch

To create both a worktree and a new branch in one step:

```sh
git worktree add worktrees/<new-branch> -b <new-branch> <base-commit>
```

Example:

```sh
git worktree add worktrees/hotfix-123 -b hotfix-123 main
```

### First-time verification

After creating a worktree, verify the isolation:

```sh
git worktree list
cd worktrees/<branch-name>
git status
git branch -v
```

Expected output: the worktree is on the target branch, the working tree is clean or shows only local changes, and the branch list shows correct tracking.

## Procedure: Inspect Active Worktrees

List all worktrees including the main repository:

```sh
git worktree list
```

Example output:

```text
/path/to/repo              abcdef1 [main]
/path/to/repo/worktrees/feat-auth  1a2b3c4 [feat-auth] (branch:feat-auth)
/path/to/repo/worktrees/hotfix-123  5e6f7a8 [hotfix-123] (branch:hotfix-123)
```

Columns:

- Path to worktree directory
- Current HEAD commit SHA
- Current branch name (in brackets) or detach status
- Extra metadata (branch lock, detach reason, etc.)

## Procedure: Remove a Worktree

### Safe removal sequence

1. Navigate out of the worktree directory.
2. Ensure all changes in the worktree are committed or stashed.
3. Remove the worktree:

```sh
git worktree remove worktrees/<branch-name>
```

### What `remove` does

- Deletes the worktree directory from the filesystem.
- Clears the internal worktree reference.
- Does **not** delete the branch itself or any commits.

### Restoring the branch after worktree removal

If you removed the worktree but want to keep working on the branch, recreate the worktree:

```sh
git worktree add worktrees/<branch-name> <branch-name>
```

## Procedure: Repair Stale Worktree References

Over time, orphaned worktree metadata may accumulate (e.g., if a worktree directory was deleted without using `git worktree remove`).

Prune stale references:

```sh
git worktree prune
```

This removes internal worktree references whose directories no longer exist.

Verify cleanup:

```sh
git worktree list
```

Expected: only paths that exist on disk are listed.

## Procedure: Verify No Branch Conflicts

Before starting work in a new worktree, confirm that the target branch is not already checked out elsewhere:

```sh
git worktree list | grep <branch-name>
```

Expected: exactly one line, pointing to the correct worktree.

If the same branch appears twice, the second `git worktree add` will fail with an error. Remove the old worktree first or use a different branch.

## Common Patterns

### Pattern: Parallel feature work

1. Create a worktree for each feature branch:

```sh
git worktree add worktrees/feat-a feat-a
git worktree add worktrees/feat-b feat-b
```

1. Work independently in each worktree:

```sh
cd worktrees/feat-a
# Make changes, commit, test
cd ../feat-b
# Make different changes, commit, test
```

1. Push each branch independently:

```sh
cd worktrees/feat-a && git push
cd ../feat-b && git push
```

1. Clean up when done:

```sh
cd ../../
git worktree remove worktrees/feat-a
git worktree remove worktrees/feat-b
```

### Pattern: Spike + main branch isolation

Keep the main branch untouched while spiking in a worktree:

```sh
git worktree add worktrees/spike-new-api -b spike-new-api main
cd worktrees/spike-new-api
# Experiment freely; main branch remains clean
cd ../../
git worktree remove worktrees/spike-new-api
```

### Pattern: Hotfix while feature work is in progress

1. Feature work ongoing:

```sh
cd worktrees/feat-main-work
# Long-running feature development
```

1. Hotfix urgent production issue in a separate worktree:

```sh
# From another terminal or after exiting the feature worktree
git worktree add worktrees/hotfix-prod-bug -b hotfix-prod-bug main
cd worktrees/hotfix-prod-bug
# Fix, test, commit
git push
# Then create PR/MR
cd ../../
```

1. Resume feature work:

```sh
cd worktrees/feat-main-work
# Continue where you left off
```

## Pitfalls

- **Same branch in two worktrees**: If you accidentally create a worktree for a branch already checked out elsewhere, `git worktree add` fails with "error: {{branch}} is already checked out". Use `git worktree list` before adding.
- **Forgetting to exit the worktree before removing**: If you try to remove a worktree while inside it, removal fails. Always `cd` out first.
- **Stale worktree metadata**: If you delete a worktree directory manually (not via `git worktree remove`), stale metadata remains. Use `git worktree prune` to clean up.
- **Nested worktrees**: Do not create a worktree inside another worktree's directory. Keep worktrees as siblings in a flat structure (e.g., `worktrees/branch-a`, `worktrees/branch-b`).
- **Base repository as a second-class worktree**: The base repository is a full worktree and can be used for development. Do not treat it as "reserved for listing only".
- **Assuming isolation without commits**: Changes in a worktree are isolated at the filesystem level but remain local. They do not appear in other worktrees until committed and (optionally) pushed and fetched by other worktrees.

## First Safe Commands

Before creating or managing worktrees, verify the current state:

```sh
git status
git worktree list
```

Create a worktree with tracking:

```sh
git worktree add worktrees/<branch-name> <branch-name>
```

Remove a worktree safely:

```sh
git worktree remove worktrees/<branch-name>
```

## Output Contract

### `git worktree list` output shape

```text
<path>  <sha>  [<branch>]  (<metadata>)
<path>  <sha>  [<branch>]  (<metadata>)
```

- Each line represents one worktree.
- Path is absolute.
- SHA is the current HEAD commit hash (short form, typically 7 chars).
- Branch is the current branch name, enclosed in `[...]`.
- Metadata is optional and includes lock status, detach reason, etc.

### `git worktree add` success output

```text
Preparing worktree (new branch)
Checking out branch '<branch>'
```

or

```text
Preparing worktree (checking out '<branch>')
HEAD is now at <sha> <commit-message>
```

If no output appears, the worktree was created successfully.

### `git worktree add` failure output

```text
fatal: <branch> is already checked out at '<path>'
```

This means the branch is already active in another worktree or the base repository. Choose a different branch or remove the conflicting worktree first.

### `git worktree remove` output

No output on success. On failure:

```text
fatal: <path> is not a worktree
```
