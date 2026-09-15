---
name: git-worktree-management
description: Add, inspect, remove, or repair Git worktrees and check branch bindings for isolated parallel work.
---

# Git Worktree Management

Use a worktree when the task needs a separate checkout, not as a prerequisite for every edit.
Each worktree has its own working tree, index, and HEAD.
All linked worktrees share the repository's objects and refs.
A commit or branch update is therefore visible across worktrees even though uncommitted files remain isolated.

## Boundaries

- Inspect existing paths, branch bindings, and relevant local changes before mutation.
- Keep one active worktree per branch; do not bypass that protection with `--force`.
- Keep worktrees outside each other's directories, using sibling paths or the repository's approved external location.
- An unrelated dirty worktree does not block creating an isolated one.
  Preserve its files and do not move its checked-out branch.
- Worktree creation does not authorize commits, publication, branch deletion, or changes in another task's checkout.
- Remove only an authorized worktree after checking tracked, untracked, and ignored files for retained work.
  Do not force-remove a dirty or locked worktree to finish cleanup.

## Inspect Or Reuse

```sh
git -C /path/to/repo worktree list --porcelain
git -C /path/to/worktree status --short --branch
```

Use an existing worktree only when its ownership and state fit the task.
Do not remove another worktree merely because its branch is already checked out.
Choose a different authorized branch or report the ownership conflict.

## Create The Needed Checkout

Resolve the repository, destination, branch, and base before selecting a command.
These portable paths represent sibling directories, not real local environment paths.

| Need | Command |
| --- | --- |
| Existing local branch | `git -C /path/to/repo worktree add /path/to/worktree <branch>` |
| New local branch | `git -C /path/to/repo worktree add -b <new-branch> /path/to/worktree <base>` |
| New tracking branch | `git -C /path/to/repo worktree add --track -b <branch> /path/to/worktree <remote>/<branch>` |
| Inspect a commit without a branch | `git -C /path/to/repo worktree add --detach /path/to/worktree <commit>` |

Confirm success from the exit result and resulting worktree/HEAD state, not silence alone.
Use `git -C` to target the intended checkout rather than relying on a prior shell's working directory.

## Remove Or Repair

Run removal outside the target worktree after verifying that its data is preserved and cleanup is authorized:

```sh
git -C /path/to/worktree status --short --ignored
git -C /path/to/repo worktree remove /path/to/worktree
```

Removal deletes the checkout and its administrative record, not the branch or commits.
Branch cleanup is a separate Git action.

For missing worktree directories, inspect before pruning:

```sh
git -C /path/to/repo worktree prune --dry-run
```

An unavailable removable drive is not proof that a worktree is obsolete.
Prune confirmed stale metadata only when authorized.
For moved worktrees, prefer `git worktree repair <path>` when repair matches the known move.
Do not edit `.git/worktrees/` records by hand.

## Completion

Verify the requested path and branch binding, or removal/repair result.
Report the action, preserved work, and unresolved ownership or cleanup blockers.
Run project checks only if the task also changes project content or requires runtime verification.
Use repository-relative paths and portable examples in committed descriptions.
Do not expose private worktree locations, external work-item identifiers, or review URLs.
