# Agent Capability Kit

This package is a Claude Code authoring plugin that teaches four distinct domains:

- Claude Code plugin-root authoring for Sinon-style packages
- Claude Code agent authoring
- Claude Code command and prompt authoring
- cross-platform Agent Skill authoring

It also includes three runtime agents for validating, reviewing, and creating plugin components, plus a command for guiding plugin creation workflows.

All content in this package is written in English.

## Purpose

- Provide reusable authoring guidance for plugin roots, agents, commands, prompt files, and portable Agent Skills.
- Keep authoring rules usable from local files alone, without requiring live marketplace or SDK documentation.
- Share one capability kit for Claude Code plugin packaging while keeping runtime manifests thin.

## Included Skills

Seven reusable skills for authoring Claude Code plugins and portable Agent Skills.

- `plugin-authoring`: build or refactor a plugin root with a Claude manifest, a plugin README, and only the runtime surfaces the plugin actually ships.
- `agent-authoring`: build or refactor reusable Claude Code agents.
- `command-authoring`: build or refactor reusable Claude Code commands and prompt files.
- `skill-authoring`: build or refactor cross-platform Agent Skills.
- `hook-authoring`: author Claude Code plugin hooks with matchers, lifecycle events, and security guardrails.
- `mcp-integration`: integrate Model Context Protocol servers into Claude Code plugins via `.mcp.json` or the `mcpServers` manifest key.
- `plugin-settings`: author plugin-level configuration via `settings.json` and per-project state with YAML frontmatter parsing patterns.

## Included Agents

Three runtime agents for validating, reviewing, and creating plugin components.

- `plugin-validator`: validate a Claude Code plugin root against Sinon manifest, directory, agent, skill, and hook rules.
- `skill-reviewer`: review Agent Skills for self-sufficiency, coherent sizing, progressive disclosure, and adherence to blocker-based reference organization.
- `agent-creator`: create or refactor Claude Code agents with clear trigger descriptions, bounded tool access, and strong system prompts for autonomous work.

## Included Commands

One command for guiding plugin creation workflows.

- `create-plugin`: end-to-end Claude Code plugin creation workflow with component design, implementation, and validation.

## Runtime Model

This plugin uses one shared plugin root with a thin Claude manifest:

- `.claude-plugin/plugin.json`

The manifest points at the shared `skills/`, `agents/`, and `commands/` content. The plugin root exposes skills, agents, and one command; it does not ship hooks, MCP servers, LSP servers, monitors, or custom runtime data surfaces.

## Scope Notes

Covered authoring topics:

- plugin root structure, Claude manifests, and plugin README content
- optional runtime-surface configuration for hooks, MCP, LSP, monitors, output styles, and settings when a target plugin actually ships those surfaces
- authoring guidance for reusable agents under `skills/agent-authoring/`
- authoring guidance for reusable commands and prompt files under `skills/command-authoring/`
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

## Quick navigation

- Plugin and skill authoring: `/agent-capability-kit:plugin-authoring`, `/agent-capability-kit:skill-authoring`
- Agent and command authoring: `/agent-capability-kit:agent-authoring`, `/agent-capability-kit:command-authoring`
- Plugin infrastructure: `/agent-capability-kit:hook-authoring`, `/agent-capability-kit:mcp-integration`, `/agent-capability-kit:plugin-settings`
- Validation and review: `plugin-validator` agent, `skill-reviewer` agent
- Plugin creation guidance: `/create-plugin` command, `agent-creator` agent

## Quick start

During local development, point Claude Code at the plugin root:

```sh
claude --plugin-dir /absolute/path/to/agent-capability-kit
```

Then invoke namespaced skills such as:

```text
/agent-capability-kit:plugin-authoring
/agent-capability-kit:agent-authoring
/agent-capability-kit:command-authoring
/agent-capability-kit:skill-authoring
```

## Reuse the scaffolds

Copy the files you need from each skill's `assets/` directory:

- plugin examples from `plugin-authoring/assets/`
- agent examples from `agent-authoring/assets/`
- command and prompt examples from `command-authoring/assets/`
- cross-platform skill examples from `skill-authoring/assets/`

## Package layout

```text
agent-capability-kit/
├── .claude-plugin/
│   └── plugin.json
├── README.md
├── agents/
│   ├── agent-creator.md
│   ├── plugin-validator.md
│   └── skill-reviewer.md
├── commands/
│   └── create-plugin.md
└── skills/
    ├── plugin-authoring/
    ├── agent-authoring/
    ├── command-authoring/
    ├── skill-authoring/
    ├── hook-authoring/
    ├── mcp-integration/
    └── plugin-settings/
```
