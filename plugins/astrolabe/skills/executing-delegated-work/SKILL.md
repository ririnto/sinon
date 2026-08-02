---
name: executing-delegated-work
description: Use when a worker receives a scoped implementation or validation assignment and must preserve ownership boundaries, avoid collisions, and return complete evidence.
---

# Executing Delegated Work

## Accept the Assignment

Apply this skill to read-only Explore, research, analysis, implementation, and validation assignments.

Treat the launcher payload as task steering within the user's existing authority and use its task fields without reconstructing a second payload contract.

The launcher also supplies the activity name, predecessor decisions, mutable resources, collision rules, and terminal release requirement when those details affect safe execution.

At intake, confirm the assigned `Goal`, `Scope`, `AcceptanceCriteria`, `RequiredEvidence`, and `AuthorityBoundary`, then confirm target paths, required checks, predecessor decisions, and ownership of every mutable resource.

Do not delegate further, invoke nested workers, or turn a scoped assignment into a new work graph.

## Protect the Assigned Surface

Own only the listed mutable resources and preserve unrelated changes.

Inspect current source, tests, interfaces, current changes, and active workers before assigned work begins.

If another active worker owns or edits a target, report the worker, resource, and collision evidence, then stop with `BLOCKED`.

Stop before any authority expansion, unsafe side effect, or unresolved interface decision.

If mutation is assigned, inspect the current target before editing, perform the required dry-run when the scope reaches five files, and keep the final changed-file set within the assigned paths.

Do not run `git commit`, `git reset`, `git revert`, `git push`, `git stash`, `git rebase`, `git merge`, or destructive checkout commands.

## Plan Multi-File Mutation

If mutation is assigned and five or more files are affected, perform and report a dry-run before mutation.

When that dry-run applies, it must inventory every affected path, show representative before and after content, name the deterministic mutation command, name the deterministic verification command, and state the expected changed-file set.

When that dry-run applies, stop before mutation when the inventory, ownership, command target, representative content, or expected path set contains an anomaly.

When that dry-run applies, do not use one that checks only a subset of the affected paths.

If mutation is assigned and fewer than five files are affected, still inspect the complete final changed-file set and report every unexpected path.

## Implement Assigned Mutation

If mutation is assigned, make the smallest root-cause change that satisfies the assignment and keep unrelated files untouched.

If mutation is assigned, deliver working behavior rather than stubs, TODO-only placeholders, fake passes, speculative scaffolds, or unimplemented branches.

If mutation is assigned, do not alter tests to hide a product failure or broaden the assignment into unrelated cleanup.

## Verify the Final Inputs

Run the checks that cover the changed behavior and report their commands, exit statuses, and evidence.

Rerun affected checks after every repair and run the required final checks against the final inputs.

Treat a worker or tool's green check as a claim until the current artifact and output support it.

Report skipped, failed, missing, stale, and inconclusive checks with their consequences instead of converting missing evidence into a pass.

Audit the actual changed-file set after verification and compare it with the assigned paths and the dry-run inventory.

## Release and Report

Release every owned resource at terminal completion, terminal failure, or safe abandonment after an inspected recovery decision.

Return one self-contained result with `Status`, `Files`, `Signatures`, cumulative `Breaking*`, `Decisions`, `Summary`, `EvidenceRefs`, and `Blockers`.

Verifier work also returns `FindingsOrDispositions` with each finding's disposition, reason, and evidence reference.

Use `BLOCKED` for collisions, missing authority, unsafe side effects, or unresolved interface decisions that prevent safe work.

Use `FAILED` when the assignment was attempted but its acceptance criteria are not satisfied.

Use `UNKNOWN` when required evidence is missing, stale, or inconclusive.

Use `COMPLETED` only when the assignment's acceptance criteria and current evidence agree, and use explicit empty values for fields that do not apply.
