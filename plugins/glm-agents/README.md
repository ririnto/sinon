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
    Claude Code model settings:
      url: https://code.claude.com/docs/en/settings-reference.md#modelsettings
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
export API_TIMEOUT_MS='3000000'
```

Replace the key placeholder with your Z.ai API key.
`API_TIMEOUT_MS` is the value from Z.ai's setup and keeps long high and max effort runs from timing out client-side at Claude Code's ten-minute default.
Accounts that previously held a GLM Coding Plan subscription, including expired ones, can currently reach `glm-5.3` only through Z.ai's OpenAI-compatible protocol, as the [GLM-5.3 documentation](https://docs.z.ai/guides/llm/glm-5.3) states.
Those accounts cannot use the native endpoint and need a translation gateway or another eligible account.
If using a custom gateway instead, replace the base URL and credential with the values issued by its operator.
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
If an `availableModels` allowlist applies, it must include `glm-5.3` and `glm-5.3-flash`.
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

Z.ai documents a 1M-token context window and 128K maximum output tokens for both listed models.
A context window does not guarantee one million input tokens through every deployment.
For a conservative session-wide cap, set `CLAUDE_CODE_AUTO_COMPACT_WINDOW` to the smallest verified input limit among selected models and gateway.
Use no more than 922,000 tokens when the session can also select the GPT agents.

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
claude plugin install glm-agents@sinon
```

For local development, load the package in a new session:

```sh
claude --plugin-dir ./plugins/glm-agents
```

## Included Agents

| Agent | Model | Effort | Tier |
| --- | --- | --- | --- |
| `glm-flash` | `glm-5.3-flash[1m]` | `max` | 1 |
| `glm-high` | `glm-5.3[1m]` | `high` | 2 |
| `glm-max` | `glm-5.3[1m]` | `max` | 3 |

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
