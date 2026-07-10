# Workflow

`WORKFLOW.md` is the operational playbook for this repository's local review flow.

## Authority

The user-facing root session is the sole orchestrator and completion authority.
Claude Code can nest subagents when `Agent` is granted, but this repository omits delegation tools from project agents; Codex uses `max_depth = 1` for the same leaf policy.
Every repository agent is a deliberate leaf.

## Model and Agent Routing

Delegate bounded exploration, implementation, documentation, audit, and review work to leaves.
The orchestrator owns workflow selection, agent type selection, capability tier selection, prompt scope, fan-in, and final decisions.
Only the user-facing top-level or root agent acts as orchestrator.
Installed repository agents are delegation targets only; do not create or delegate to a `project-orchestrator` agent.

| Work | Claude | Codex | Effort | Agent |
| --- | --- | --- | --- | --- |
| Top-level orchestration and completion | `opus` | `gpt-5.6-sol` | `medium` | interactive root session |
| Exhaustive single-file or small related-file edit | `haiku` | `gpt-5.6-luna` | `low` | `scoped-implementer` |
| Related-file discovery, broad implementation, or integration | `sonnet` | `gpt-5.6-terra` | `medium` | `implementation` |
| Independent change review | `sonnet` | `gpt-5.6-terra` | `medium` | `review` |

Use `scoped-implementer` only for a complete, explicit file set with no architecture or scope expansion.
Use `implementation` for affected-set discovery, related contracts, multi-file or multi-layer work, design choices, and integrated validation.
Ambiguous scope returns to read-only exploration and planning before writer selection.
Haiku's `effort: low` is explicit runtime-inert metadata under the current Claude compatibility table.

- `scoped-implementer`: performs a fully specified edit inside an exhaustive single-file or related-file ownership list with targeted validation.
- `implementation`: handles large or cross-file changes that require affected-set discovery, cross-file reasoning, or integration validation.
- `review`: reviews changes and validation evidence for risks and contract drift.

## Required Plan

Record acceptance criteria, difficulty, routing, ownership, base commit, worktree, validation, review, and local completion target before dispatch.
Material ambiguity blocks writers.

| Need | Agent type |
| --- | --- |
| Read-only search, issue duplicate checks, dependency scan | explorer or read-only research agent |
| Exhaustive single-file or related-file edit with desired behavior and exact validation commands | `scoped-implementer` |
| Large change, related modules or layers, unknown affected set, cross-file reasoning, or integration validation | `implementation` |
| Independent quality, risk, validation, or contract review | `review` |
| Local review or execution-plan completion | main orchestrator |

The packaged `scoped-implementer` uses the lightweight Haiku/Luna low-effort tier.
The general `implementation` agent uses the Sonnet/Terra medium-effort tier.
Never send an ambiguous or incomplete file set directly to `scoped-implementer`.
When ownership is not exhaustive, explore and plan first, then either supply a complete ownership list or route the work to `implementation`.
Parallel `scoped-implementer` assignments MUST have disjoint ownership lists.

Choose the lightest capability tier that can complete the assignment.
Tiers name capability bands, not specific vendor models; map the available runtime's models to Haiku-, Sonnet-, and Opus-equivalent bands by published capability, not by marketing label.

| Difficulty | Capability tier |
| --- | --- |
| Narrow lookup, mechanical formatting, short evidence collection | Haiku-tier |
| Routine implementation, validation triage, issue-mining synthesis | Sonnet-tier |
| Ambiguous architecture, security-sensitive work, broad autonomous planning, final high-risk review | Opus-tier |

Include the inputs the assignment needs:

- scope
- acceptance criteria or question to answer
- workflow decisions that affect the assigned scope
- validation command or blocker, when validation is part of the assignment
- completion target, when the assignment owns that record
- expected output fields

Add context paths only when the assignment has known files, directories, records, or commands.
Pass only the branch, validation, review, and completion decisions needed for the assignment.
Integrate returned changes or findings through the active orchestration workflow.
Wait for delegated results before making dependent decisions.

## Non-interactive Delegation

Subagent orchestration assumes an interactive main loop.
For CI jobs, hooks, and autonomous loops where no human is present, delegate through `claude -p` (print mode), which runs one prompt to completion and exits instead of opening a session.

```sh
claude -p "List each validation failure in this repository as path: reason" > .tmp/review.md
```

Pass the same scope, acceptance criteria, validation command, and output fields as an interactive subagent assignment; capture stdout to a file when the caller needs the result.

## Orchestration Workflow

| Phase | Action |
| --- | --- |
| Intake | Confirm the task, plan, local review record, or user request that owns the work. |
| Explore | The top-level orchestrator assigns architecture, docs, code, validation, and local-review-context exploration to bounded leaf workers. |
| Plan | Define subagent scopes, changed files, acceptance criteria, validation, manual QA, and local review target. |
| Implement | The top-level orchestrator assigns the change set to a bounded leaf worker to satisfy the criteria and preserve the contracts that cover changed files. |
| Review | Assign independent review for correctness, security, contract drift, and missing evidence when the change is non-trivial. |
| Validate | Run the active validation command and active hooks after integrating subagent output. |
| Complete | Update the local review record or execution plan with validation, review, evidence, and blockers. |

## Issue Mining

Use the `issue-mining` skill when the user asks to investigate a specific task, duplicate or related reports, likely code cause, or open-ended issue candidates.
Issue mining ends with a report or requested local record registration.
It does not implement fixes.
The orchestrator may assign internal or external exploration and then integrates findings before reporting or registration.

## Evidence

Record these items before completing the plan:

- issue source or mining rationale
- validation command and result
- test names or CI job names
- manual QA action and observed output
- review findings or approval record
- unresolved blockers and owner

## Git and Records

Branch names use `<type>/<short-description>`.

Use built-in worktree tooling if the runtime provides it.
Fallback to Git worktrees from the repository-approved base ref.
Run `git fetch <remote>` first when `<base-ref>` is remote.

## Parallelism

- Independent read-only workers may share a worktree.
- Each writer owns one disjoint file and contract surface in one worktree.
- Serialize overlapping writers.
- Workers do not commit, publish, or edit another worker's branch.

```sh
git worktree add <worktree-path> -b <type>/<short-description> <base-ref>
```

## Lifecycle

1. Intake: identify the user request, task, plan, or local review record.
2. Plan: define verifiable criteria, dependencies, owners, validation, and completion target.
3. Classify: select exploration, `scoped-implementer`, `implementation`, or `review` with model and effort.
4. Isolate: assign disjoint writer worktrees and serialize overlap.
5. Dispatch: provide scope, base commit, criteria, validation, and output fields.
6. Fan in: wait for every requested result and resolve contradictions.
7. Review: run independent `review` over integrated changes.
8. Fix: return findings to the owning writer.
9. Re-review: verify the fix over the same scope.
10. Validate: run focused and integrated repository checks on the combined tree.
11. Complete: update the local review record or execution plan from the root session.

Missing, failed, or contradictory workers block completion.
Worker-branch validation is not integrated validation.

## Local Records

Record durable decisions and completion evidence in the repository-approved tracker or `docs/exec-plans/`.
Do not invent a GitHub or GitLab path when local review is selected.

## Evidence and Completion

Record worker fan-in, validation commands and results, review findings, owner fixes, re-review, manual QA, blockers, and the completed local record.
Do not report completion while any required evidence or record update is missing.

Use `autonomous-execution` only when explicitly requested.
Use `issue-mining` for investigation and local record preparation, not fixes.
