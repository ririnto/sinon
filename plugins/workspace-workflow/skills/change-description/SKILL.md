---
name: change-description
description: Draft a Git-contained change summary, review context, or merge handoff from repository evidence.
---

# Change Description

Compose a truthful, self-contained description from the requested diff, commit history, validation evidence, and user context.
This is a drafting skill, not an implementation lifecycle or authorization to integrate or publish.

## Evidence And Scope

- Use the requested scope: staged changes, working-tree changes, a commit range, or branch comparison.
  Resolve a comparison base and target independently when a branch-level handoff needs them.
- Inspect relevant repository policy and the actual diff before making claims.
  Do not run an unrelated startup checklist or infer motivation from filenames alone.
- Describe one cohesive review unit.
  For independent concerns, identify useful split boundaries without staging files or rewriting commits.
- List only validation that ran, with its actual result.
  Mark required checks with missing evidence as pending or unverified.
- Keep rationale, proof, and handoff self-contained in Git.
  Use repository-relative paths and portable examples.
  Do not include external Issue/PR numbers, work-item identifiers, review URLs, or private local environment details.
- Do not publish, change remote metadata, assign responsibility, or mutate Git state without a separate applicable grant.

For a resolved branch comparison, inspect only the evidence needed:

```sh
git -C /path/to/repo log <base>..HEAD --oneline
git -C /path/to/repo diff <base>...HEAD --stat
git -C /path/to/repo diff <base>...HEAD
```

Use `git diff --cached` for a staged-only description and `git diff` for unstaged tracked changes.
Account for untracked files when they belong to the requested scope.
Disclose stale remote-tracking evidence; do not silently fetch or invent a base.

## Draft The Required Detail

Follow the repository's format when one applies.
Otherwise include only sections that help the requested review or handoff:

- Summary: intended result and affected behavior.
- Why: evidenced problem or requirement.
- Changes: meaningful implementation facts and boundaries, not a file-by-file recital.
- Validation: exact checks and results, including material gaps.
- Review focus: consequential behavior or unresolved uncertainty.
- Merge handoff: known target, comparison base, selected strategy, and authorization still needed.
- Risks: concrete compatibility, rollout, dependency, or performance effects.

A small change may need one paragraph and validation evidence rather than every heading.
Do not add empty `None` sections or repeat evidence in an output checklist.
If motivation is unavailable, say so instead of inventing a reason.
Use the repository's title convention; load [commit-convention](../commit-convention/SKILL.md) only when Conventional Commits details are needed.

## Completion And Readiness

Return the requested draft even when integration is not ready, with the blocker made explicit.
A complete description of incomplete work is not a claim that the work can merge.
Do not mark a merge handoff ready while required validation, review findings, implementation, or target decisions remain unresolved.

Before returning, compare the draft with the actual diff and supplied evidence.
Report only material blockers and unverified claims.
Return the draft to the caller without publishing it or starting an unrelated implementation, test, or Git operation.
