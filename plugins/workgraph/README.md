# Workgraph

Workgraph is a dependency-free Claude Code plugin.
It keeps common session behavior small and loads Graph mechanics only when a task needs them.

## Session Behavior

| Event | Injected context |
| --- | --- |
| `SessionStart: startup` | Full `WORKGRAPH_MAIN_V1` contract from `skills/main-agent-contract/SKILL.md` |
| `SessionStart: clear` | Full `WORKGRAPH_MAIN_V1` contract from `skills/main-agent-contract/SKILL.md` |
| `SessionStart: compact` | Main-contract reload check from `hooks/compact-context.md` |
| `SessionStart: resume` | No additional context |
| `SubagentStart` | Full `WORKGRAPH_SUBAGENT_V1` bounded-node contract from `hooks/subagent-context.md` |

The Main Agent contract owns the user outcome, scope, authority, progress cadence, evidence bar, stop condition, and terminal response.
The node contract enforces the authority and completion boundary of each dispatched node.
The injected Main and node contracts require English for all Agent-to-Agent communication.
`compact-context.md` only tells the Main Agent to load `main-agent-contract` when `WORKGRAPH_MAIN_V1` is absent.

## Skills

- `main-agent-contract`: Use when a compacted session lacks `WORKGRAPH_MAIN_V1`.
- `orchestration`: Use when sizeable work needs isolated node ownership, specialized capability, real parallel execution, dependency coordination, or multi-node result synthesis.
- `recovery`: Use when a Workgraph node has interrupted or ambiguous identity, activity, ownership, or terminal state.

Each Skill is self-contained.
A Skill does not load another Skill.

The orchestration Skill owns Graph selection, dependencies, concurrency, resource ownership, edge state, integration, and Graph completion.
The recovery Skill owns lifecycle inspection and resume, curate, or replace decisions.
The orchestration Skill has one direct reference:

- `payload-contracts.md`: Load before dispatching a node or accepting its terminal result.

## Requirements

- Node.js 22 or newer.
- A Claude Code release that supports plugin-bundled `SessionStart` and `SubagentStart` command hooks.

The runtime has no third-party package dependencies.
The package manifest omits `version` because global marketplace metadata owns it.

## Claude Code

Validate the plugin:

```sh
claude plugin validate /absolute/path/to/workgraph
```

Load it for one development session:

```sh
claude --plugin-dir /absolute/path/to/workgraph
```

Use `/hooks` to confirm that the `SessionStart` and `SubagentStart` handlers are active.
Restart Claude Code or run `/reload-plugins` after hook or manifest changes.

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
    |   +-- SKILL.md
    +-- orchestration/
    |   +-- SKILL.md
    |   +-- references/
    |       +-- payload-contracts.md
    +-- recovery/
        +-- SKILL.md
```

## Design Sources

The design is an original synthesis of the Agent Skills specification, Claude Code plugin guidance, and the supplied Claude Opus 5 and GPT-5.6 prompting guides.
Pinned upstream versions are recorded in `THIRD_PARTY_NOTICES.md`.
