# Workflow

`WORKFLOW.md` is the operational playbook for repositories that publish through GitHub, GitLab, or both.

## Authority

The user-facing root session is the sole orchestrator.
It delegates all task work and owns decomposition, worker selection, fan-in, integrated validation, Git state, and publication.

Claude Code can nest subagents when `Agent` is granted, but this repository deliberately omits delegation tools from project agents.
Codex uses `max_depth = 1` for the same leaf policy.
Every repository agent returns a decomposition handoff when additional workers are needed.

Delegate bounded exploration, implementation, documentation, audit, and review work to leaves.
The orchestrator owns workflow selection, agent type selection, capability tier selection, prompt scope, fan-in, and final decisions.
Only the user-facing top-level or root agent acts as orchestrator.
Installed repository agents are delegation targets only; do not create or delegate to a `project-orchestrator` agent.

## Model and Agent Routing

| Work | Claude | Codex | Effort | Agent |
| --- | --- | --- | --- | --- |
| Top-level orchestration and final decisions | `opus` | `gpt-5.6-sol` | `medium` | interactive root session |
| Exhaustive single-file or small related-file edit | `haiku` | `gpt-5.6-luna` | `low` | `scoped-implementer` |
| Related-file discovery, broad implementation, or integration | `sonnet` | `gpt-5.6-terra` | `medium` | `implementation` |
| Independent change review | `sonnet` | `gpt-5.6-terra` | `medium` | `review` |

- `scoped-implementer`: performs a fully specified edit inside an exhaustive single-file or related-file ownership list with targeted validation.
- `implementation`: handles large or cross-file changes that require affected-set discovery, cross-file reasoning, or integration validation.
- `review`: reviews changes and validation evidence for risks and contract drift.

Use `scoped-implementer` only when the caller can enumerate the complete file set and no architecture, scope expansion, or integration decision remains.
Use `implementation` when the affected set must be discovered or crosses files, modules, layers, contracts, or validation surfaces.
If scope exhaustiveness is unclear, run read-only exploration and produce a verifiable plan before selecting a writer.

Claude accepts Haiku's explicit `effort: low`, but current official documentation does not list Haiku as effort-aware.
Treat the field as runtime-inert routing metadata.

| Need | Agent type |
| --- | --- |
| Read-only search, issue duplicate checks, dependency scan | explorer or read-only research agent |
| Exhaustive single-file or related-file edit with desired behavior and exact validation commands | `scoped-implementer` |
| Large change, related modules or layers, unknown affected set, cross-file reasoning, or integration validation | `implementation` |
| Independent quality, risk, validation, or contract review | `review` |
| Record publication or completion | main orchestrator |

The packaged `scoped-implementer` uses the lightweight Haiku/Luna low-effort tier.
The general `implementation` agent uses the Sonnet/Terra medium-effort tier.
Never send an ambiguous or incomplete file set directly to `scoped-implementer`.
When ownership is not exhaustive, explore and plan first, then either supply a complete ownership list or route the work to `implementation`.
Parallel `scoped-implementer` assignments MUST have disjoint ownership lists.

Choose the lightest capability tier that can complete the assignment.
Tiers name capability bands, not specific vendor models; map the available runtime's models to Haiku-, Sonnet-, and Opus-equivalent bands by published capability, not by marketing label.

## Verifiable Plan

Before dispatching work, record:

- observable acceptance criteria
- affected behavior and known paths
- read-only versus writer classification
- difficulty, agent, model, and effort
- owner, base commit, and worktree for every writer
- focused and integrated validation commands
- review and publication target

Material ambiguity blocks writer dispatch.

## Parallelism and Ownership

- Independent read-only workers may share a worktree.
- Each writer owns one disjoint file and contract surface in one worktree.
- Overlapping writers are serialized under one owner.
- Generated outputs and their source templates count as one ownership surface.
- Workers do not commit, push, publish, or change another worker's branch.

Create an isolated writer worktree from the approved base when the runtime does not provide one:

```sh
git worktree add <worktree-path> -b <type>/<short-description> <base-ref>
```

## Lifecycle

1. Intake: identify the request, issue, plan, or review record that owns the work.
2. Plan: define acceptance criteria, dependencies, ownership, validation, and publication target.
3. Classify: select read-only exploration, `scoped-implementer`, `implementation`, or `review` with model and effort.
4. Isolate: assign disjoint writers to explicit worktrees and serialize overlap.
5. Dispatch: provide scope, acceptance criteria, base commit, workflow decisions, validation, and output fields.
6. Fan in: wait for every requested result and reconcile contradictions.
7. Review: run independent `review` after integration-ready changes exist.
8. Fix: return findings to the owning writer.
9. Re-review: verify the owner fix over the same scope.
10. Validate: run focused checks and the integrated repository command on the combined tree.
11. Publish: update the selected host record only from the root session.

A failed, missing, or contradictory worker result blocks fan-in and completion.
Worker-branch validation does not replace integrated validation.

## Background CLI Dispatch

When native delegation cannot select the required model, dispatch a bounded leaf through a background CLI. Record the requested model and effort, prompt, worktree, ownership, job or session identifier, output, and exit status. Wait for full fan-in before integration or release. Request acceptance does not prove backend identity. Open the selected host reference for commands and version-sensitive flags.

## Review Host Selection

Resolve the host from explicit user choice, existing review metadata, repository policy, upstream, then remote URL.
When GitHub and GitLab are both plausible, ask which host owns the record.
After selection, inspect only that CLI and authentication state.

| Host | CLI |
| --- | --- |
| GitHub | `gh` |
| GitLab | `glab` |
| Local policy | repository-approved local review flow |

Do not preload both host command catalogs.

## Evidence and Completion

Record:

- acceptance criteria and owning record
- worker results and fan-in status
- focused and integrated validation commands and results
- review findings, owner fixes, and re-review result
- manual QA actions and observed output
- unresolved blockers and owners
- final publication URL or local completion record

Do not report completion while any required result, owner fix, re-review, validation, or publication action is missing.

## Autonomous Execution

Use `autonomous-execution` only when the user explicitly requests continued follow-through beyond one scoped item.
It inherits this workflow's ownership, model routing, fan-in, validation, and root-only publication gates.

Use `issue-mining` for investigation and record preparation only.
It does not implement fixes.
