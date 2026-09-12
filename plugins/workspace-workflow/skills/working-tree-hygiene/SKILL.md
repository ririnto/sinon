---
name: working-tree-hygiene
description: >-
  Inspect, maintain, and verify clean working trees before starting work or integrateing changes.
  Triggers on staged or unstaged status checks, stash operations, untracked file classification, upstream sync verification, or baseline hygiene state establishment before or after a task.
---

# Working Tree Hygiene

## Goal

Establish and maintain the discipline of clean, predictable working trees.
A clean working tree is one with all changes committed or stashed, all branches in a known sync state with their upstreams, and no untracked files blocking work.
This skill covers the inspection, classification, and remediation steps that occur before starting a task and after completing one.

## Common-Case Workflow

1. Inspect the current working tree status to understand what changes exist.
2. Classify changes as staged, unstaged, or untracked.
3. Decide whether to stash, commit, or ignore each class of change.
4. Verify the branch is in sync with its upstream (ahead, behind, diverged, or even).
5. Establish a clean baseline: no staged changes, no unstaged changes, no blocking untracked files.
6. Confirm the branch is push-able before integrateing.

## Operating Rules

The following invariants ensure safe, reproducible working trees:

- Establish a known starting state: inspect `git status` and accept a clean tree or an intentional baseline.
  - A read-only task may run without a clean tree.
  - A risky ref transition (merge, rebase, checkout, reset, branch switch) requires a clean tree, isolation (stash or worktree), or an explicitly preserved change path.
  - Never discard work.
- Keep staged and unstaged changes distinct when deciding what belongs in a commit.
  - Separate unrelated changes when the repository workflow calls for focused commits.
- Check branch sync before pushing when an upstream exists.
  - Fetch current upstream state before integration work when a remote exists.
  - Resolve behind or diverged state before integrateing.
- Classify untracked files and decide whether to commit, ignore, or remove each one.
- Use stashing when temporary isolation helps without creating a commit.
  Isolation, not mandatory stashing of unrelated work, solves a dirty tree.
- integrate only the intended committed state: never integrate from a tree with uncommitted or blocking untracked changes.

## Procedure: Inspect Working Tree Status

Establish the baseline status:

```sh
git status
git status -s -b
```

Verify the output names the intended branch, shows the sync state against the upstream (even, ahead, behind, or diverged), and classifies every change as staged, unstaged, or untracked.
Behind or diverged state must be resolved before integrateing.
A clean tree reports `nothing to commit, working tree clean`.

## Procedure: Inspect Staged and Unstaged Changes

View the diff of staged (ready-to-commit) changes and of unstaged changes:

```sh
git diff --cached
git diff
```

Use this to verify that staged changes match your commit intent before running `git commit`.

If unstaged changes belong in the current commit, stage them with `git add <file>`.
If they belong in a separate commit or should be temporarily shelved, stash them.

## Procedure: Stash Changes Temporarily

```sh
git stash          # set aside changes without committing
git stash list     # inspect saved stashes
git stash pop      # restore the most recent stash and remove it
git stash apply stash@{0}   # restore without removing
git stash drop stash@{0}    # remove one stash without applying
```

Stashes are not restored automatically.
Remember to apply them when returning to the context.

## Procedure: Classify and Handle Untracked Files

List untracked files:

```sh
git status --porcelain
```

Output (untracked files start with `??`):

```text
 M src/Main.java
?? build/
?? .DS_Store
```

### Decision: Commit, ignore, or delete?

For each untracked file:

1. Commit if it is part of the source tree and should be tracked by everyone:

    ```sh
    git add <file>
    ```

1. Ignore if it is a build artifact or local file that should never be tracked:

    ```sh
    echo "<pattern>" >> .gitignore
    git add .gitignore
    ```

   Examples of patterns:

    ```text
    build/
    *.pyc
    .DS_Store
    target/
    node_modules/
    ```

1. Delete if it is temporary and not needed:

    ```sh
    rm <file>
    ```

## Procedure: Establish a Clean Baseline

Before starting a task, ensure a known-clean state:

1. Check status:

    ```sh
    git status
    ```

1. If staged changes exist that you do not intend to commit, unstage them:

    ```sh
    git restore --staged <file>
    ```

1. If unstaged changes exist, decide:
   - Commit them:

     ```sh
     git add <file>
     git commit -m "type(scope): describe change"
     ```

   - Stash them:

     ```sh
     git stash
     ```

   - Or discard them:

     ```sh
     git restore <file>
     ```

1. If untracked files block work (e.g., build artifacts), delete or ignore them:

    ```sh
    rm <file>
    echo "<pattern>" >> .gitignore
    ```

1. Verify the final state:

    ```sh
    git status
    ```

   Expected:

    ```text
    On branch main
    Your branch is up to date with 'origin/main'.

    nothing to commit, working tree clean
    ```

## Procedure: Verify Push-Readiness

Before integrateing a branch, confirm it is ready to push:

1. Check the branch is clean:

    ```sh
    git status
    ```

   Expected: "nothing to commit, working tree clean"

1. Check the branch is not behind its upstream:

    ```sh
    git status -s -b
    ```

   Expected: "even" or "[ahead N]" (never "[behind ...]")

1. If behind, pull first:

    ```sh
    git pull
    ```

   Then verify no merge conflicts:

    ```sh
    git status
    ```

   Expected: "nothing to commit" again.

1. If no conflicts and all work is committed, push:

    ```sh
    git push
    ```

## Common Patterns

### Pattern: Interrupt-safe context switch

You are working on feature A but need to switch to feature B urgently:

1. Stash uncommitted work:

    ```sh
    git stash
    ```

1. Switch branches or create a new worktree for feature B.
1. When done with B and ready to resume A, restore:

    ```sh
    git stash pop
    ```

### Pattern: Deferred cleanup

You have untracked build artifacts and `.gitignore` updates:

1. Add to `.gitignore`:

    ```sh
    echo "build/" >> .gitignore
    ```

1. Commit the `.gitignore` change separately:

    ```sh
    git add .gitignore
    git commit -m "chore: ignore build artifacts"
    ```

1. Delete the artifact or run a clean build.

### Pattern: Pre-integration handoff verification

Before creating a change description:

1. Verify status is clean:

    ```sh
    git status
    ```

1. Verify sync state:

    ```sh
    git status -s -b
    ```

1. If behind, pull and re-test:

    ```sh
    git pull
    # Run tests to confirm no regressions from upstream changes
    ```

1. If all checks pass, push:

    ```sh
    git push
    ```

## Pitfalls

- Mixed-purpose commits: Staging both feature work and unrelated cleanup in one commit makes the history harder to bisect.
  - Keep commits focused on one logical unit.
- Forgetting to pull before pushing: If your branch is behind the upstream, your push may fail or require a force-push.
  - Always check `git status -s -b` before pushing.
- Stashing and forgetting: Stashed changes are not automatically restored.
  - If you stash and switch contexts, remember to apply the stash when you return.
  - List stashes occasionally to avoid orphans.
- Untracked files cluttering the tree: If you leave untracked build artifacts or temporary files, they can interfere with branch switching and make the tree look dirtier than it is.
  - Decide consciously: commit, ignore, or delete.
- Partial commits: Never commit half a feature.
  - If you stage only part of a file's changes, the commit may be logically incomplete.
  - Review staged changes with `git diff --cached` before committing.
- Not verifying the branch name before work: If you accidentally work on the wrong branch, you may integrate to the wrong place.
  - Always confirm `git status` shows the intended branch name at the start.

## First Safe Commands

Inspect the working tree and branch sync state, then stage and verify before committing:

```sh
git status
git status -sb
git add <file>
git diff --staged
```

Stash changes temporarily when switching contexts:

```sh
git stash
git stash list
```

## Output Contract

Use the following as recommended defaults.
Follow task, host, and dispatch requirements when they differ.

### `git status` clean state

```text
On branch <branch>
Your branch is up to date with 'origin/<branch>'.

nothing to commit, working tree clean
```

### `git status` with changes

The output lists staged changes, unstaged changes, and untracked files in separate sections under the branch and sync-state line.

### `git diff --cached` and `git diff` output

Standard unified diff, one hunk per file region changed.
`--cached` covers staged intent.
Plain `git diff` covers unstaged working-tree changes.

### `git status -s -b` output

```text
## <branch>...<upstream> [<sync>]
<XY> <file>
```

`<XY>` is a two-letter status code followed by a space and the path.
`X` is the index (staged) status, `Y` is the working-tree (unstaged) status, and `??` marks untracked files.
Read the full code table with `git status --help` when an uncommon code appears.
