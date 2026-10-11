---
description: >-
  Claude Code subagent definitions that run on Z.ai GLM models.
metadata:
  reference:
    Claude Code subagents:
      url: https://code.claude.com/docs/en/sub-agents.md
    Claude Code plugin components:
      url: https://code.claude.com/docs/en/plugins/manifest-reference.md
    Codex plugins:
      url: https://developers.openai.com/codex/plugins/build.md
---

# GLM Agents

This plugin provides Claude Code subagents whose `model` fields select Z.ai GLM models.

## Runtime

Claude Code loads the agents from the plugin's default `agents/` directory.
Each agent's `model` value must be available from the model provider configured for the Claude Code session.
Each agent's `effort` value sets its reasoning effort.
Codex lists the plugin through its native manifest but loads no components from it.
Codex plugin manifests declare skills, MCP servers, apps, and hooks, but not custom agents, so the package ships no Codex agent definitions.

## Install And Use

Install the plugin from the Sinon marketplace in Claude Code:

```sh
claude plugin marketplace add ririnto/sinon
claude plugin install glm-agents@sinon
```

For local development, load the package in a new session:

```sh
claude --plugin-dir ./plugins/glm-agents
```

## Included Agents

| Agent | Model | Effort | Tier |
| --- | --- | --- | --- |
| `glm-flash` | `glm-5.3-flash` | `max` | 1 |
| `glm-high` | `glm-5.3` | `high` | 2 |
| `glm-max` | `glm-5.3` | `max` | 3 |

## Equivalence Tiers

Pick an agent by tier when a task calls for a Claude model class.
Tier 4 agents run only on explicit request.

| Tier | Workload | Claude | `gpt-agents` | `glm-agents` |
| --- | --- | --- | --- | --- |
| 1 | Clear, bounded tasks | `haiku` | `luna-high` | `glm-flash` |
| 2 | Broader development and analysis | `sonnet` | `luna-max` | `glm-high` |
| 3 | Conflicting evidence and hard tradeoffs | `opus` | `sol-medium`, `sol-high` | `glm-max` |
| 4 | Hardest or longest work | `fable` | `astra` | None |

`sol-medium` and `sol-high` mirror the medium and high effort choices for Tier 3 work.

## Package Contents

- The Sinon Claude marketplace entry declares the plugin metadata.
- `.codex-plugin/plugin.json` declares the Codex plugin identity.
- `agents/` contains the Claude Code subagent definitions.
