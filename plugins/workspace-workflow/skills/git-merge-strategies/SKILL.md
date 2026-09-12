---
name: git-merge-strategies
description: >-
  Merge feature branches into a base branch using appropriate strategies
  (fast-forward, no-ff merge commit, squash, octopus).
  Use when choosing a merge strategy, handling merge conflicts interactively, or automating recurring conflict patterns with rerere.
---

# Git Merge Strategies

Integrate completed feature work into a base branch using the merge strategy that matches your history and conflict-handling requirements.

## Scope

This skill covers:

- Choosing the correct merge strategy (fast-forward, --no-ff, --squash, octopus).
- Executing the merge command safely.
- Handling merge conflicts interactively.
- Using rerere (reuse recorded resolution) to automate recurring conflict patterns.

This skill does not cover:

- Rebase-based integration (use `git rebase` workflows separately).
- Cherry-picking individual commits across branches.
- Undoing commits after merge (covered in working-tree-hygiene).

## Operating Rules

- Preserve uncommitted work before merging: run `git status` before merging.
  - A merge is a ref transition: commit the work first, isolate it (stash or worktree), or name an explicit preserve path.
  - Never discard or overwrite uncommitted work to start a merge.
- Fetch current upstream state before merging when a remote exists: run `git fetch` before merging.
  - A merge with no applicable upstream may skip the fetch.
- Pushed branches MUST NOT be force-pushed after merge: Once a branch is merged and pushed, do not use `git push --force`.
  - Use `git revert` instead.
- Merge commits SHOULD have clear intent: Add `-m` message to merge commits (beyond the default).
  - State the feature and branch name.
- Conflicting files MUST be fully resolved: MUST NOT commit a merge with `<<<<<<<` or `>>>>>>>` markers remaining.

## Decision: Which Merge Strategy?

| Strategy | Command | History shape | Use when | Avoid when |
| --- | --- | --- | --- | --- |
| fast-forward | `git merge {{branch}}` | Linear; no merge commit | Branch is a clean extension of the base; feature is small and clean | Want to preserve feature branch as a named commit; feature has long history |
| --no-ff | `git merge --no-ff {{branch}}` | Tree; explicit merge commit | Want to preserve branch identity; feature is significant; need bisect anchors | Linear history preferred; single-commit features |
| --squash | `git merge --squash {{branch}}` | Linear; combined into one commit | Many small feature commits; want clean history; feature work is experimental | Need to preserve individual commit attribution; long feedback history is valuable |
| octopus | `git merge -s octopus branch1 branch2 ...` | Multi-parent; all branches merged at once | Integrating 3+ parallel features at once; all are complete and non-conflicting | Any conflicts exist; need to debug individual merges; most merges (use for special cases) |

### Quick decision rules

1. Default: Use `--no-ff` unless your project standard specifies otherwise.
   - It preserves feature branch identity without cluttering history.
2. Small hotfix or trivial rebase: Use fast-forward (`git merge {{branch}}`).
   - The feature is a clean linear extension.
3. Experimental or temporary branch: Use `--squash`.
   - Collapse feature work into a single logical commit.
4. Multiple features at once: Use octopus *only* if all feature branches are complete, tested, and non-conflicting.

## Procedure: Pre-Merge Verification

1. Verify the working tree is clean:

    ```sh
    git status
    ```

   Expected: "nothing to commit, working tree clean"

1. Fetch and confirm you are on the target (base) branch and not behind it:

    ```sh
    git fetch origin
    git status -s -b
    ```

   Expected: the current branch line shows the base branch, "even" or "[ahead N]" (never "[behind]").
   If behind, pull first:

    ```sh
    git pull origin <base-branch>
    ```

## Procedure: Execute Merge (Fast-Forward)

Fast-forward merge (suitable for clean, linear feature branches):

```sh
git merge <feature-branch>
```

Example output:

```text
Updating a1b2c3d..e4f5g6h
Fast-forward
 src/feature.js | 10 +++++++++-
 test/feature.test.js | 5 +++++
 2 files changed, 14 insertions(+), 1 deletion(-)
```

Verify the merge:

```sh
git log --oneline -n 5
```

## Procedure: Execute Merge (--no-ff)

Merge with explicit commit (preserves branch identity):

```sh
git merge --no-ff -m "Merge branch '<feature-branch>' into <base-branch>" <feature-branch>
```

This uses the provided merge message without opening an editor.

Example output:

```text
Merge made by the 'ort' strategy.
 src/feature.js | 10 +++++++++-
 test/feature.test.js | 5 +++++
 2 files changed, 14 insertions(+), 1 deletion(-)
```

Verify the merge commit:

```sh
git log --oneline -n 5
```

Expected: Newest entry shows "Merge branch ..." with two parents.

## Procedure: Execute Merge (--squash)

Merge and stage all changes as one commit (without auto-committing):

```sh
git merge --squash <feature-branch>
```

Example output:

```text
Squash commit -- not updating HEAD
 src/feature.js | 10 +++++++++-
 test/feature.test.js | 5 +++++
 2 files changed, 14 insertions(+), 1 deletion(-)
```

Review the staged changes:

```sh
git diff --cached
```

Commit with a clear message:

```sh
git commit -m "feat: add feature-branch functionality"
```

Verify:

```sh
git log --oneline -n 5
```

Expected: One new commit with all feature work combined.

## Procedure: Handle Merge Conflicts

When a merge encounters conflicts, Git pauses:

```text
Auto-merging src/module.js
CONFLICT (content): Merge conflict in src/module.js
Automatic merge failed; fix conflicts and then commit the result.
```

### Step 1: Inspect conflict status

```sh
git status
```

Example:

```text
On branch main
You have unmerged paths.
  (fix conflicts and run "git commit")
  (use "git merge --abort" to abort the merge)

both modified: src/module.js
```

### Step 2: View conflicted file

```sh
cat src/module.js
```

Example (conflict markers):

```text
function greet(name) {
<<<<<<< HEAD
  return "Hello, " + name;
=======
  return "Hi, " + name + "!";
>>>>>>> feature-branch
}
```

### Step 3: Resolve conflict manually

Edit the file to keep the correct version or combine both versions.
Remove all conflict markers after editing.

### Step 4: Stage resolved file

```sh
git add src/module.js
```

### Step 5: Complete the merge

After all conflicts are resolved and staged:

```sh
git commit
```

This opens your editor.
Accept the default merge message or edit it:

```text
Merge branch 'feature-branch' into main
```

Save and exit.

### Step 6: Verify merge completion

```sh
git status
```

Expected:

```text
On branch main
nothing to commit, working tree clean
```

Verify history:

```sh
git log --oneline -n 5
```

## Conflict Resolution Patterns

### Pattern: Same line edited differently

File: `src/config.js`

```js
const API_TIMEOUT = 3000;  // base
const API_TIMEOUT = 5000;  // feature
```

Resolution: Decide which value is correct or use a compromise.
Remove conflict markers and keep one value.

### Pattern: One branch deleted, other branch modified

File: `src/old_module.js`

Status: Base branch deleted the file.
Feature branch modified it.

Git reports a path-level modify/delete conflict and keeps the modified file in the working tree:

```text
CONFLICT (modify/delete): src/old_module.js deleted in HEAD and modified in feature-branch. Version feature-branch of src/old_module.js left in tree.
```

Resolution: Decide:

- To delete (resolve by doing nothing, then `git rm src/old_module.js`):

    ```sh
    git rm src/old_module.js
    git add -u
    ```

- To keep (resolve by keeping the modifications):

    ```sh
    git checkout --theirs src/old_module.js
    git add src/old_module.js
    ```

### Pattern: Rename and edit same file

File: Renamed in base branch.
Content edited in feature branch.

Status: Git detects rename + content conflict.

Resolution:

- Accept the rename from base and manually apply feature edits:

    ```sh
    git add <renamed-file>
    ```

- Then edit to re-apply feature changes.

### Pattern: Binary file conflict

File: `image.png` or `archive.bin`

Status: Both branches changed the binary file.

Resolution: Git cannot auto-merge binaries.
Choose the current branch version:

```sh
git checkout --ours image.png
git add image.png
```

Choose the incoming branch version:

```sh
git checkout --theirs image.png
git add image.png
```

## Rerere: Reuse Recorded Resolution

For recurring conflicts (e.g., repeated merges in long-lived branches or complex dependency files), use `git rerere` to cache and replay resolutions automatically.

### Enable rerere

One-time setup:

```sh
git config rerere.enabled true
```

Make it persistent globally:

```sh
git config --global rerere.enabled true
```

### How rerere works

1. When you resolve a conflict, Git records the conflict pattern (pre-resolve) and your resolution (post-resolve).
2. On future merges, if the same conflict pattern is detected, Git automatically applies the cached resolution.
3. You still must review and commit.
   - Rerere just saves the manual editing step.

### Rerere cache maintenance

```sh
git rerere list                       # hash IDs of recorded conflicts
git rerere forget <hash>              # remove one outdated cached resolution
git rerere clear                      # remove all cached resolutions
```

Cached conflict patterns are stored in `.git/rr-cache/`.
Each directory represents one recorded conflict.

## Pitfalls

- Aborting mid-merge: Use `git merge --abort` to cancel a merge cleanly.
  - MUST NOT use `git reset --merge` unless you understand the difference (reset is destructive and can lose work).
- Committing with conflict markers: Always verify conflict markers are removed before committing.
  - Search for `<<<<<<<` and `=======` in all files.
- Force-pushing after merge: Never use `git push --force` after merging into a published branch.
  - Use `git revert` to undo bad merges instead.
- Stale branch after merge: After a successful merge, the feature branch still exists locally and remotely.
  - Delete it explicitly:

    ```sh
    git branch -d <feature-branch>
    git push origin --delete <feature-branch>
    ```

- Missing rerere auto-apply: If rerere is enabled but a cached resolution was not applied, manually check the conflict and apply it, or disable rerere temporarily and resolve again.

## First Safe Commands

Before merging, verify state:

```sh
git status
git fetch origin
git status -s -b
```

Execute merge with `--no-ff` (safest default):

```sh
git merge --no-ff -m "Merge branch '<feature-branch>' into <base-branch>" <feature-branch>
```

If conflicts occur, resolve manually, then:

```sh
git add <file>
git commit
```

If merge goes wrong, abort:

```sh
git merge --abort
```

## Output Contract

Use the following as recommended defaults.
Follow task, host, and dispatch requirements when they differ.

### Successful merge (no conflicts)

A useful report typically includes:

1. Merge command used and output (for example, "Fast-forward" or "Merge made by the 'ort' strategy").
2. Commit hash of the merge, or the most recent commit when fast-forwarded.
3. Files changed, using `git diff --stat {{base-branch}}^..{{branch}}` when applicable.
4. Verification from `git log --oneline -n 3`.

### Merge with conflicts

A useful report typically includes:

1. Conflicted files from `git status`.
2. Resolution strategy, including changes kept, combined, or deleted.
3. Verification from `git status` and `git log --oneline -n 3`.
4. Manual edits or mergetool usage, when applicable.

### Cleanup after merge

When cleanup is part of the task, report:

1. Merge commit hash.
2. Branch deletion commands, if authorized:

    ```sh
    git branch -d <feature-branch>
    git push origin --delete <feature-branch>
    ```

3. Final state from `git log --graph --oneline -n 10`.
