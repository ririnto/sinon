# Agent Capability Kit Rules

These rules apply to agent and Agent Skill authoring under `plugins/agent-capability-kit/`.
Normative keywords follow BCP 14.

## Canonical Surfaces

- `agents/` is the canonical Claude source for shared Sinon agents.
- `.codex/agents/` at the repository root contains intentional Codex counterparts.
- `skills/agent-authoring/` owns Claude agent authoring guidance and copyable assets.
- `skills/skill-authoring/` owns portable Agent Skill guidance and copyable assets.
- `scripts/agent-routing-manifest.json` is the canonical routing and counterpart inventory.
- `scripts/agent-routing.ts` validates every repository agent against that inventory.

## Sinon Agent Contract

Every installable Sinon agent MUST:

- be a direct file under an `agents/` directory
- use a kebab-case filename and matching `name`
- provide a capability-first description with distinct trigger vocabulary
- declare `model` and `effort`
- state its leaf topology, process, boundaries, escalation, and output
- use the minimum tool boundary that supports the role
- remain self-contained on the ordinary path

Nested files below `agents/` are prohibited.
Optional depth belongs in a reusable skill or `docs/agent-references/<agent>/` and MUST have an exact blocker-based pointer.

## Model and Effort

Sinon packages only leaf agents.

| Leaf class | Claude | Codex | Effort |
| --- | --- | --- | --- |
| Substantive implementer, reviewer, validator, or domain expert | `sonnet` | `gpt-5.6-terra` | `medium` |
| Lightweight inventory or exhaustive mechanical edit | `haiku` | `gpt-5.6-luna` | `low` |

The user-facing top-level session reserves `opus` and `gpt-5.6-sol` with `medium` effort.
Those values MUST NOT be selected in installable Sinon agent runtime fields.

Use `high`, `xhigh`, or `max` only when the body includes an `Effort Exception` section.
A substantive leaf using `low` MUST include a `Low Effort Rationale` section.

Current Claude documentation does not list Haiku as effort-aware.
Haiku agents still declare `effort: low`, but documentation and validators MUST report the value as runtime-inert compatibility metadata rather than effective reasoning control.

Codex TOML MUST use `model_reasoning_effort`.
Intentional counterparts MUST use the exact model pair, the same effort, equivalent description, and identical instruction body.

## Topology and Tools

- Every installable agent is a leaf.
- Leaf agents MUST NOT expose `Agent`, `Task`, or child allowlists.
- Skill loading is allowed for domain routers and is not subagent delegation.
- Read-only roles MUST NOT expose `Edit`, `Write`, `MultiEdit`, or `NotebookEdit`.
- Codex report-only counterparts MUST use `sandbox_mode = "read-only"`.
- Codex writers MUST use `sandbox_mode = "workspace-write"`.
- A leaf that discovers parallel or integration work MUST return a decomposition handoff to the top-level session.

Review fixes return to the owning writer and MUST receive re-review.
Agents MUST NOT publish, push, or claim integrated completion.

## Supported Claude Plugin Fields

Plugin agents may use:

- `name`
- `description`
- `model`
- `effort`
- `tools`
- `disallowedTools`
- `maxTurns`
- `skills`
- `memory`
- `background`
- `isolation`
- `color`
- `initialPrompt`

Plugin agents MUST NOT rely on `hooks`, `mcpServers`, or `permissionMode`; Claude ignores those fields for plugin-loaded agents.
`Examples` is not a supported frontmatter field.

## Portable Agent Skill Contract

When a task creates, edits, reviews, refactors, validates, or packages an Agent Skill, load `skills/skill-authoring/SKILL.md` completely before acting.

Portable required frontmatter fields are:

- `name`
- `description`

The Agent Skills schema also permits optional `license`, `compatibility`, `metadata`, and experimental `allowed-tools`.
Sinon house style uses only `name` and `description` unless the task and repository policy explicitly require a portable optional field.

Agent runtime fields `model`, `effort`, and `model_reasoning_effort` MUST NOT appear in Agent Skill frontmatter.

## Trigger Description

A skill description MUST:

1. open with an imperative capability statement
2. add a `Use when ...` clause only when it contributes distinct tasks, artifacts, systems, timing, or user intent
3. avoid repeating capability verbs as trigger synonyms
4. remain valid outside one host unless intentionally host-specific

## `SKILL.md` Common Path

`SKILL.md` MUST be usable by itself for ordinary work, including offline use.
It MUST contain:

- activation surface and coherent scope
- first safe checks and commands
- main workflow and decisions
- representative copy-adaptable examples
- invariants, pitfalls, edge cases, and stop conditions
- format-critical output shape
- brief support-file pointers indexed by concrete blockers

`SKILL.md` MUST NOT become a routing stub, generic essay, or prerequisite chain to another skill.

## Support Files

Use:

- `references/` for optional host variants, troubleshooting, compatibility boundaries, and extended examples
- `assets/` for copyable templates and starter artifacts
- `scripts/` for deterministic, non-interactive helpers

Each reference MUST state when to open it and stand alone for one blocker or job.
References MUST NOT repeat the common workflow or become mandatory hidden prerequisites.
Commonly co-read references SHOULD be merged when the split does not reduce scanning cost.

Coding-related skills MUST weight code, commands, templates, configuration, and concrete output over explanatory prose.

## Validation

Before completing agent changes:

```sh
bun scripts/agent-routing.ts
claude plugin validate plugins/agent-capability-kit
```

Before completing Agent Skill changes, use `skills/skill-authoring/assets/validation-checklist.md` and run the repository checks.
A review-driven modification MUST receive a follow-up review over the same scope.
