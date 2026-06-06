---
name: working-tree-hygiene
description: >-
  Inspect, maintain, and verify clean working trees before starting work or publishing changes. Triggers on staged or unstaged status checks, stash operations, untracked file classification, upstream sync verification, or baseline hygiene state establishment before or after a task.
---

# Working Tree Hygiene

## Goal

Establish and maintain the discipline of clean, predictable working trees. A clean working tree is one with all changes committed or stashed, all branches in a known sync state with their upstreams, and no untracked files blocking work. This skill covers the inspection, classification, and remediation steps that occur before starting a task and after completing one.

## Common-Case Workflow

1. Inspect the current working tree status to understand what changes exist.
2. Classify changes as staged, unstaged, or untracked.
3. Decide whether to stash, commit, or ignore each class of change.
4. Verify the branch is in sync with its upstream (ahead, behind, diverged, or even).
5. Establish a clean baseline: no staged changes, no unstaged changes, no blocking untracked files.
6. Confirm the branch is push-able before publishing.

## Operating Rules

The following invariants ensure safe, reproducible working trees:

- **Clean start**: Before starting a task in a working tree, you MUST verify `git status` shows "nothing to commit, working tree clean" or an acceptable baseline state (e.g., intentional untracked build artifacts in `.gitignore`).
- **Staged/unstaged separation**: You MUST know the difference between staged changes (ready to commit) and unstaged changes (not yet decided). You MUST NOT mix unrelated changes in a single commit.
- **Branch sync**: Before pushing, you MUST verify the branch is even with or ahead of its upstream. A branch that is behind its upstream before you push will fail or cause unexpected merge commits.
- **Untracked discipline**: Untracked files are ignored by Git but can clutter the tree. You SHOULD consciously decide whether to commit, add to `.gitignore`, or delete each untracked file.
- **Stash as isolation tool**: You SHOULD use stashing to temporarily set aside work without committing it, allowing you to switch contexts or verify a clean state.
- **No partial publication**: You MUST NOT push a branch that is not clean at the tip (all work committed, nothing staged, no blocking untracked files).

## Procedure: Inspect Working Tree Status

Establish the baseline status:

```sh
git status
```

Example output:

```text
On branch main
Your branch is up to date with 'origin/main'.

Changes to be committed:
  (use "git restore --staged <file>..." to unstage)
 modified:   src/Main.java

Changes not staged for commit:
  (use "git restore <file>..." to discard changes in working directory)
 modified:   src/Helper.java

Untracked files:
  (use "git ls-files --others --exclude-standard")
 build/
 .DS_Store
```

### Interpretation

| Output section | Meaning | Next action |
| --- | --- | --- |
| `On branch {{branch}}` | Current branch name | Verify this is the intended branch. |
| `Your branch is up to date with 'origin/{{branch}}'` | Branch is even with upstream. | Safe to work; no forced merge needed on pull. |
| `Your branch is ahead of 'origin/{{branch}}' by N commits` | Unmerged commits exist locally. | Push or understand why they exist. |
| `Your branch is behind 'origin/{{branch}}' by N commits` | Upstream has unmerged commits. | Pull to sync before pushing new work. |
| `Changes to be committed:` | Staged changes await commit. | Decide whether to commit or unstage. |
| `Changes not staged for commit:` | Unstaged changes exist. | Decide whether to stage and commit or stash. |
| `Untracked files:` | New files not tracked by Git. | Decide whether to commit, ignore, or delete. |
| `nothing to commit, working tree clean` | No uncommitted or untracked changes. | Safe to switch branches or push. |

## Procedure: Inspect Staged Changes

View the diff of staged (ready-to-commit) changes:

```sh
git diff --cached
```

or more concisely:

```sh
git diff --staged
```

Example output:

```text
diff --git a/src/Main.java b/src/Main.java
index a1b2c3d..e4f5g6h 100644
--- a/src/Main.java
+++ b/src/Main.java
@@ -10,6 +10,10 @@ public class Main {
   public static void main(String[] args) {
       System.out.println("Hello");
+      System.out.println("World");
   }
 }
```

Use this to verify that staged changes match your commit intent before running `git commit`.

## Procedure: Inspect Unstaged Changes

View the diff of unstaged (not yet staged) changes:

```sh
git diff
```

Example output:

```text
diff --git a/src/Helper.java b/src/Helper.java
index x1y2z3a..m1n2o3p 100644
--- a/src/Helper.java
+++ b/src/Helper.java
@@ -5,8 +5,10 @@ public class Helper {
   public static String format(String s) {
-      return s.trim();
+      return s.trim().toLowerCase();
   }
 }
```

### Decision: Stage or stash?

If unstaged changes belong in the current commit, stage them:

```sh
git add src/Helper.java
```

If they belong in a separate commit or should be temporarily shelved, stash them:

```sh
git stash
```

## Procedure: Check Branch Sync State

Verify the relationship between your branch and its upstream:

```sh
git status -s -b
```

Output:

```text
## main...origin/main
M  src/Main.java
?? build/
```

The first line shows the sync state:

| Sync state | Meaning | Action |
| --- | --- | --- |
| `## main...origin/main` | Even (no commits ahead or behind). | Safe to work; ready to push when done. |
| `## main...origin/main [ahead N]` | N commits ahead of upstream. | Push to share your work; upstream has not changed. |
| `## main...origin/main [behind N]` | N commits behind upstream. | Pull to sync before continuing; upstream has new work. |
| `## main...origin/main [ahead N, behind M]` | Diverged (both have unmerged commits). | Pull first to merge upstream changes, then push. |

## Procedure: Stash Changes Temporarily

Use stashing to set aside changes without committing:

```sh
git stash
```

Output:

```text
Saved working directory and index state WIP on main: a1b2c3d Last commit message
```

### List stashed changes

```sh
git stash list
```

Output:

```text
stash@{0}: WIP on main: a1b2c3d Last commit message
stash@{1}: WIP on feat-auth: e4f5g6h Add authentication
```

### Restore stashed changes

Pop the most recent stash (and remove it from the stash list):

```sh
git stash pop
```

or apply without removing:

```sh
git stash apply stash@{0}
```

### Drop a stash

Remove a stash without applying it:

```sh
git stash drop stash@{0}
```

Clear all stashes:

```sh
git stash clear
```

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

1. **Commit** if it is part of the source tree and should be tracked by everyone:

```sh
git add <file>
```

1. **Ignore** if it is a build artifact or local file that should never be tracked:

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

1. **Delete** if it is temporary and not needed:

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

Before publishing a branch, confirm it is ready to push:

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
2. When done with B and ready to resume A, restore:

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

### Pattern: Pre-publication verification

Before creating a pull request or merge request:

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

- **Mixed-purpose commits**: Staging both feature work and unrelated cleanup in one commit makes the history harder to bisect. Keep commits focused on one logical unit.
- **Forgetting to pull before pushing**: If your branch is behind the upstream, your push may fail or require a force-push. Always check `git status -s -b` before pushing.
- **Stashing and forgetting**: Stashed changes are not automatically restored. If you stash and switch contexts, remember to apply the stash when you return. List stashes occasionally to avoid orphans.
- **Untracked files cluttering the tree**: If you leave untracked build artifacts or temporary files, they can interfere with branch switching and make the tree look dirtier than it is. Decide consciously: commit, ignore, or delete.
- **Partial commits**: Never commit half a feature. If you stage only part of a file's changes, the commit may be logically incomplete. Review staged changes with `git diff --cached` before committing.
- **Not verifying the branch name before work**: If you accidentally work on the wrong branch, you may publish to the wrong place. Always confirm `git status` shows the intended branch name at the start.

## First Safe Commands

Inspect the working tree and branch sync state:

```sh
git status
git status -sb
```

Stage changes and verify before committing:

```sh
git add <file>
git diff --staged
```

Stash changes temporarily when switching contexts:

```sh
git stash
git stash list
```

## Output Contract

### `git status` clean state

```text
On branch <branch>
Your branch is up to date with 'origin/<branch>'.

nothing to commit, working tree clean
```

### `git status` with changes

```text
On branch <branch>
Your branch is <sync-state>.

Changes to be committed:
  <staged-file-list>

Changes not staged for commit:
  <unstaged-file-list>

Untracked files:
  <untracked-file-list>
```

### `git diff --cached` output

```text
diff --git a/<path> b/<path>
index <sha1>..<sha2> <mode>
--- a/<path>
+++ b/<path>
@@ -<old-line>,<count> +<new-line>,<count> @@
<context-and-changes>
```

One hunk per file region changed. Use this to verify staged intent before committing.

### `git diff` output

Same format as `--cached` but shows unstaged (working directory) changes instead.

### `git status -s -b` output

```text
## <branch>...<upstream> [<sync>]
<porcelain-status> <file>
<porcelain-status> <file>
```

Porcelain status codes:

- `M` = modified, unstaged
- `M` = modified, staged
- `??` = untracked
- `MM` = modified staged and unstaged
