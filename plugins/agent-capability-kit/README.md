---
description: >-
  Overview of the Agent Capability Kit plugin, its authoring skills, runtime agents, and plugin packaging guidance.
---

# Agent Capability Kit

This package is a Claude Code authoring plugin that teaches three distinct domains:

- Claude Code plugin-root authoring for Sinon-style packages
- Claude Code agent authoring
- cross-platform Agent Skill authoring

It also includes four leaf runtime agents for validating, reviewing, creating, and inventorying plugin components.

All content in this package is written in English.

## Purpose

- Provide reusable authoring guidance for plugin roots, agents, and portable Agent Skills.
- Keep authoring rules usable from local files alone, without requiring live marketplace or SDK documentation.
- Share one capability kit for Claude Code plugin packaging while keeping runtime manifests thin.

## Included Skills

Six reusable skills for authoring Claude Code plugins and portable Agent Skills.

- `plugin-authoring`: build or refactor a plugin root with a Claude manifest, a plugin README, and only the runtime surfaces the plugin actually ships.
- `agent-authoring`: build or refactor reusable Claude Code agents.
- `skill-authoring`: build or refactor cross-platform Agent Skills.
- `hook-authoring`: author Claude Code plugin hooks with matchers, lifecycle events, and security guardrails.
- `mcp-integration`: integrate Model Context Protocol servers into Claude Code plugins via `.mcp.json` or the `mcpServers` manifest key.
- `plugin-settings`: author supported plugin settings, prompted `userConfig`, and plugin-defined per-project `.claude/<plugin>.local.md` state with safe YAML parsing.

## Included Agents

Four runtime agents with explicit model and effort routing.

- `plugin-validator`: validate a Claude Code plugin root against Sinon manifest, directory, agent, skill, and hook rules.
- `skill-reviewer`: review Agent Skills for self-sufficiency, coherent sizing, progressive disclosure, and adherence to blocker-based reference organization.
- `agent-creator`: create or refactor Claude Code agents with clear trigger descriptions, bounded tool access, and strong system prompts for autonomous work.
- `inventory-scanner`: inventory bounded repository surfaces and cite direct evidence without editing or making architecture decisions.

All installable agents are leaves.
The substantive agents use Claude Sonnet with medium effort.
The inventory scanner uses Claude Haiku with low effort as its declared route.
The user-facing top-level session owns orchestration and is not packaged as an agent.

## Runtime Model

This plugin uses `.claude-plugin/plugin.json` at the plugin root.

## Scope Notes

Covered authoring topics:

- plugin root structure, Claude manifests, and plugin README content
- optional runtime-surface configuration for hooks, MCP, LSP, monitors, output styles, and settings when a target plugin actually ships those surfaces
- authoring guidance for reusable agents under `skills/agent-authoring/`
- cross-platform Agent Skill structure under `skills/`

Explicitly excluded:

- OpenAI-specific capability design
- routing skills that only choose between sibling skills
- any skill that depends on another skill in this package
- SDK-specific workflows that replace portable Markdown, JSON, or YAML assets

## Defaults used in this kit

`https://agentskills.io/llms.txt` is the default basis for how this kit structures `SKILL.md`, `references/`, and `assets/`.

1. Each skill owns one domain and does not refer the reader to another skill in this package.
2. Always-needed guidance belongs in `SKILL.md`.
3. `references/` contains additive depth only and each file states when to open it.
4. Skill directory names and `name` values use lowercase letters, numbers, and single hyphens only, with a maximum length of 64 characters.
5. `description` explains both what the skill does and when to use it.
6. Each skill keeps a flat layout under `SKILL.md`, `references/`, and `assets/`.

## Installation

During local development, point Claude Code at the plugin root:

```sh
claude --plugin-dir /absolute/path/to/agent-capability-kit
```

Then use Quick navigation to choose the relevant namespaced skills or runtime agents.

## Quick navigation

- Plugin and skill authoring: `agent-capability-kit:plugin-authoring`, `agent-capability-kit:skill-authoring`
- Agent authoring: `agent-capability-kit:agent-authoring`
- Plugin infrastructure: `agent-capability-kit:hook-authoring`, `agent-capability-kit:mcp-integration`, `agent-capability-kit:plugin-settings`
- Validation and review: `plugin-validator` agent, `skill-reviewer` agent
- Lightweight evidence collection: `inventory-scanner` agent
- Plugin creation guidance: `agent-creator` agent

## Reuse the scaffolds

Copy the files you need from each skill's `assets/` directory:

- plugin examples from `plugin-authoring/assets/`
- agent examples from `agent-authoring/assets/`
- cross-platform skill examples from `skill-authoring/assets/`

## Plugin Layout

```text
agent-capability-kit/
+-- .claude-plugin/
|   +-- plugin.json
+-- README.md
+-- agents/
|   +-- agent-creator.md
|   +-- inventory-scanner.md
|   +-- plugin-validator.md
|   +-- skill-reviewer.md
+-- skills/
    +-- plugin-authoring/
    +-- agent-authoring/
    +-- skill-authoring/
    +-- hook-authoring/
    +-- mcp-integration/
    +-- plugin-settings/
```
