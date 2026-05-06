# Source Verification

Open this reference when updating the OpenAI Harness Engineering source mapping, CI/build/script templates, YAML style guidance, or Claude/Codex compatibility rules.

## Verified source

| Field | Value |
| --- | --- |
| Source | OpenAI, `Harness engineering: using Codex in an agent-first world` |
| URL | `https://openai.com/ko-KR/index/harness-engineering/` |
| Retrieval date | 2026-05-07 |
| Retrieval method | Browser-backed Markdown fetch of the article body and image alt text |
| Status | HTTP 200, article headings and diagrams available |

## Source facts encoded by this skill

- Bootstrap starts from an empty git repository and asks Codex to create repository structure, CI config, formatting rules, package manager setup, app framework, and an agent-instruction map.
- Repository instructions should be a short map, not a thousand-page encyclopedia; deeper knowledge lives in structured `docs/` content.
- The default knowledge base includes root `AGENTS.md`, root `ARCHITECTURE.md`, `docs/design-docs/`, `docs/exec-plans/`, `docs/generated/`, `docs/product-specs/`, `docs/references/`, and topic docs for design, frontend, plans, product sense, quality, reliability, and security.
- Agent-readable applications expose browser automation, before/after snapshots, runtime events, local logs, metrics, and traces so agents can validate behavior directly.
- Repository knowledge must be encoded into Markdown, code, schemas, and execution plans because external docs, chat, and tacit human knowledge are outside the agent-visible boundary.
- Architecture rules use mechanically enforced layer boundaries and taste invariants rather than prose-only guidance.
- Execution plans, agent review loops, CI checks, and entropy cleanup are first-class repository systems that support higher autonomy over time.

## Supporting official references

| Surface | Reference | Encoded guidance |
| --- | --- | --- |
| GitHub Actions events and branch filters | `https://docs.github.com/actions/using-workflows/workflow-syntax-for-github-actions` | `push`, `pull_request`, `workflow_dispatch`, branch filters, minimal workflow permissions |
| GitLab CI rules | `https://docs.gitlab.com/ee/ci/yaml/` | `workflow: rules`, job `rules`, `merge_request_event`, branch-push conditions |
| YAML block chomping | `https://yaml.org/spec/1.2.2/#8111-block-chomping-indicator` | `|-` strips the final newline for literal command blocks |
| YAML folded style | `https://yaml.org/spec/1.2.2/#812-folded-style` | `>-` folds scalar text and strips the final newline |
| npm scripts | `https://docs.npmjs.com/cli/using-npm/scripts` | `npm run harness:validate` as the npm entrypoint |
| pnpm scripts | `https://pnpm.io/cli/run` | `pnpm run harness:validate` as the pnpm entrypoint |
| Yarn script command | `https://yarnpkg.com/cli/run` | `yarn run harness:validate` as the Yarn entrypoint |
| Bun package scripts | `https://bun.sh/docs/cli/run` | `bun run harness:validate` as the Bun entrypoint |
| Gradle Exec task | `https://docs.gradle.org/current/dsl/org.gradle.api.tasks.Exec.html` | `tasks.register<Exec>("harnessCheck")` with `commandLine(...)` |
| Gradle lifecycle check task | `https://docs.gradle.org/current/userguide/base_plugin.html` | Wire `harnessCheck` into `check` only when Gradle integration is enabled |
| uvx command | `https://docs.astral.sh/uv/reference/cli/#uvx` | `uvx --offline --no-python-downloads python ...` for the direct wrapper when `uvx` is provisioned |
| Claude Code memory | `https://docs.anthropic.com/en/docs/claude-code/memory` | `CLAUDE.md` is the Claude Code project memory entrypoint |
| Claude Code agents | `https://docs.anthropic.com/en/docs/claude-code/sub-agents` | Project-specific agents can live under `.claude/agents/` when copied into target repos |
| Playwright Python API | `https://playwright.dev/python/docs/api/class-page` and `https://playwright.dev/python/docs/videos` | `page.video.path()` and context-level video recording examples in `references/agent-legibility.md` |

Re-check these sources when changing command syntax, event rules, package-manager script forms, YAML scalar style, or Claude ecosystem paths.

## Local adaptations

- This repository keeps `CLAUDE.md` as the canonical root map and exposes `AGENTS.md -> CLAUDE.md` for compatibility.
- `ARCHITECTURE.md` remains root-level, not under `docs/`.
- `.agents/skills -> ../.claude/skills` remains the compatibility direction when both skill surfaces exist.
- `docs/harness-engineering/harness-engineering.json` is the target repository's visible config source of truth.
- `docs/generated/` holds reproducible derived documentation only; source schemas remain in project-specific source roots.
