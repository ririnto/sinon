---
name: agent-frontmatter
description: >-
  Supported Claude Code agent fields and plugin-specific restrictions for non-default runtime behavior.
---

# Agent Frontmatter

Open this reference when an agent needs runtime fields beyond `name`, `description`, `color`, and a simple `tools` list.

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
Do not use `tools: [Skill]` as a substitute for preloading.

## Model and Effort

Omit `model` and `effort` when session inheritance is correct.
Pin them only when the role needs stable capability or cost behavior and the selected model supports the requested effort value.

## Scope Check

Before retaining an existing field, determine whether the file is loaded from a plugin, `.claude/agents/`, or `~/.claude/agents/`.
A field that is valid for a project agent may still be ignored for a plugin agent.
