# OpenAI Symphony Reference

Open this reference when a target repository wants to understand Symphony-style orchestration boundaries and how they relate to repository harness setup.

## Source

| Field | Value |
| --- | --- |
| Source | OpenAI Symphony article and SPEC.md |
| URL | `https://openai.com/ko-KR/index/open-source-codex-orchestration-symphony/` |
| SPEC URL | `https://github.com/openai/symphony/blob/main/SPEC.md` |
| Retrieval date | 2026-05-09 |
| Local use | Boundary reference for repository harness setup, not a required runtime dependency |

## What Symphony is

Symphony is a long-running automation service that continuously reads work from an issue tracker (Linear in the current spec version), creates an isolated workspace for each issue, and runs a coding agent session inside that workspace.

## Key characteristics

- Turns issue execution into a repeatable daemon workflow instead of manual scripts.
- Isolates agent execution in per-issue workspaces.
- Keeps workflow policy in repository-owned `WORKFLOW.md` so teams version agent prompts and runtime settings with their code.
- Provides observability for multiple concurrent agent runs.
- Is a scheduler/runner and tracker reader, not a general workflow engine.
- Ticket writes (state transitions, comments, PR links) are performed by the coding agent using runtime tools.

## Boundary for this plugin

This plugin does **not** implement Symphony, run a daemon, poll an issue tracker, or manage per-issue workspaces. The plugin's job is to install the repository-local docs, configuration, validation, and evidence surfaces that make Symphony-style orchestration safer when a target repository adopts it.

## What the plugin provides for Symphony readiness

| Symphony need | Plugin expression |
| --- | --- |
| Repository-owned workflow policy | `docs/harness/guardrails.md`, `docs/PLANS.md`, or a target-owned execution plan |
| Structured knowledge base | `docs/harness/` directory with guardrails, readiness, updates |
| Agent-readable docs | `CLAUDE.md`, `ARCHITECTURE.md`, configured docs paths |
| Validation and guardrails | Python/Bun/Gradle/Maven validators, CI/hook templates |
| Evidence and proof packets | `docs/harness/readiness.md`, `docs/harness/known-violations.md`, and target-owned proof records |
| Mechanical enforcement | Custom validators, structural checks, staged gates |

## What the plugin does not provide

- Issue tracker integration (Linear, GitHub Issues, etc.)
- Daemon process or persistent orchestrator
- Per-issue workspace isolation
- Agent session lifecycle management
- Real-time observability stack
- Automatic PR creation and merging

## When to open this reference

Open this reference when:
- A target repository asks about Symphony-style orchestration.
- You need to explain why this plugin stops at harness setup and does not include runtime orchestration.
- You want to map Symphony concepts to the plugin's repository-local artifacts.
