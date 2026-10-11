---
description: >-
  Claude Code subagent definitions that run on OpenAI GPT models.
metadata:
  reference:
    Claude Code subagents:
      url: https://code.claude.com/docs/en/sub-agents.md
    Claude Code plugin components:
      url: https://code.claude.com/docs/en/plugins/manifest-reference.md
    Claude Code model configuration:
      url: https://code.claude.com/docs/en/model-config.md
    Claude Code gateway configuration:
      url:
        - https://code.claude.com/docs/en/llm-gateway-connect.md
        - https://code.claude.com/docs/en/llm-gateway-protocol.md
    Codex plugins:
      url: https://developers.openai.com/codex/plugins/build.md
    OpenAI models:
      url:
        - https://developers.openai.com/api/docs/models/gpt-6-luna.md
        - https://developers.openai.com/api/docs/models/gpt-6.1-sol.md
        - https://developers.openai.com/api/docs/models/gpt-6-astra.md
---

# GPT Agents

This plugin provides Claude Code subagents whose `model` fields select OpenAI GPT models.

## Runtime

Claude Code loads the agents from the plugin's default `agents/` directory.
Each agent's `effort` value requests its reasoning effort through the configured gateway.
Codex lists the plugin through its native manifest but loads no components from it.
Codex plugin manifests declare skills, MCP servers, apps, and hooks, but not custom agents, so the package ships no Codex agent definitions.

## Gateway Prerequisites

Before installing, configure an Anthropic Messages-compatible gateway that routes `gpt-6-luna`, `gpt-6.1-sol`, and `gpt-6-astra` to OpenAI.
The gateway must expose `/v1/messages`, support streaming and tool use, and translate Anthropic-format requests and responses to the upstream provider's format.
Pointing Claude Code at the upstream OpenAI API without that compatibility layer is not sufficient.
Anthropic does not support routing Claude Code to non-Claude models through a gateway.
The plugin provides agent definitions only and does not configure a gateway or credentials.

Follow the gateway operator's setup instructions and the [Claude Code connection guide](https://code.claude.com/docs/en/llm-gateway-connect.md).
For a gateway that uses bearer authentication, set the following before launching Claude Code:

```sh
export ANTHROPIC_BASE_URL='https://gateway.example.com'
export ANTHROPIC_AUTH_TOKEN='<gateway-issued-token>'
```

Replace the placeholders with your gateway's base URL and credential.
Use `ANTHROPIC_API_KEY` instead if the gateway requires `x-api-key` authentication.
Confirm the gateway URL in `/status` and test each agent through the gateway before relying on its tier.

### Effort And Context

The gateway must translate `output_config.effort` to the upstream model's supported reasoning setting and honor every effort listed below.
Verify distinct effort values in gateway request diagnostics without exposing credentials or conversation content.
A gateway that drops effort or rejects it can make the high and max agents behave identically.
Current Claude Code sends effort for unrecognized IDs behind `ANTHROPIC_BASE_URL`.
The `ANTHROPIC_DEFAULT_*_MODEL_SUPPORTED_CAPABILITIES` variables apply to cloud-provider configurations, not to this gateway connection.
See [gateway feature pass-through](https://code.claude.com/docs/en/llm-gateway-protocol.md#feature-pass-through) for effort forwarding and retry behavior.

These raw custom model IDs default to a 200K context window in Claude Code.
To use a larger supported window, set `CLAUDE_CODE_MAX_CONTEXT_TOKENS` before launching Claude Code to the verified input-token limit of your gateway deployment.
For a deployment that supports at least one million input tokens on every selected model, use:

```sh
export CLAUDE_CODE_MAX_CONTEXT_TOKENS=1000000
```

The variable is session-wide, so use the smallest verified input window among all models the session and its subagents can select.
Do not infer an input limit from a model's combined input-and-output token allowance.
The plugin leaves model IDs unchanged and does not raise the context limit automatically.

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

| Agent | Model | Effort | Tier |
| --- | --- | --- | --- |
| `luna-high` | `gpt-6-luna` | `high` | 1 |
| `luna-max` | `gpt-6-luna` | `max` | 2 |
| `sol-medium` | `gpt-6.1-sol` | `medium` | 3 |
| `sol-high` | `gpt-6.1-sol` | `high` | 3 |
| `astra` | `gpt-6-astra` | `high` | 4 |

## Equivalence Tiers

These tiers are repository task-routing preferences, not vendor claims of equal performance or cost.
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
- `LICENSE` contains the MIT license notice.
