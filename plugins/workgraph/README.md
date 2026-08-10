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

The Main Agent is the orchestrator and terminal reporter.
It forms, routes, dispatches, coordinates, integrates, resolves conflicts, validates the control plane,
and performs source-control integration only when authorized.
It does not implement changes. Nodes own every implementation and task-side mutation, including small changes.
Before the first dispatch, it materializes the smallest inspectable execution Graph. The Graph makes topology,
ownership, predecessor dependencies, resource conflicts, branch identity, join readiness, and loop exit conditions explicit.
The Main Agent validates cycles and resource ownership, updates the Graph before each ready-set calculation,
and dispatches only nodes ready in the current Graph. Nodes receive only their local Graph slice and decision-bearing predecessor state.
Every required branch contributes a terminal outcome before a join, unless defined compensation satisfies that input.
Broad or uncertain discovery goes first to haiku scout nodes. Model aliases steer dispatch:
haiku for exploration, research, simple bounded implementation, or bounded review;
sonnet for complex or multi-file implementation, substantial lane planning, or comprehensive review;
opus for architecture and other high-intelligence decisions, never implementation; fable only when the user or dispatch explicitly requests it.
The Main Agent keeps exact-known narrow reads and required repository-wide integration gates.
The node contract enforces the authority and completion boundary of each dispatched node.
The injected Main and node contracts require English for all Agent-to-Agent communication.
`compact-context.md` only tells the Main Agent to load `main-agent-contract` when `WORKGRAPH_MAIN_V1` is absent.

## Skills

- `main-agent-contract`: Use when a compacted session lacks `WORKGRAPH_MAIN_V1` to restore the Workgraph Main Agent session invariants.
- `orchestration`: Use for every Workgraph node dispatch and for dependency, ownership, concurrency, join, or terminal result synthesis decisions.
- `recovery`: Use when a Workgraph node has interrupted or ambiguous identity, activity, ownership, or terminal state.

Each Skill is self-contained.
A Skill does not load another Skill.

The orchestration Skill owns Graph construction, dependency and join readiness, concurrency,
resource ownership, edge state, integration, and Graph completion.
The recovery Skill owns lifecycle inspection, terminal-outcome interpretation, retry decisions,
and resume, curate, or replace decisions.
Hooks inject context. They do not enforce ownership, prevent duplicate spawns, provide durable replay,
or guarantee exactly-once behavior.
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
