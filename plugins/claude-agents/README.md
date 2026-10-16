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
When Workgraph governs orchestration, read its installed Main Agent Contract before selecting agents.
Read its matching host reference, `references/claude.md` or `references/codex.md`, before selecting models or effort.

## Provider Prerequisites

The Codex session's model provider must serve `claude-haiku-5-5`, `claude-sonnet-5-5`, `claude-opus-5-5`, and `claude-fable-5-1`.
Codex providers call a Responses-compatible `POST /v1/responses` route, so a bare Anthropic Messages endpoint does not satisfy them.
Point the provider at a gateway that exposes `/v1/responses` and translates it to the Claude Messages API, with streaming and tool use.
The default OpenAI provider does not serve Claude models.
The plugin provides agent definitions only and does not configure a provider or credentials.
The gateway must translate `model_reasoning_effort` to the Claude effort setting and honor each listed value.

Codex's bundled model catalog does not list these Claude IDs, so Codex uses fallback metadata for them.
Supply a custom model catalog entry for each ID, with its context window and supported reasoning levels, through Codex's `model_catalog_json` setting.
Test each agent through the configured provider before relying on it.

## Install And Use

Add the Sinon marketplace and install the plugin in Codex to list and track the package:

```sh
codex plugin marketplace add ririnto/sinon --ref main
codex plugin add claude-agents@sinon
```

Installing does not place the agent files where Codex loads them.
Clone the repository and copy the definitions into an agent directory:

```sh
git clone https://github.com/ririnto/sinon.git
mkdir -p ~/.codex/agents
cp sinon/plugins/claude-agents/agents/*.toml ~/.codex/agents/
```

Use `.codex/agents/` inside a project instead to scope the agents to that project.

## Included Agents

| Agent | Model | Effort |
| --- | --- | --- |
| `haiku` | `claude-haiku-5-5` | `medium` |
| `sonnet` | `claude-sonnet-5-5` | `medium` |
| `opus` | `claude-opus-5-5` | `medium` |
| `fable` | `claude-fable-5-1` | `high` |

## Package Contents

- `.codex-plugin/plugin.json` declares the Codex plugin identity.
- `agents/` contains the Codex custom agent definitions.
- `LICENSE` contains the MIT license notice.
