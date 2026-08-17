---
name: git-rebase-strategies
description: >-
  Rebase feature branches with interactive editing, autosquash, and selective reapplication while protecting shared history.
  Use when linearizing history, replaying commits onto a new base with `--onto`, resolving mid-rebase conflicts, or deciding whether rebase or merge is safer for a shared branch.
---

# Git Rebase Strategies

Rebase a feature branch to linearize history, clean up commits, or replay changes onto a new base while preserving commit authorship and avoiding data loss on shared branches.

## Goal

Successfully rebase a branch without losing commits, corrupting history, or forcing unwanted changes on teammates.
Understand when rebase is appropriate and when merge is safer.

## Scope

This skill covers:

- Interactive rebasing with commit editing, reordering, and squashing
- Autosquash workflows for fixup commits
- Replaying branches onto new bases with `--onto`
- Conflict resolution during rebase
- Abort and recovery from mid-rebase failures
- Safe force-push decisions

This skill does not cover:

- General Git branching or merge workflows
- Distributed rebase workflows across teams
- Recovering commits lost to hard resets or reflog expiration

## Operating Rules

These invariants govern safe rebase practice:

- Working tree MUST be clean before starting a rebase.
  - Commit or stash all changes.
- Shared branches SHOULD NOT be rebased unless the entire team agrees and understands force-push implications.
  - Rebasing shared history and then force-pushing will erase commits from teammates' local branches.
- Force-push MUST only be used with `--force-with-lease` to prevent overwriting remote changes you have not seen.
  - Bare `--force` is unsafe on branches others might push to simultaneously.
- Commit references (SHAs) change on rebase.
  - Update any references to old commits in tickets, PRs, or scripts.

## Rebase and Merge Decision

Rebase is appropriate when:

- The branch is local and not yet pushed (no risk to teammates)
- Cleaning up commit history makes the main branch easier to read
- You want a linear history without merge commits
- The branch is short-lived and will be squashed before merge anyway

Merge is safer when:

- The branch is shared or others have pulled from it
- Preserving the exact branching point matters for blame or history archaeology
- The team workflow uses merge commits as explicit integration points
- You want to preserve all intermediate commits exactly as they were

| Scenario | Preferred | Reason |
| --- | --- | --- |
| Local feature branch before first push | Rebase | Cleaning history is safe when no one else has the commits |
| Already-pushed branch with no other users | `--force-with-lease` + rebase | Safe if you own the branch; prevents accidental overwrites |
| Branch shared with teammates | Merge | Rebase will erase their commits; force-push will break their checkouts |
| Quick fixup to own feature branch | Rebase + squash | Interactive rebase is cleaner than a fixup commit |
| Catching up to main while preserving own work | `git rebase origin/main` | Replays your commits on top of updated main |

## Procedure

### 1. Before starting: Fetch and verify state

Always ensure your local view of the remote is current:

```sh
git fetch origin
git status
```

Verify the branch is in a clean state:

```sh
git diff
git diff --cached
```

If there are uncommitted changes, stash or commit them:

```sh
git stash
```

### 2. Choose the rebase mode

#### Simple interactive rebase (reorder, squash, or edit commits)

Rebase the last 5 commits:

```sh
git rebase -i HEAD~5
```

An editor opens with a list of commits.
Commands available:

- `pick` - keep the commit as-is (default)
- `reword` - keep the commit but edit the message
- `squash` - combine this commit with the previous one
- `fixup` - like squash but discard this commit's message
- `drop` - remove the commit entirely
- `edit` - pause at this commit to amend or modify

Example workflow:

```text
pick a1b2c3d Add user authentication
reword 2d3e4f5 Fix typo in user form
squash 3e4f5g6 Refactor user service (combine into previous)
drop 4f5g6h7 Debug: temporary logging
pick 5g6h7i8 Add password validation
```

After editing, save and exit the editor.
Git applies each commit in order.

#### Autosquash workflow (automatic fixup grouping)

Use `--fixup` or `--squash` when committing to mark commits for automatic reordering:

```sh
git commit --fixup a1b2c3d
```

This creates a commit named `fixup! <message of a1b2c3d>`.

Then run rebase with `--autosquash`:

```sh
git rebase -i --autosquash origin/main
```

Git automatically moves fixup commits below their target and marks them for squashing.

#### Replay branch onto new base (--onto)

Rebase commits from one branch point to another:

```sh
git rebase --onto <new-base> <old-base> <branch>
```

Example: Move the last 3 commits from the current branch onto a different commit:

```sh
git rebase --onto origin/main HEAD~3
```

This replays commits between `HEAD~3` and `HEAD` on top of `origin/main`.

### 3. Handle conflicts

If a conflict occurs, Git pauses and marks conflicted files:

```sh
git status
```

Edit each conflicted file to resolve the conflict.
In a rebase, `HEAD` is the rebased/upstream result, and `>>>>>>> commit-message` marks the commit being replayed (the rebase-specific ours/theirs sides are reversed):

```text
<<<<<<< HEAD
upstream result
=======
commit being replayed
>>>>>>> commit-message
```

Remove the markers and keep the code you want.
Then stage the resolved file:

```sh
git add <file>
```

After resolving all conflicts, continue the rebase:

```sh
git rebase --continue
```

If the conflict is too complex, abort and restart:

```sh
git rebase --abort
```

### 4. Post-rebase validation

After rebase completes, verify the new history:

```sh
git log --oneline -n 20
```

Check that commits are in the expected order with correct messages.

## Templates and Examples

### Interactive rebase: squash commits before merge

Scenario: Local feature branch with messy history before merging to main.

```sh
git fetch origin
git rebase -i origin/main
```

Mark commits to squash:

```text
pick a1b2c3d Add user profile feature
squash 2d3e4f5 WIP: refactor validation
squash 3e4f5g6 Fix tests
pick 4f5g6h7 Add profile documentation
```

Save.
Reword the squashed commit message if needed.

### Autosquash workflow: fixup then rebase

Scenario: Noticed a typo in an earlier commit.
Fixing it with a fixup commit.

Commit the fix with `--fixup`:

```sh
git commit --fixup a1b2c3d
```

Run autosquash rebase:

```sh
git rebase -i --autosquash HEAD~10
```

The fixup commit is automatically placed and squashed.

### Rebase onto new base: catch up to main

Scenario: Feature branch is behind main.
Want to replay changes on top of latest main.

```sh
git fetch origin
git rebase origin/main
```

This replays all commits from the merge-base to HEAD on top of `origin/main`.

### Safe force-push after rebase

After a successful rebase of a branch only you have pushed:

```sh
git push --force-with-lease origin <branch-name>
```

`--force-with-lease` prevents overwriting commits if the remote has changed since your last fetch.
Bare `--force` is dangerous.

## Pitfalls

### --force and --force-with-lease

- `git push --force` overwrites the remote unconditionally, even if someone else pushed commits.
  - MUST NOT use.
- `git push --force-with-lease` overwrites only if the remote ref matches your last fetched state, protecting against accidental overwrites.
  - Always prefer this.

### Rebasing shared branches

If a branch is already pushed and other developers have pulled it, rebasing and force-pushing will:

- Erase commits from teammates' local branches
- Break their checkouts if they were on that branch
- Make it impossible to merge their work cleanly

MUST NOT rebase shared branches without explicit team agreement and communication.

### Editing history of commits already pushed

After a force-push following rebase, commit SHAs change.
If the commits are referenced in:

- Pull request comments or descriptions
- Ticket systems or issue trackers
- Commit links in documentation or chat

Those references will become invalid.
Communicate the change to affected parties.

## First Safe Commands

Start here for the common case:

```sh
git fetch origin
git status
git rebase -i HEAD~5
```

Edit the rebase script in your editor.
Save and exit.

For catching up a local branch to main:

```sh
git fetch origin
git rebase origin/main
```

For safe force-push after local rebase:

```sh
git push --force-with-lease origin <branch-name>
```

## Abort and Recovery

### Abort a rebase in progress

If a rebase goes wrong, stop immediately:

```sh
git rebase --abort
```

This returns to the state before rebase started.

### Recover from mistaken rebase

If you completed a rebase but realize it was wrong, use reflog to find the previous HEAD:

```sh
git reflog
```

Look for the entry before the rebase.
Then reset:

```sh
git reset --hard <old-head-sha>
```

Example:

```sh
git reset --hard HEAD@{5}
```

This returns to the state 5 positions back in your reflog.

## Output Contract

Return:

1. The new commit history (verify with `git log --oneline -n 20`)
2. Confirmation of successful rebase or clear error message if conflicts prevented completion
3. If conflicts occurred and were resolved: list of files that were modified and how
4. Push command recommended (if force-push is needed, always use `--force-with-lease`)
5. Any explicit warnings if this branch is shared or if force-push behavior may affect others
