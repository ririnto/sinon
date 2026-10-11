---
description: >-
  Codex custom agent definitions that run on Anthropic Claude models.
metadata:
  reference:
    Codex custom agents:
      url: https://learn.chatgpt.com/docs/agent-configuration/subagents.md
    Codex plugins:
      url: https://developers.openai.com/codex/plugins/build.md
    Claude models:
      url: https://platform.claude.com/docs/en/about-claude/models/overview.md
---

# Claude Agents

This plugin provides Codex custom agents whose `model` fields select Anthropic Claude models.

## Runtime

Codex reads custom agents from `~/.codex/agents/` for personal agents or `.codex/agents/` for project-scoped agents.
Codex plugin manifests declare skills, MCP servers, apps, and hooks, but not custom agents, so installing the plugin does not load these agents.
Copy the definitions into an agent directory to enable them.
Each file sets `model` and `model_reasoning_effort`, which take precedence over the session defaults.

This package is Codex-only.
Claude Code reserves plugin names that start with `claude-`, so the Sinon Claude marketplace does not list it.

## Provider Prerequisites

The Codex session's model provider must serve `claude-haiku-5-5`, `claude-sonnet-5-5`, `claude-opus-5-5`, and `claude-fable-5-1`.
The default OpenAI provider does not serve Claude models, so configure a provider whose endpoint routes these IDs before selecting the agents.
The plugin provides agent definitions only and does not configure a provider or credentials.
The provider must translate `model_reasoning_effort` to the Claude effort setting and honor each listed value.
Test each agent through the configured provider before relying on its tier.

## Install And Use

Add the Sinon marketplace and install the plugin in Codex:

```sh
codex plugin marketplace add ririnto/sinon --ref main
codex plugin add claude-agents@sinon
```

Enable the agents from a checkout of this repository:

```sh
mkdir -p ~/.codex/agents
cp plugins/claude-agents/agents/*.toml ~/.codex/agents/
```

Use `.codex/agents/` inside a project instead to scope the agents to that project.

## Included Agents

| Agent | Model | Effort | Tier |
| --- | --- | --- | --- |
| `haiku` | `claude-haiku-5-5` | `medium` | 1 |
| `sonnet` | `claude-sonnet-5-5` | `medium` | 2 |
| `opus` | `claude-opus-5-5` | `medium` | 3 |
| `fable` | `claude-fable-5-1` | `high` | 4 |

These tiers follow the Sinon equivalence tiers in the `gpt-agents` and `glm-agents` packages.
They are repository task-routing preferences, not vendor claims of equal performance or cost.
Tier 4 agents run only on explicit request.

## Package Contents

- `.codex-plugin/plugin.json` declares the Codex plugin identity.
- `agents/` contains the Codex custom agent definitions.
- `LICENSE` contains the MIT license notice.
