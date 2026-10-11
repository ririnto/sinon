---
description: >-
  Claude Code subagent definitions that run on OpenAI GPT models.
metadata:
  reference:
    Claude Code subagents:
      url: https://code.claude.com/docs/en/sub-agents.md
    Claude Code plugin components:
      url: https://code.claude.com/docs/en/plugins/manifest-reference.md
---

# GPT Agents

This plugin provides Claude Code subagents whose `model` fields select OpenAI GPT models.

## Runtime

Claude Code loads the agents from the plugin's default `agents/` directory.
Each agent's `model` value must be available from the model provider configured for the Claude Code session.
Each agent's `effort` value sets its reasoning effort.
Codex lists the plugin through its native manifest but loads no components from it.

## Install And Use

Install the plugin from the Sinon marketplace in Claude Code:

```sh
claude plugin marketplace add ririnto/sinon
claude plugin install gpt-agents@sinon
```

For local development, load the package in a new session:

```sh
claude --plugin-dir ./plugins/gpt-agents
```

## Included Agents

| Agent | Model | Effort | Purpose |
| --- | --- | --- | --- |
| `luna` | `gpt-6-luna` | `xhigh` | Easy, bounded tasks. |
| `sol-medium` | `gpt-6.1-sol` | `medium` | Coding and everyday tasks. |
| `sol-high` | `gpt-6.1-sol` | `high` | Harder coding and reasoning tasks. |

## Package Contents

- The Sinon Claude marketplace entry declares the plugin metadata.
- `.codex-plugin/plugin.json` declares the Codex plugin identity.
- `agents/` contains the Claude Code subagent definitions.
