# Workflow

`WORKFLOW.md` is the operational playbook for this repository's Git-contained review flow.

## Role

- Orchestrate repository work from intake through Git-contained integration.
- Choose direct execution or scoped subagent delegation for each phase.
- Integrate subagent results, validation evidence, review findings, and local Git state.
- Record task context, change descriptions, and proof in versioned project files.

## Subagent Use

Use subagents as the normal tool for bounded exploration, implementation, and review when the work needs isolated context or independent judgment.
The orchestrator owns workflow selection, agent type selection, capability tier selection, prompt scope, fan-in, and final decisions.
Only the user-facing top-level or root agent acts as orchestrator.
Installed repository agents are delegation targets only; do not create or delegate to a `project-orchestrator` agent.

Repository subagents:

- `scoped-implementer`: performs a fully specified edit inside an exhaustive single-file or related-file ownership list with targeted validation.
- `implementation`: handles large or cross-file changes that require affected-set discovery, cross-file reasoning, or integration validation.
- `review`: reviews changes and validation evidence for risks and contract drift.

Choose the narrowest agent type that can complete the assignment.

| Need | Agent type |
| --- | --- |
| Read-only search, issue duplicate checks, dependency scan | explorer or read-only research agent |
| Exhaustive single-file or related-file edit with desired behavior and exact validation commands | `scoped-implementer` |
| Large change, related modules or layers, unknown affected set, cross-file reasoning, or integration validation | `implementation` |
| Independent quality, risk, validation, or contract review | `review` |
| Git-contained task and change records | main orchestrator |

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
- publication or completion target, when the assignment owns that record
- expected output fields

Add context paths only when the assignment has known files, directories, records, or commands.
Pass only the branch, validation, review, and publication decisions needed for the assignment.
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
| Intake | Confirm the local task, plan, change description, or user request that owns the work. |
| Explore | Assign or perform architecture, docs, code, validation, and local Git-context exploration. |
| Plan | Define subagent scopes, changed files, acceptance criteria, validation, manual QA, and integration target. |
| Implement | Assign or perform the change set needed to satisfy the criteria and preserve the contracts that cover changed files. |
| Review | Assign independent review for correctness, security, contract drift, and missing evidence when the change is non-trivial. |
| Validate | Run the active validation command and active hooks after integrating subagent output. |
| Integrate | Update the Git-contained task and change records, then apply the approved Git integration method. |

## Issue Mining

Use the `issue-mining` skill when the user asks to investigate a specific problem, duplicate or related reports, likely code cause, or open-ended improvement candidates.
Issue mining ends with a report or a requested Git-contained task record.
It does not implement fixes.
The orchestrator may assign internal or external exploration and then integrates findings before reporting or registration.

## Evidence

Record these items before integration:

- task source or mining rationale
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

```sh
git worktree add <worktree-path> -b <type>/<short-description> <base-ref>
```

Keep the task context and change description in versioned project files.
Record the branch or commit range, acceptance criteria, validation, review findings, manual QA, blockers, and integration decision.
Apply the repository-approved Git integration method only after approval and final validation.

## Autonomous Execution Loop

Use the `autonomous-execution` skill only when the user explicitly asks for autonomous follow-through beyond one scoped work item.
The skill uses this workflow's Git-contained review policy, subagent rules, capability-tier rules, evidence requirements, and integration path.
The orchestrator may process non-overlapping tasks in separate worktrees when their files, contracts, validation surfaces, and integration targets do not conflict.
