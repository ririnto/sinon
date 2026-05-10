# OpenAI Harness Engineering Reference

Open this reference when adapting OpenAI's Harness Engineering article into a target repository's docs, validation, or agent-readability posture.

## Source

| Field | Value |
| --- | --- |
| Source | OpenAI Harness Engineering article |
| URL | `https://openai.com/ko-KR/index/harness-engineering/` |
| Retrieval date | 2026-05-09 |
| Local use | Methodology reference for repository harness setup, not a required runtime or product dependency |

## Transferable principles

- Keep repository instructions short and map-like. Put durable knowledge in structured docs, not one large instruction file.
- Make repository knowledge agent-readable through versioned Markdown, schemas, execution plans, generated docs, and validation records.
- Enforce important boundaries mechanically with validators, structural tests, custom lint, CI, or hook gates instead of relying on prose alone.
- Treat missing agent capability as a harness gap. Add tools, guardrails, documentation, or evidence paths instead of asking agents to try harder.
- Preserve proof of behavior through tests, runtime evidence, logs, metrics, screenshots, traces, or proof packets where the target repository supports them.
- Feed review findings, bugs, and drift back into docs or validators so quality rules compound over time.

## Local harness expression

| Article idea | Setup-harness expression |
| --- | --- |
| `AGENTS.md` as a map | Root `CLAUDE.md` with optional `AGENTS.md` alias points to `docs/harness/config.json` and focused docs |
| Structured knowledge base | `docs/harness/guardrails.md`, `docs/harness/readiness.md`, `docs/harness/updates.md`, architecture docs, generated docs, and configured plan paths |
| Mechanical invariants | Python/Bun/Gradle/Maven validators, configured commands, CI templates, hooks, and bundled review agents |
| Agent-readable evidence | Proof packets, readiness records, known violations, generated docs, runtime commands, and E2E evidence paths |
| Entropy cleanup | Doc gardening, readiness ratchets, known-violation budgets, and updates records |

## Boundary

This plugin does not require target repositories to use zero human-written code, autonomous merging, agent-to-agent review, local observability stacks, or browser automation. Those practices are optional target-owned workflow choices. The plugin's job is to install the repository-local docs, configuration, validation, and evidence surfaces that make such choices safer when a target adopts them.
