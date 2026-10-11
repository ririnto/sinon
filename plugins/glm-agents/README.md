---
description: >-
  Claude Code subagent definitions that run on Z.ai GLM models.
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
    Z.ai Claude Code setup:
      url: https://docs.z.ai/devpack/tool/claude.md
    Z.ai models:
      url:
        - https://docs.z.ai/guides/llm/glm-5.3.md
        - https://docs.z.ai/guides/vlm/glm-5.3-flash.md
---

# GLM Agents

This plugin provides Claude Code subagents whose `model` fields select Z.ai GLM models.

## Runtime

Claude Code loads the agents from the plugin's default `agents/` directory.
Each agent's `effort` value requests its reasoning effort through the configured gateway.
Codex lists the plugin through its native manifest but loads no components from it.
Codex plugin manifests declare skills, MCP servers, apps, and hooks, but not custom agents, so the package ships no Codex agent definitions.

## Gateway Prerequisites

Before installing, connect Claude Code to Z.ai's native Anthropic-compatible endpoint or a gateway that routes `glm-5.3` and `glm-5.3-flash` to Z.ai.
The gateway must expose `/v1/messages`, support streaming and tool use, and translate Anthropic-format requests and responses to the upstream provider's format.
Z.ai provides that compatibility at `https://api.z.ai/api/anthropic`, so a separate translator is not required for the vendor-supported connection.
The OpenAI-format Z.ai endpoint is not interchangeable with this Anthropic-compatible endpoint.
Anthropic does not support routing Claude Code to non-Claude models through a gateway.
The plugin provides agent definitions only and does not configure a gateway or credentials.

Follow the gateway operator's setup instructions and the [Claude Code connection guide](https://code.claude.com/docs/en/llm-gateway-connect.md).
For Z.ai's native endpoint, follow its [official Claude Code setup](https://docs.z.ai/devpack/tool/claude.md) and set:

```sh
export ANTHROPIC_BASE_URL='https://api.z.ai/api/anthropic'
export ANTHROPIC_AUTH_TOKEN='<zai-api-key>'
```

Replace the key placeholder with your Z.ai API key.
If using a custom gateway instead, replace the base URL and credential with the values issued by its operator.
Use `ANTHROPIC_API_KEY` instead if the gateway requires `x-api-key` authentication.
Confirm the gateway URL in `/status` and test each agent through the gateway before relying on its tier.

### Effort And Context

Leave `CLAUDE_CODE_EFFORT_LEVEL` unset so each agent's frontmatter controls effort.
Before launching a new session, clear an inherited override with:

```sh
unset CLAUDE_CODE_EFFORT_LEVEL
```

Do not supply a per-invocation effort override when selecting an agent by its listed effort.
Managed `maxEffortLevel` and any applicable organization effort limits must permit the requested level.
Otherwise Claude Code can clamp effort, and the listed tiers no longer imply distinct reasoning settings.

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
