# Workgraph

Workgraph is a dependency-free plugin for Claude Code.

It keeps the session contract small and moves specialized behavior into focused Agent Skills.

It combines minimal implementation discipline with a bounded execution Graph.

It supports orchestration, Subagent isolation, recovery, and evidence-based completion.

## Session behavior

| Event | Injected context |
| --- | --- |
| `SessionStart: startup` | Full `WORKGRAPH_MAIN_V2` Main Agent contract from `skills/main-agent-contract/SKILL.md` |
| `SessionStart: clear` | Full `WORKGRAPH_MAIN_V2` Main Agent contract from `skills/main-agent-contract/SKILL.md` |
| `SessionStart: compact` | Complete `WORKGRAPH_COMPACT_V2` recovery context from `hooks/compact-context.md` |
| `SessionStart: resume` | No additional context |
| `SubagentStart` | Complete bounded-node context from `hooks/subagent-context.md` |

The hooks inject complete files for each supported event.

The Main Agent owns the objective, graph selection, authority, integration, and final evidence.

Sub Agents receive a self-contained task boundary, keep raw working input and output local, and return one terminal result in the payload-specified format, when present.

## Skills

- `main-agent-contract`: Canonical Workgraph Main Agent contract. Use after compaction when `WORKGRAPH_MAIN_V2` is absent.
- `orchestration`: Use when work needs delegation, parallel lanes, recovery, ownership transfer, or multi-node synthesis.

Each workflow is self-contained at the Skill level.

Optional references are one level deep and state the exact condition for loading them.

The payload contract keeps formal Graph relations in LaTeX.

## Requirements

- Node.js 18 or newer.
- A Claude Code release that supports plugin-bundled `SessionStart` and `SubagentStart` command hooks.

The runtime has no third-party package dependencies.

## Claude Code

Validate the plugin:

```sh
claude plugin validate /absolute/path/to/workgraph --strict
```

Load it for one session during development:

```sh
claude --plugin-dir /absolute/path/to/workgraph
```

Use `/hooks` to confirm that the `SessionStart` and `SubagentStart` handlers are active.

Restart Claude Code or run `/reload-plugins` after changing hooks or manifests.

## Layout

```text
workgraph/
+-- .claude-plugin/plugin.json
+-- hooks/
|   +-- hooks.json
|   +-- compact-context.md
|   +-- inject-context.mjs
|   +-- subagent-context.md
+-- skills/
    +-- main-agent-contract/
    +-- orchestration/
```

## Design sources

The implementation is an original synthesis informed by Superpowers, Ponytail, Giver Architecture's `giver-principles.md`, the Agent Skills specification, the supplied local operating instructions, and current Claude Code plugin and prompting documentation.

Pinned upstream versions are recorded in `THIRD_PARTY_NOTICES.md`.
