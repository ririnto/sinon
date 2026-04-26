# Agent Capability Kit

This package is a dual-runtime authoring plugin that teaches four distinct domains:

- Claude Code and Codex plugin-root authoring for Sinon-style packages
- Claude Code agent authoring
- Claude Code command and prompt authoring
- cross-platform Agent Skill authoring

All content in this package is written in English.

## Purpose

- Provide reusable authoring guidance for plugin roots, agents, commands, prompt files, and portable Agent Skills.
- Keep authoring rules usable from local files alone, without requiring live marketplace or SDK documentation.
- Share one capability kit across Claude Code and Codex-style plugin packaging while keeping runtime manifests thin.

## Included Skills

- `plugin-authoring`: build or refactor a plugin root with paired Claude/Codex manifests, a plugin README, and only the runtime surfaces the plugin actually ships.
- `agent-authoring`: build or refactor reusable Claude Code agents.
- `command-authoring`: build or refactor reusable Claude Code commands and prompt files.
- `skill-authoring`: build or refactor cross-platform Agent Skills.

## Included Agents

This plugin ships no plugin-root `agents/` directory. Agent examples and templates live inside the authoring skill assets instead of being exposed as runtime agents.

## Runtime Model

This plugin uses one shared plugin root with two thin runtime manifests:

- `.claude-plugin/plugin.json`
- `.codex-plugin/plugin.json`

Both manifests point at the same shared `skills/` content. The plugin root intentionally exposes skills only; it does not ship commands, hooks, MCP servers, LSP servers, monitors, or plugin-root agents.

## Scope Notes

Covered authoring topics:

- plugin root structure, paired runtime manifests, and plugin README content
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

## Skill index

- `plugin-authoring`: build or refactor a plugin root with paired Claude/Codex manifests, a plugin README, and minimal runtime surfaces.
- `agent-authoring`: build or refactor reusable Claude Code agents.
- `command-authoring`: build or refactor reusable Claude Code commands and prompt files.
- `skill-authoring`: build or refactor cross-platform Agent Skills.

## Quick start

During local development, point Claude Code at the plugin root:

```bash
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
├── .codex-plugin/
│   └── plugin.json
├── README.md
└── skills/
    ├── plugin-authoring/
    ├── agent-authoring/
    ├── command-authoring/
    └── skill-authoring/
```
