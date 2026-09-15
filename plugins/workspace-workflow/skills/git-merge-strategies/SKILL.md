---
name: git-merge-strategies
description: Choose or perform an authorized Git merge, resolve merge conflicts, or assess recurring conflict resolutions.
---

# Git Merge Strategies

Integrate the named source into the named target using repository policy and actual ancestry.
This skill covers merges, not rebase, cherry-pick, or remote publication.

## Authority And Preconditions

- Advice and inspection do not authorize merging or committing.
  Confirm the source, target, and granted Git action before mutation.
- Inspect repository policy, relevant ancestry, worktree bindings, and local changes.
  Use [working-tree-hygiene](../working-tree-hygiene/SKILL.md) when preservation or isolation needs attention.
- Preserve uncommitted work; do not discard, overwrite, or automatically stash unrelated changes.
- Fetch only when current remote evidence is needed and network access is authorized.
  A local merge without an applicable upstream needs no fetch.
- Do not infer merge policy from feature size or commit count.
  If history choices materially differ and policy is absent, present the choices for a decision.

## Choose The History Result

| Intent | Command | Result |
| --- | --- | --- |
| Fast-forward only | `git merge --ff-only <source>` | Move target to source; fail if divergence requires a merge commit |
| Preserve explicit integration point | `git merge --no-ff <source>` | Create a merge commit, including when fast-forward is possible |
| Combine changes into one new commit | `git merge --squash <source>` | Stage combined changes without recording source ancestry or committing |
| Integrate several non-conflicting heads | `git merge -s octopus <source-a> <source-b> ...` | Create a multi-parent merge; not suitable for manual conflict resolution |

There is no universal `--no-ff` default.
Plain `git merge <source>` may fast-forward or create a merge commit, depending on history and configuration.
Use the explicit mode that matches the selected policy.

## Execute The Selected Merge

Inspect the selected refs before acting:

```sh
git -C /path/to/repo status --short --branch
git -C /path/to/repo worktree list --porcelain
git -C /path/to/repo merge-base <target> <source>
git -C /path/to/repo log --oneline <target>..<source>
git -C /path/to/repo diff <target>...<source>
```

Run the chosen merge in the target branch's worktree.
Record the pre-merge target commit for a precise before/after comparison.
For a merge commit, use the repository's message convention and explain integration intent.
For squash, review the staged diff and create the single commit only when the grant includes committing.

## Resolve Conflicts

Inspect `git status` and the affected files, including both sides and their base.
Resolve according to intended behavior, not an arbitrary compromise or blanket choice of one side.
Stage only resolved paths and verify the affected diff before completing the merge.
No unresolved conflict markers may remain.

For modify/delete conflicts, decide whether the file's responsibility still exists before keeping it or using `git rm`.
For renames, preserve the intended destination and applicable content changes.
For binaries, select the intended version or regenerate from its authoritative source.
`--ours` is the current target and `--theirs` is the incoming source during a normal merge.
Do not transfer that interpretation to rebase.

If the authorized merge cannot continue safely, report the conflict or use `git merge --abort` when preserving the pre-merge work is established.
Do not substitute a destructive reset for recovery.

## Reuse Recorded Resolutions

When rerere is already enabled, review its proposed resolution like a manual edit.
A prior resolution is evidence, not proof that it remains correct.
Enable rerere or change its cache only when the task authorizes that configuration or cache change.
A merge request alone does not authorize global Git configuration edits.

## Verification And Handoff

Verify the expected history result, affected diff, and preservation of unrelated work.
Run native checks for content changed by integration or conflict resolution.
Reuse valid checks for unchanged inputs instead of rerunning suites because commit hashes changed.

Report source and target, merge mode, resulting commit, checks, and unresolved conflicts or blockers.
Do not push, delete remote branches, or rewrite published history without a separate applicable grant.
Remove a completed local branch or worktree only when cleanup is authorized and retained work is verified.
Use repository-relative paths and portable examples in committed descriptions.
Keep private environment details, external work-item identifiers, and review URLs out of them.
