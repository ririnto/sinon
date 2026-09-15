---
name: git-rebase-strategies
description: Plan or perform an authorized Git rebase, autosquash, selective replay, or rebase-conflict recovery.
---

# Git Rebase Strategies

Replay the intended commits onto an explicit base without losing work or rewriting history outside the grant.
Rebase changes commit identities; it is not a substitute for choosing repository integration policy.

## Authority And Preservation

- Inspection and advice are read-only unless the user authorizes a history change.
  Resolve the branch, old range, new base, and allowed edits before rebasing.
- Preserve uncommitted work and inspect branch-to-worktree bindings.
  Use [working-tree-hygiene](../working-tree-hygiene/SKILL.md) when isolation is needed.
- Rebase shared history only with explicit approval and collaborator coordination.
  Ownership of a pushed branch does not itself authorize a force-push.
- Fetch when current remote evidence is necessary and network access is authorized.
  Local-only history editing needs no remote access.
- Record the old tip and selected range before rewriting.
  Do not add backup branches or files unless the task authorizes them.

## Select The Operation

Use the repository's actual base and reviewed range, not an arbitrary last-N-commits recipe.

| Need | Command |
| --- | --- |
| Replay a branch onto a base | `git rebase <new-base>` |
| Reword, reorder, edit, or combine selected commits | `git rebase -i <old-base>` |
| Apply existing fixup/squash markers | `git rebase -i --autosquash <old-base>` |
| Replay a selected range onto a different base | `git rebase --onto <new-base> <old-base> <branch>` |

Inspect merge commits before choosing a mode; ordinary rebase does not preserve their topology.
Prefer merge when published ancestry must remain intact or repository policy requires merge commits.

Interactive actions have distinct effects:

- `pick` retains the change.
- `reword` changes its message.
- `edit` pauses for an authorized content or message edit.
- `squash` combines it with the previous commit and edits the combined message.
- `fixup` combines it while discarding its message.
- `drop` removes it from the replayed history.

Do not drop or alter content outside the approved rewrite intent.
Creating a new `git commit --fixup <commit>` requires a commit grant, separate from reviewing existing history.

## Conflicts And Recovery

Inspect the paused state and conflicting files before editing.
During rebase, `ours` is the upstream/rebased result and `theirs` is the commit being replayed.
Resolve intended behavior rather than selecting a side mechanically.
Stage only resolved paths, then use `git rebase --continue`.
Use `--skip` only when omitting that commit's change is intended and authorized.

If safe continuation is blocked, report it or use `git rebase --abort` when the pre-rebase state can be restored safely.
For a completed mistaken rebase, inspect the reflog and old tip before proposing recovery:

```sh
git -C /path/to/repo reflog
git -C /path/to/repo show <old-tip>
```

A reflog position is not a stable recovery target.
Do not run `git reset --hard` from a canned recipe; it can discard current work and requires explicit authority.

## Verification

Compare the intended old and new ranges, not only their commit messages:

```sh
git -C /path/to/repo range-diff <old-base>..<old-tip> <new-base>..<new-tip>
git -C /path/to/repo status --short --branch
```

Account for intended edits and conflict resolutions.
Run affected native checks when content changed; preserve passing evidence for unchanged content and toolchain.
Update in-scope references to rewritten commits when their meaning depends on the old identities.
Report external references needing separate coordination without modifying external systems.

## Publication Boundary

Rebase does not authorize pushing.
If a separate grant explicitly permits replacing the named remote ref, use `--force-with-lease`, never bare `--force`.
Verify the expected remote tip and all replacement commits before the authorized push.
A lease guards a ref comparison; it does not prove that overwriting known remote changes is appropriate.

## Completion

Report the selected range and base, rewrite result, checks, material conflicts, and any remaining publication decision.
Do not recommend a push as an automatic final step.
Keep committed descriptions self-contained, with portable paths and no private environment details, work-item identifiers, or review URLs.
