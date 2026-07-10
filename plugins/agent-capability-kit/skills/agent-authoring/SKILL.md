---
name: agent-authoring
description: >-
  Create or refactor Claude Code plugin agents with clear triggers, supported frontmatter, bounded tools, and self-contained prompts.
  Use when authoring `agents/*.md`, choosing agent runtime fields, or aligning an agent's process with its tool boundary.
---

# Agent Authoring

Build one reusable Claude Code plugin agent whose description routes reliably and whose
prompt can execute its bounded role without hidden prerequisites.

Sinon agents MUST declare a model and effort.
The repository uses explicit routing so runtime cost and capability do not depend on a caller default.

## Owned Surface

- plugin-root `agents/<name>.md`
- frontmatter discovery and runtime fields
- system-prompt structure
- tool and autonomy boundaries
- output contracts and inline examples

For Sinon plugins, the filename stem and frontmatter `name` MUST match exactly and both MUST use kebab-case.

## Supported Plugin-Agent Fields

This field surface was verified with Claude Code 2.1.206.

Plugin agents require:

- `name`
- `description`

Sinon additionally requires:

- `model`
- `effort`

Plugin agents may use:

| Field | Use |
| --- | --- |
| `tools` | Explicit allowlist; omit to inherit available tools |
| `disallowedTools` | Remove tools from the inherited or explicit set |
| `model` | Pin a supported model or use `inherit` |
| `effort` | Override session effort where the selected model supports it |
| `maxTurns` | Bound agentic turns |
| `skills` | Preload complete skill content at startup |
| `memory` | Use `user`, `project`, or `local` persistent memory |
| `background` | Always run the agent as a background task when `true` |
| `isolation` | Use `worktree` for temporary Git worktree isolation |
| `color` | Distinguish the agent in the interface |
| `initialPrompt` | Auto-submit a first user turn when the agent runs as the main session agent |

Plugin agents MUST NOT rely on `hooks`, `mcpServers`, or `permissionMode`; Claude Code ignores those fields for agents loaded from a plugin.
Those fields are available only on user- or project-scoped agents.

`Examples` is not a supported frontmatter field.
Place short examples in the Markdown body when they materially improve execution.

Open `references/agent-frontmatter.md` for field-specific constraints and plugin-versus-project differences.

## Model and Effort Routing

Classify topology before choosing a model:

| Role | Claude | Codex counterpart | Effort |
| --- | --- | --- | --- |
| Substantive implementer, reviewer, validator, or domain expert | `sonnet` | `gpt-5.6-terra` | `medium` |
| Lightweight inventory or exhaustive mechanical edit | `haiku` | `gpt-5.6-luna` | `low` |

Sinon does not package a general orchestrator agent.
The user-facing top-level session owns orchestration and reserves Claude `opus` or Codex
`gpt-5.6-sol` with `medium` effort through repository policy.
It delegates all task work to bounded leaves and keeps decomposition, ownership, fan-in,
conflict coordination, and release decisions at the top level.

Use `high`, `xhigh`, or `max` only when the agent body contains an `Effort Exception` section that explains why medium is insufficient.
A substantive leaf using `low` needs a `Low Effort Rationale` section.

Claude Code accepts an `effort` declaration with Haiku, but the current official model-effort table does not list Haiku as effort-aware.
Keep `effort: low` for explicit repository routing and report it as runtime-inert
compatibility metadata rather than claiming it changes Haiku reasoning.

Codex counterparts use `model_reasoning_effort`, not `effort`:

```toml
model = "gpt-5.6-terra"
model_reasoning_effort = "medium"
```

Do not put runtime `model` or `effort` fields in portable Agent Skill frontmatter.

## Description Contract

Use the description as the routing surface:

1. Open with an imperative capability clause.
2. Add `Use this agent when ...` only when it contributes distinct assets, systems, timing, or task vocabulary.
3. Keep one coherent responsibility.
4. Avoid implementation steps and release notes.

Weak:

```yaml
description: Helps with schemas.
```

Strong:

```yaml
description: >-
  Inspect schemas and configuration files for defects and missing constraints.
  Use this agent when a schema or config needs focused read-only review before implementation or release.
```

## Choose the Tool Boundary

Start from the ordinary process, then grant the narrowest tools that can perform it.

- Read-only reviewers usually need `Read`, `Glob`, and `Grep`.
- Editors need discovery plus the minimal mutation tool their process uses.
- Agents that run validation need `Bash` only when commands are part of the role.
- Omit `tools` only when inheriting the complete caller tool set is intentional.
- Use `disallowedTools` when inheritance is useful but a small set must be removed.

Do not claim repository discovery if the agent lacks `Glob` or `Grep`.
Do not claim file edits if the agent lacks a mutation tool.
Do not claim runtime verification if the agent lacks the command or connector surface required to perform it.

## Minimal Plugin Agent

```markdown
---
name: schema-reviewer
description: >-
  Inspect schemas and configuration files for defects and missing constraints.
  Use this agent when a schema or config needs focused read-only review before implementation or release.
model: sonnet
effort: medium
color: cyan
tools:
  - Read
  - Glob
  - Grep
---

# Schema Reviewer

Inspect the requested schema or configuration surface and report evidence-backed findings without modifying files.

## Responsibilities

1. Locate the files in the requested scope.
2. Check syntax, field contracts, and cross-file consistency.
3. Support each finding with direct file evidence.

## Process

1. Read local rules and the target files.
2. Search for declarations and consumers of the affected fields.
3. Report material findings in priority order.
4. Recheck every cited path and line before returning.

## Output

Return:

1. findings ordered by severity
2. file evidence for each finding
3. remaining uncertainty or blockers
```

## Authoring Procedure

1. Read the target plugin rules, README, existing agent, and sibling naming conventions.
2. Classify the packaged agent as a substantive leaf or a lightweight inventory or mechanical leaf.
3. State the agent's one responsibility in a sentence.
4. Choose a stable kebab-case name and matching filename.
5. Write the capability and trigger description.
6. Assign the required model and effort from the routing table.
7. Select the smallest tool boundary that supports discovery, action, and verification.
8. Write a self-contained body with role, topology, responsibilities, process, quality rules, edge cases, escalation, and output.
9. Add one short inline example when format or decision behavior would otherwise be ambiguous.
10. Verify that every process verb is possible with the declared tools and supported plugin fields.
11. Validate frontmatter and run `claude plugin validate <plugin-root>`.

## Additional Runtime Decisions

- Set `maxTurns` when an otherwise bounded workflow can loop indefinitely.
- Use `skills` only when full skill content is an intentional startup prerequisite; keep the agent prompt understandable about what that content contributes.
- Use `memory` only when cross-session learning is part of the role and its scope is appropriate.
- Use `background: true` only when background execution is always correct for the agent.
- Use `isolation: worktree` only when Git worktree isolation fits the mutation and cleanup model.
- Use `initialPrompt` only when the agent is selected as the main session agent through `--agent` or the `agent` setting.
  Claude Code prepends it to any user-provided prompt and processes commands and skills in the value.

## Prompt Structure

Keep the body direct and executable:

1. role statement
2. `Responsibilities`
3. `Process`
4. quality or safety rules when needed
5. edge cases or stop conditions
6. `Output`

The agent body MUST contain ordinary-path guidance.
Use frontmatter `skills` only when every invocation needs the full skill content at startup.
An agent with the `Skill` tool MAY load a matched project, user, or plugin skill on demand;
domain routers SHOULD prefer that progressive-disclosure path.
In either case, keep the agent's role, decision boundary, and output shape explicit.

## Topology and Delegation

The user-facing root session is the sole general orchestrator and is not packaged as an agent profile.
Every Sinon agent file is a leaf and MUST NOT expose `Agent` or `Task` delegation tools.
Skill loading is not subagent delegation.

When a leaf discovers work that needs decomposition, integration, or parallel writers, it
returns a decomposition handoff to the root session.
Current Claude Code supports nested subagents up to depth 5 when `Agent` is granted, but
Sinon deliberately omits delegation tools from installable agents.
Installed Codex workflows use `max_depth = 1`.

Read-only reviewers and validators MUST NOT expose mutation tools.
Writers receive one explicit ownership scope; overlapping writers are serialized or isolated in separate non-overlapping worktrees.

## Validation

- filename stem and `name` match for Sinon plugins
- `name` is kebab-case
- description has a capability and distinct trigger vocabulary
- only supported plugin-agent fields are used
- `model` and `effort` are present and match the declared topology
- any Codex counterpart uses the mapped slug and `model_reasoning_effort`
- Haiku effort is reported as runtime-inert compatibility metadata
- high-or-greater effort has a written exception
- no plugin agent relies on `hooks`, `mcpServers`, or `permissionMode`
- tool list supports file discovery, action, and verification claims
- optional runtime fields have a concrete reason
- `initialPrompt`, when present, is intended for main-session startup rather than delegated subagent execution
- leaf agents expose no delegation tools
- no installable agent claims general orchestration or child ownership
- read-only roles expose no mutation tools
- body is self-contained and bounded to one role
- examples live in the body, not `Examples` frontmatter
- output shape is explicit
- `claude plugin validate` reports no blocking error

## Output Contract

Return:

1. final agent path and Markdown
2. trigger rationale
3. tool and optional-field rationale
4. validation results
5. remaining risks or blockers

## Pitfalls

- Do not put `hooks`, `mcpServers`, or `permissionMode` on a plugin agent.
- Do not invent an `Examples` frontmatter field.
- Do not omit discovery tools from an agent expected to locate files.
- Do not grant broad mutation or Bash access for a report-only role.
- Do not hide required workflow instructions in another file.
- Do not omit model or effort from a Sinon agent.
- Do not assign Opus or Sol to an installable agent; reserve them for the user-facing root session.
- Do not claim Haiku effort is effective under the current official compatibility table.
- Do not use high-or-greater effort without a written exception.
- Do not place model or effort in portable Agent Skill frontmatter.
- Do not use `initialPrompt` as delegated subagent instructions; keep those instructions in the agent body.

## Support Files

- `references/agent-frontmatter.md` - open for supported-field details or plugin-versus-project scope.
- `references/agent-execution.md` - open for exceptional autonomy, isolation, background, or broad execution boundaries.
- `assets/agent-template.md` - copy when creating a new agent.
- `assets/agent-frontmatter-patterns.md` - copy when a role needs optional runtime fields.
