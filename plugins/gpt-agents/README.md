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
    Claude Code model settings:
      url: https://code.claude.com/docs/en/settings-reference.md#modelsettings
    Claude Code gateway configuration:
      url:
        - https://code.claude.com/docs/en/llm-gateway-connect.md
        - https://code.claude.com/docs/en/llm-gateway-protocol.md
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
This package is Claude Code only and is not listed in the Codex marketplace.
For Codex agents, use the `claude-agents` package.

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
Use `/tasks` to inspect a running agent's model and effort.
The inventory lists frontmatter values, including the context suffix.
Compare gateway diagnostics with the unsuffixed prerequisite IDs and the inventory's effort values.

These file-based agents were verified on Claude Code v2.1.299 with a local stub gateway.
Use v2.1.299 or later and recheck your gateway after host upgrades.
Earlier versions are unverified.
Their definitions provide model and effort configuration without a custom role prompt.
On these versions, `CLAUDE_CODE_SUBAGENT_MODEL` does not override a definition's `model`.

### Effort And Context

Leave `CLAUDE_CODE_EFFORT_LEVEL` unset so each agent's frontmatter controls effort.
Leave `CLAUDE_CODE_SUBAGENT_MODEL_FORCE` unset so Claude Code honors each definition's model instead of forcing a shared model.
Before launching a new session, clear an inherited override with:

```sh
unset CLAUDE_CODE_EFFORT_LEVEL
unset CLAUDE_CODE_SUBAGENT_MODEL_FORCE
```

Ensure user, project, and managed settings do not reintroduce either override when Claude Code starts.
If managed policy enforces an override, these definitions cannot guarantee their listed model and effort.
Each discovery description tells callers not to specify a model or effort override when selecting the agent.
This instruction guides callers but does not prevent per-invocation or host-enforced overrides.
Managed `maxEffortLevel` and any applicable organization effort limits must permit the requested level.
Otherwise Claude Code can clamp effort, and the listed tiers no longer imply distinct reasoning settings.
If an `availableModels` allowlist applies, it must include `gpt-6-luna`, `gpt-6.1-sol`, and `gpt-6-astra`.
Claude Code strips `[1m]` from both allowlist entries and requested IDs before matching.
Otherwise Claude Code substitutes another model for a blocked agent, and the listed tiers no longer hold.

The gateway must translate `output_config.effort` to the upstream model's supported reasoning setting and honor every effort listed below.
Verify distinct effort values in gateway request diagnostics without exposing credentials or conversation content.
A gateway that drops effort or rejects it can make the high and max agents behave identically.
Current Claude Code sends effort for unrecognized IDs behind `ANTHROPIC_BASE_URL`.
The `ANTHROPIC_DEFAULT_*_MODEL_SUPPORTED_CAPABILITIES` variables apply to cloud-provider configurations, not to this gateway connection.
See [gateway feature pass-through](https://code.claude.com/docs/en/llm-gateway-protocol.md#feature-pass-through) for effort forwarding and retry behavior.

The definitions append `[1m]` to select Claude Code's 1M context window for these custom model IDs.
A local stub gateway test on Claude Code v2.1.299 observed requests with the suffix removed.
Configured effort was preserved for a main-session agent and a subagent.
No live provider was called.
Verify this behavior after host upgrades before relying on unsuffixed gateway routing.
The documented provider-request stripping applies to pinned default-model variables, not explicitly to custom IDs in subagent frontmatter.

Leave `CLAUDE_CODE_DISABLE_1M_CONTEXT` unset to retain the 1M estimate.
Setting it to `1` sizes these IDs like their untagged forms, normally 200K without a context-window override.

OpenAI documents a 1,050,000-token context window, a 922,000-token maximum input, and 128,000 maximum output tokens for each listed model.
The suffix does not increase the upstream input limit.
For a conservative session-wide cap, use the verified deployment input limit, no higher than 922,000 tokens:

```sh
export CLAUDE_CODE_AUTO_COMPACT_WINDOW=922000
```

Use a lower value if any selected model or gateway has a smaller input limit.

The environment override is session-wide, so account for the main model and every selectable subagent.
On v2.1.288 or later, `/autocompact` saves a window for the current main-session model under `modelSettings`.
Custom-ID matching and use of these entries for subagent compaction remain unverified.
For these agents, retain the session-wide environment cap instead of relying on per-model entries.
The environment override takes precedence over per-model windows.
Keep proactive compaction enabled and verify the deployment's output allowance separately.
`CLAUDE_CODE_MAX_CONTEXT_TOKENS` alone does not change Claude Code's 1M estimate for IDs carrying `[1m]`.

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
| `luna-high` | `gpt-6-luna[1m]` | `high` | 1 |
| `luna-max` | `gpt-6-luna[1m]` | `max` | 2 |
| `sol-medium` | `gpt-6.1-sol[1m]` | `medium` | 3 |
| `sol-high` | `gpt-6.1-sol[1m]` | `high` | 3 |
| `astra` | `gpt-6-astra[1m]` | `high` | 4 |

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
- `agents/` contains the Claude Code subagent definitions.
- `LICENSE` contains the MIT license notice.
