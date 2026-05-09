# Source Verification

Open this reference when updating the OpenAI Harness Engineering source mapping, related Symphony boundary notes, CI/script templates, YAML style guidance, or Claude compatibility rules.

## Verified source

| Field | Value |
| --- | --- |
| Source | OpenAI Harness Engineering article |
| URL | `https://openai.com/ko-KR/index/harness-engineering/` |
| Retrieval date | 2026-05-09 |
| Retrieval method | Browser-backed Markdown fetch of the article body and image alt text |
| Status | HTTP 200, article headings and diagrams available |

## Harness source facts encoded by this skill

- Bootstrap starts from an empty git repository and uses an agent to create repository structure, CI config, formatting rules, package manager setup, app framework, and an agent-instruction map.
- Repository instructions should be a short map, not a thousand-page encyclopedia; deeper knowledge lives in structured `docs/` content.
- The default knowledge base includes root `AGENTS.md`, root `ARCHITECTURE.md`, `docs/design-docs/`, `docs/exec-plans/`, `docs/generated/`, `docs/product-specs/`, `docs/references/`, and topic docs for design, frontend, plans, product sense, quality, reliability, and security.
- Agent-readable applications expose browser automation, before/after snapshots, runtime events, local logs, metrics, and traces so agents can validate behavior directly.
- Repository knowledge must be encoded into Markdown, code, schemas, and execution plans because external docs, chat, and tacit human knowledge are outside the agent-visible boundary.
- Architecture rules use mechanically enforced layer boundaries and taste invariants rather than prose-only guidance.
- Execution plans, agent review loops, CI checks, and entropy cleanup are first-class repository systems that support higher autonomy over time.

## Related Symphony facts

| Field | Value |
| --- | --- |
| Source | OpenAI Symphony article and `openai/symphony` `SPEC.md` |
| URLs | `https://raw.githubusercontent.com/openai/symphony/main/SPEC.md` |
| Retrieval date | 2026-05-09 |
| Retrieval method | Browser-backed Markdown fetch of the OpenAI article and raw public `SPEC.md` |
| Status | HTTP 200 for the article and raw public specification |

- Symphony is a long-running automation service that continuously reads work from an issue tracker, with Linear as the current specification version, creates an isolated workspace for each issue, and runs a coding agent session inside that workspace.
- Symphony turns issue execution into a repeatable daemon workflow instead of manual scripts, keeps workflow policy in repository-owned `WORKFLOW.md`, and provides observability for multiple concurrent agent runs.
- Symphony's runner launches a coding-agent app-server client, with app-server examples; protocol schemas, payloads, framing, and method names are owned by the targeted app-server version.
- Symphony includes Linear-compatible issue tracker semantics, GraphQL authentication, pagination, and issue-state refresh behavior for `tracker.kind == "linear"`.

## Plugin-specific conventions layered on top

- The OpenAI article informs the repository knowledge-base shape and enforcement posture; it does not define `docs/harness-engineering/harness-engineering.json`, root `.harness-engineering.json`, staged config fields, or validator command forms.
- `docs/harness-engineering/harness-engineering.json` is this plugin's stable convention for target-specific, machine-readable, docs-adjacent harness metadata.
- The path is namespaced under `docs/harness-engineering/` so operational metadata can sit beside guardrails, readiness, known-violation, update, and validation resources without crowding root `CLAUDE.md`, `AGENTS.md`, or `ARCHITECTURE.md`.
- Root `.harness-engineering.json` is manual migration input only when an existing repository already has one; it is not a supported fallback path for new setup or validation.
- The plugin adds staged fields, readiness scoring, user requirement rules, validators, CI/hooks templates, and harness engineering checks on top of the article-derived knowledge-base posture.
- This plugin treats `CLAUDE.md` as the target repository's canonical root instruction map because Claude Code reads project `CLAUDE.md` files. It exposes `AGENTS.md -> CLAUDE.md` as the compatibility profile for AGENTS.md readers rather than reversing the direction.
- This plugin may document agent-readable runtime evidence such as browser automation, logs, metrics, traces, screenshots, and validation commands, but it does not provision a Symphony daemon, Linear integration, app-server client, agent runtime stack, or always-on scheduler.
- This plugin owns setup-time repository visibility and guardrail metadata. Runtime orchestration, ticket polling, workspace scheduling, issue-state mutation, and app-server protocol compatibility belong to a separate orchestration product or target-specific integration.

## Product boundary interpretation

- Harness Engineering is the setup-time repository visibility and guardrail layer. It prepares a target repository so agents can find instructions, architecture, docs, validation commands, readiness state, known violations, and evidence paths.
- Symphony is a runtime orchestration layer above individual coding-agent sessions. It coordinates issue polling, dispatch, workspaces, retries, agent app-server execution, and operator-visible runtime state.
- Do not describe this plugin as a Symphony implementation, a Linear integration, an app-server client, an OpenAI runtime stack installer, or an always-on scheduler.
- If a target repository adopts Symphony or another orchestrator, record that integration in target-specific docs or execution plans and keep `docs/harness-engineering/harness-engineering.json` focused on docs, scripts, checks, CI, and hooks.

## Supporting official references

| Surface | Reference | Encoded guidance |
| --- | --- | --- |
| GitHub Actions events and branch filters | `https://docs.github.com/actions/using-workflows/workflow-syntax-for-github-actions` | `push`, `pull_request`, `workflow_dispatch`, branch filters, minimal workflow permissions |
| GitLab CI rules | `https://docs.gitlab.com/ee/ci/yaml/` | `workflow: rules`, job `rules`, `merge_request_event`, branch-push conditions |
| YAML block chomping | `https://yaml.org/spec/1.2.2/#8111-block-chomping-indicator` | `\|-` strips the final newline for literal command blocks |
| YAML folded style | `https://yaml.org/spec/1.2.2/#812-folded-style` | `>-` folds scalar text and strips the final newline |
| uv run command | `https://docs.astral.sh/uv/reference/cli/#uv-run` | `uv run ...` for the validator wrapper and command examples |
| Claude Code memory | `https://docs.anthropic.com/en/docs/claude-code/memory` | `CLAUDE.md` is the Claude Code project memory entrypoint |
| Claude Code agents | `https://docs.anthropic.com/en/docs/claude-code/sub-agents` | Project-specific agents can live under `.claude/agents/` when copied into target repos |

Re-check these sources when changing command syntax, event rules, YAML scalar style, or Claude ecosystem paths.

## Local adaptations

- This repository keeps `CLAUDE.md` as the canonical root map and exposes `AGENTS.md -> CLAUDE.md` for compatibility.
- `ARCHITECTURE.md` remains root-level, not under `docs/`.
- `.agents/skills -> ../.claude/skills` remains the compatibility direction when both skill surfaces exist.
- `docs/harness-engineering/harness-engineering.json` is this plugin's visible config source of truth for target repositories.
- `docs/generated/` holds reproducible derived documentation only; source schemas remain in project-specific source roots.
