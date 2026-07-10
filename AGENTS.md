---
description: Stable repository-wide rules for the Sinon plugin marketplace.
---

# Sinon Project Rules

Sinon is a marketplace repository for Claude Code plugins and portable Agent Skills.
Normative keywords MUST be interpreted under BCP 14.
Repository rules and agent-facing documents MUST be written in English.

## Entry Point

Before changing the repository, read:

1. this `AGENTS.md`
2. the root `README.md`
3. every nearer `AGENTS.md` from the repository root to the changed file
4. the affected plugin README and component files

`CLAUDE.md` files are pointer documents that import the nearest `AGENTS.md`.
They MUST NOT become parallel rule copies.

## Working Behavior

- Surface material ambiguity, assumptions, tradeoffs, and simpler alternatives before implementation.
- Write the minimum change that satisfies observable acceptance criteria.
- Preserve unrelated user changes and report unrelated defects without fixing them.
- Match existing local style and remove only orphans introduced by the current change.
- Continue until acceptance criteria are verified or an exact blocker is reported.
- Prefer a focused reproduction or test before a bug fix when the repository has an appropriate test surface.

## Orchestration Authority

The user-facing top-level session is the sole general orchestrator.
Sinon MUST NOT package a `project-orchestrator` or equivalent delegated profile.

The top-level orchestrator owns:

- requirement clarification and verifiable planning
- difficulty and model classification
- worker scope, ownership, and worktree selection
- complete fan-in and contradiction resolution
- integrated review and validation
- commits, pushes, pull or merge requests, and other publication

It delegates every exploration, implementation, documentation edit, audit, and validation task to a bounded leaf worker. It does not execute leaf work itself.

Claude Code supports nested subagents to a fixed depth when an agent receives the `Agent` tool.
Sinon deliberately omits delegation tools from installable agents, and installed Codex workflows use `max_depth = 1`.
Every packaged or project agent is a repository-policy leaf and MUST return a decomposition handoff when its task needs further delegation.

## Model and Effort Routing

Every agent file in this repository MUST declare a model and effort.

| Work class | Claude | Codex | Effort |
| --- | --- | --- | --- |
| Interactive top-level orchestration | `opus` | `gpt-5.6-sol` | `medium` |
| Substantive implementation, review, validation, or domain expertise | `sonnet` | `gpt-5.6-terra` | `medium` |
| Lightweight inventory or exhaustive mechanical edits | `haiku` | `gpt-5.6-luna` | `low` |

Opus and Sol MUST NOT be selected in installable agent runtime fields.
Use `high`, `xhigh`, or `max` only with a written `Effort Exception` in the agent body.
A substantive leaf using `low` MUST include a `Low Effort Rationale`.

Claude accepts `effort: low` with Haiku, but current official documentation does not list Haiku as effort-aware.
Treat the field as required, runtime-inert compatibility metadata and MUST NOT claim that it changes Haiku reasoning.

Codex agent TOML uses `model_reasoning_effort`, not `effort`.
Canonical mappings and counterpart paths live in `scripts/agent-routing-manifest.json` and are validated by `scripts/agent-routing.ts`.

## Agent Boundaries

- Leaf agents MUST NOT expose `Agent`, `Task`, or another child-delegation tool.
- Read-only reviewers, validators, scanners, routers, and drafters MUST NOT expose mutation tools.
- Agent bodies MUST state their leaf topology, ordinary process, stop conditions, escalation, and output.
- Intentional Claude and Codex counterparts MUST keep descriptions, instruction bodies, model pairs, and efforts aligned.
- Optional depth MUST stay outside recursively scanned `agents/` directories.
- `agents/` directories MUST contain only direct agent files; nested Markdown is prohibited.

Portable Agent Skills MUST NOT contain runtime `model`, `effort`, or `model_reasoning_effort` frontmatter.
Runtime routing belongs in agent files and repository workflow policy.

## Delegation and Single-Editor Safety

Before parallel work, classify every unit as read-only or mutating.

Dispatch independent reads and disjoint writers in parallel. Assign even narrow work to the lightest suitable worker with an explicit prompt, ownership, behavior, and completion check.

- Independent read-only workers MAY share a worktree.
- Each writer MUST receive one explicit, disjoint ownership scope, one base commit, one worktree, acceptance criteria, and validation target.
- Overlapping file, contract, or generated-output ownership MUST be serialized under one writer.
- Ambiguous affected-file scope MUST be explored or planned before selecting a writer.
- The lightweight scoped implementer is appropriate only for an exhaustive single-file or small related-file set with no architecture or scope expansion.
- The general implementation agent owns affected-set discovery, related-file changes, multi-module or multi-layer reasoning, and integrated validation.

## Fan-In and Review

The top-level orchestrator MUST wait for every requested worker result before dependent action.
A missing, failed, or contradictory result blocks completion.

After fan-in:

1. reconcile assumptions and overlapping evidence
2. integrate accepted work
3. run independent read-only review
4. return fixes to the owning writer
5. re-review the fixes
6. validate the integrated tree

Worker-branch checks MUST NOT substitute for integrated-tree validation.

## Completion and Publication Gates

Do not report completion or publish while any required acceptance criterion, worker result, owner fix, re-review, validation command, or publication result is missing.

Before publication, verify:

- the diff contains only authorized changes
- agent routing and counterpart parity pass
- plugin manifests and README inventories match the filesystem
- changed skills remain self-sufficient and portable
- security-sensitive hooks, scripts, MCP, LSP, settings, and packaged assets were reviewed
- the narrowest relevant checks and full repository check pass
- unresolved risks and skipped checks have explicit owners

Only the user-facing top-level orchestrator MAY commit, push, or create/update a review record unless the user explicitly grants another authority.

## Escalation

Stop and return the exact evidence, blocker, and safest next action when:

- requirements have materially different interpretations
- ownership overlaps or a safe worktree boundary cannot be established
- a required reference, tool, credential, or model is unavailable
- a configured model or effort is unsupported at runtime
- validation or review fails
- publication authority is missing

## Repository Invariants

- `AGENTS.md` is the canonical root contract.
- root `CLAUDE.md` MUST remain the exact pointer to root `AGENTS.md`.
- `.claude/skills/` MUST point to `plugins/agent-capability-kit/skills/`.
- `.claude/agents/` MUST point to `plugins/agent-capability-kit/agents/`.
- `.agents/skills/` MUST point to `.claude/skills/`.
- `.agents/agents/` MUST NOT exist.
- `.codex/agents/` MUST be a regular directory with one TOML counterpart per shared agent.
- `.claude-plugin/marketplace.json` is the root Claude marketplace catalog.
- Plugins MUST live under `plugins/` and keep their runtime files inside the owning plugin root.
- Target Harness assets MUST stay under `plugins/harness/skills/harness-install/assets/`.
- Root documentation MUST describe repository-wide contracts, not fast-changing plugin internals.

## Instruction Hierarchy

Narrow rules live near their activation surface:

- `plugins/AGENTS.md`: plugin packaging, documentation, code, and shell rules
- `plugins/agent-capability-kit/AGENTS.md`: agent and Agent Skill authoring
- `plugins/harness/AGENTS.md`: Harness package and installed-runtime contracts
- `plugins/workspace-workflow/AGENTS.md`: host detection and publication routing
- plugin-specific `AGENTS.md`: narrower domain rules

Every root-to-leaf project instruction chain MUST stay below the Codex default 32 KiB project-instruction limit.
Measure changed chains and verify live discovery before release.

## Required Validation

Run the narrowest relevant checks first, then `bun run check` before merging repository-wide changes.

Required release probes for agent-routing changes are:

```sh
bun scripts/agent-routing.ts
bun run probe:claude-plugins
bun run probe:agent-models
bun run probe:instruction-discovery
bun run check
```

Harness runtime or packaged-asset changes also require:

```sh
plugins/harness/scripts/plugin-self-check.ts
```
