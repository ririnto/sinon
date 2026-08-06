# Workgraph

Workgraph is a dependency-free plugin for Claude Code.

It keeps the session contract small and moves specialized behavior into focused Agent Skills.

It combines minimal implementation discipline with a bounded execution Graph.

It supports orchestration, Subagent isolation, recovery, and evidence-based completion.

## Session behavior

| Event | Injected context |
| --- | --- |
| `SessionStart: startup` | Full `WORKGRAPH_MAIN_V2` Main Agent contract from `session-core` |
| `SessionStart: clear` | Full `WORKGRAPH_MAIN_V2` Main Agent contract from `session-core` |
| `SessionStart: compact` | Small `WORKGRAPH_COMPACT_V2` recovery instruction that loads `session-core` only when the Main contract is absent |
| `SessionStart: resume` | No additional context |
| `SubagentStart` | Shared operating policy plus the `WORKGRAPH_SUBAGENT_V2` bounded-node contract |

The Main Agent owns the objective, graph selection, authority, integration, and final evidence.

Sub Agents receive a self-contained task boundary, keep raw working input and output local, and return a compact result schema.

## Default operating policy

`session-core` owns the shared policy.

The Hook extracts that policy for Subagent sessions.

This avoids a second policy source.

The policy supplies the default implementation and English technical authoring contract.

## Skills

- `artifact-authoring`: Use when Markdown, YAML, configuration, documentation, metadata, portability, or rendered quality matters.
- `instruction-authoring`: Use when creating or revising prompts, hooks, skills, agent contracts, or explicitly authorized durable memory.
- `minimal-implementation`: Use when reuse, dependency choice, module boundaries, or solution shape is materially undecided.
- `orchestration`: Use when work needs delegation, parallel lanes, recovery, ownership transfer, or multi-node synthesis.
- `session-core`: Load only when the compact recovery hook reports that `WORKGRAPH_MAIN_V2` is absent.

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

## Verify

Run the complete test suite:

```sh
node --test tests/*.test.mjs
```

The tests cover lifecycle routing, policy composition, LaTeX notation, prompt budgets, the manifest, Agent Skills frontmatter, one-level references, prohibited Skill and reference chains, and repeated long instruction lines.

The enforced injection limits are 2,200 characters for the Main Agent contract, 600 for compact recovery, and 2,400 for the Sub Agent contract.

## Layout

```text
workgraph/
+-- .claude-plugin/plugin.json
+-- hooks/
|   +-- hooks.json
|   +-- inject-context.mjs
|   +-- subagent-context.md
+-- skills/
|   +-- artifact-authoring/
|   +-- instruction-authoring/
|   +-- minimal-implementation/
|   +-- orchestration/
|   +-- session-core/
+-- tests/
```

## Design sources

The implementation is an original synthesis informed by Superpowers, Ponytail, Giver Architecture's `giver-principles.md`, the Agent Skills specification, the supplied local operating instructions, and current Claude Code plugin and prompting documentation.

Pinned upstream revisions are recorded in `THIRD_PARTY_NOTICES.md`.
