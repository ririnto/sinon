---
name: agent-frontmatter
description: >-
  Supported Claude Code agent fields and plugin-specific restrictions for non-default runtime behavior.
---

# Agent Frontmatter

Open this reference when an agent needs field-level compatibility detail or a Codex counterpart.

## Field Matrix

| Field | Plugin agent | User or project agent | Notes |
| --- | --- | --- | --- |
| `name` | supported | supported | Required; Sinon also requires filename match |
| `description` | supported | supported | Required routing surface |
| `tools` | supported | supported | Omission inherits tools |
| `disallowedTools` | supported | supported | Removes inherited or specified tools |
| `model` | supported | supported | Alias, full ID, or `inherit` |
| `effort` | supported | supported | Available values depend on model |
| `maxTurns` | supported | supported | Positive turn bound |
| `skills` | supported | supported | Preloads full skill content |
| `memory` | supported | supported | `user`, `project`, or `local` |
| `background` | supported | supported | Boolean |
| `isolation` | supported | supported | Only `worktree` |
| `color` | supported | supported | Display field |
| `initialPrompt` | supported | supported | Auto-submitted only when the agent runs as the main session agent |
| `hooks` | ignored | supported | Do not put on plugin agents |
| `mcpServers` | ignored | supported | Do not put on plugin agents |
| `permissionMode` | ignored | supported | Do not put on plugin agents |

`Examples` is not a frontmatter field in either scope.

## Optional Field Example

```yaml
---
name: migration-runner
description: >-
  Apply and verify a bounded repository migration in an isolated worktree.
  Use this agent when a multi-file migration needs direct edits, focused checks, and worktree isolation.
model: sonnet
effort: medium
tools:
  - Read
  - Glob
  - Grep
  - Edit
  - Bash
maxTurns: 40
isolation: worktree
---
```

Each optional field needs a concrete role requirement.
Remove a field that does not change execution.

## Main-Session Startup

`initialPrompt` applies when the agent runs as the main session agent through `--agent` or the `agent` setting.
Claude Code processes commands and skills in the value, prepends it to any user-provided prompt, and does not use it as the delegated subagent prompt.

```yaml
initialPrompt: >-
  Inspect the current repository state and report the highest-priority validation failure.
```

## Skill Preloading

`skills` injects the complete named skill content when the agent starts.
Use it only when that content is a deliberate part of every invocation.
Use the `Skill` tool instead when a domain router should load only the skill selected at runtime.
On-demand Skill invocation and frontmatter preloading are complementary, not interchangeable.

## Model and Effort

Every Sinon Claude agent declares `model` and `effort`.

| Topology | Model | Default effort |
| --- | --- | --- |
| Substantive leaf | `sonnet` | `medium` |
| Lightweight inventory or mechanical leaf | `haiku` | `low` |

Sinon reserves `opus` and `gpt-5.6-sol` for the user-facing root session and does not package that orchestrator as an agent file.

Use `high`, `xhigh`, or `max` only with a body section named `Effort Exception`.
Use low on a substantive leaf only with a body section named `Low Effort Rationale`.

Current Claude documentation does not list Haiku as effort-aware.
Declare `effort: low` for explicit routing, but treat it as runtime-inert compatibility metadata.

Claude resolves subagent model selection in this order:

1. `CLAUDE_CODE_SUBAGENT_MODEL`
2. per-invocation model selection
3. agent frontmatter `model`
4. parent model inheritance

Runtime `availableModels` policy may further restrict selection.
Frontmatter therefore declares the repository route; it does not prove the final backend identity, especially behind a model gateway.
Treat backend identity as unobservable unless the runtime event explicitly reports the resolved backend model.

## Codex Counterpart

Use these exact pairs:

| Claude | Codex |
| --- | --- |
| `sonnet` | `gpt-5.6-terra` |
| `haiku` | `gpt-5.6-luna` |

The top-level runtime policy maps `opus` to `gpt-5.6-sol`, but neither value belongs in a Sinon installable agent.

Codex TOML uses:

```toml
model = "gpt-5.6-terra"
model_reasoning_effort = "medium"
```

Keep paired descriptions and instruction bodies semantically identical.
Use `sandbox_mode = "read-only"` for report-only roles and `workspace-write` only for writers or the main orchestrator.

## Scope Check

Before retaining an existing field, determine whether the file is loaded from a plugin, `.claude/agents/`, or `~/.claude/agents/`.
A field that is valid for a project agent may still be ignored for a plugin agent.
