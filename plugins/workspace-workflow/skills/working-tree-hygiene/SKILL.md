---
name: working-tree-hygiene
description: Inspect staged, unstaged, untracked, stash, and upstream state to preserve work during a Git operation.
---

# Working Tree Hygiene

Establish a known state for the requested operation, not an empty tree for every task.
Unrelated changes are work to preserve, not cleanup to perform automatically.

## Authority And Preservation

- Inspect state before changing the index, working tree, stash, or refs.
  Read-only tasks and isolated edits may proceed in a dirty tree.
- Preserve staged and unstaged intent separately.
  Do not stage, unstage, stash, commit, ignore, or delete unrelated work to make status look clean.
- Before a merge, rebase, or branch switch, identify changes that could be overwritten.
  Use an authorized stash, separate worktree, or explicit preservation path when needed.
- Discarding changes, deleting user files, and dropping stashes require explicit authority for the affected data.
- Commit and push only within an explicit Git grant.
  A readiness check does not authorize publication, fetching, pulling, or rewriting history.

## Inspect The Relevant State

Use the commands needed for the operation, with the intended repository path:

```sh
git -C /path/to/repo status --short --branch
git -C /path/to/repo diff --cached
git -C /path/to/repo diff
```

In short status, `X` is index state, `Y` is working-tree state, and `??` marks an untracked file.
Inspect unfamiliar status codes with `git status --help`.
For each relevant change, determine ownership and whether it belongs to the requested operation.
Leave unrelated files untouched.

For a commit, review the staged diff as the exact proposed commit.
For branch integration, inspect the relevant base, upstream, and worktree bindings.
For publication, distinguish committed content from local changes that have not been included in its verification.

## Temporary Isolation

Use a stash only when the task authorizes shelving those changes and isolation is needed.
Choose named paths where possible:

```sh
git -C /path/to/repo stash push -m "isolate selected work" -- path/to/file
git -C /path/to/repo stash list
git -C /path/to/repo stash show --stat 'stash@{0}'
```

Normal stashing omits untracked files; include `-u` only when those files belong to the authorized stash.
Inspect the selected stash and destination before restoring it:

```sh
git -C /path/to/repo stash apply 'stash@{0}'
```

Resolve any conflicts without losing either side.
Verify restoration before an authorized stash drop; `apply` retains the recovery copy.
Do not apply or drop another task's stash.

## Untracked Files

Track source files only when they belong to the requested change.
Ignore generated files only when repository policy and the task require that change.
Delete only authorized temporary output or user-approved data.
An unrelated untracked file is not a reason to edit `.gitignore` or clean the repository.

## Upstream And Publication Readiness

Resolve the actual remote, branch, and target rather than assuming `origin/main`.
Ahead/behind counts describe local remote-tracking refs, which may be stale.
Fetch the named remote when current remote evidence is needed and network access is authorized.
If the branch is behind or diverged, choose the repository-approved merge or rebase path before acting.
Do not use an unqualified `git pull` as automatic remediation.

A ready-to-push report identifies the intended commits, target ref, relevant validation, and remaining blockers.
Unrelated dirty work does not enter a push, but MUST remain untouched and separate from the verified commit state.
Never force-push merely to resolve a rejected push.

## Completion

Verify the affected index, worktree, stash, or branch state after an authorized mutation.
Reuse check evidence for unchanged content; run affected native checks after conflict resolution or integration changes.
Report the preserved baseline, action and result, and any unresolved blocker.
Use repository-relative paths and portable examples in committed text.
Do not include private local environment details, external work-item identifiers, or review URLs.
