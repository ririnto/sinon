# Harness Plugin Rules

These rules apply to `plugins/harness/`.
Normative keywords follow BCP 14.

## Package Boundary

This repository is the Harness plugin source, not an installed target repository.
Target-owned files such as root `ARCHITECTURE.md`, `WORKFLOW.md`, project docs, project agents, project skills, CI, and hooks MUST remain packaged under `skills/harness-install/assets/`.
They MUST NOT become active requirements at the Sinon repository root.

Plugin-root agents are structural Harness specialists.
Installed `.claude/agents/` and `.codex/agents/` files are day-to-day target repository leaves.

## Installed Instruction Contract

- Target `AGENTS.md` is the primary repository contract.
- Target `CLAUDE.md` MUST remain a pointer to target `AGENTS.md`.
- `WORKFLOW.md` owns branch, worktree, delegation, validation, review, and publication decisions.
- Installed Codex configuration MUST use `max_depth = 1`.
- Claude and Codex project agents MUST remain leaves and return decomposition handoffs instead of nesting.

## Installed Agent Routing

The target workflow distinguishes two implementation scopes:

- `scoped-implementer`: Haiku/Luna low for an exhaustive single-file or small related-file set.
  - It MUST NOT discover a broader affected set, make architecture choices, expand scope, delegate, integrate unrelated work, or publish.
  - Ambiguity MUST return to inventory or planning before assignment.
- `implementation`: Sonnet/Terra medium for affected-set discovery, related-file changes, large cross-file, module, or layer work, design choices, and integrated validation.
  - It owns one complete related-file scope in one worktree.
- `review`: Sonnet/Terra medium and read-only.
  - Fixes return to the owning writer and require re-review.

The scoped-implementer asset files are supplied by the concurrent Harness asset workstream.
Release integration MUST promote its pending entry in `scripts/agent-routing-manifest.json` into the canonical inventory when those files land.

## Orchestration Lifecycle

The user-facing root session is the sole orchestrator and MUST follow:

1. define a verifiable plan and acceptance criteria
2. classify difficulty and choose model and effort
3. run independent read-only work in parallel when useful
4. give each writer a disjoint scope, base commit, worktree, and validation target
5. wait for complete fan-in
6. return findings to the owning writer
7. re-review fixes
8. validate the integrated tree
9. publish only from the root session

A failed, missing, or contradictory worker blocks completion.
Overlapping writers MUST be serialized.
Worker-branch validation MUST NOT replace integrated validation.

## Asset Consistency

When one rule changes across installed docs, templates, agents, skills, validators, CI, hooks, or manifest inventory, update every owning surface together.
Do not weaken validation to hide drift.
Do not install fake product truth or generated artifacts without a reproducible source.

The checked-in asset manifest is deny-by-default.
Any added, removed, or renamed install asset requires regeneration with the packaged generator.

## Security Review

Review changed hooks, settings, scripts, MCP configuration, CI, and installer behavior for:

- command execution and shell injection
- path traversal and symlink escape
- filesystem mutation and ownership drift
- network and credential exposure
- implicit publication or Git-state mutation

Installed review and validation agents MUST NOT publish, push, tag, or edit marketplace metadata.

## Validation

Harness runtime, installer, validator, workflow, or packaged-asset changes require:

```sh
plugins/harness/scripts/plugin-self-check.ts
claude plugin validate plugins/harness
bun run check
```

The self-check MUST cover asset-manifest parity, installer outcomes, package surface, agent routing, native command parity, and executable modes.
