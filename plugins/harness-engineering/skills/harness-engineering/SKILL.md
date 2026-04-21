---
name: harness-engineering
description: >-
  Set up and maintain an agent-first repository with progressive disclosure, architecture enforcement, and entropy management. Use when designing repository structure for AI agent development, creating CLAUDE.md as a table of contents, enforcing layer constraints with custom linters, or managing technical debt in agent-generated codebases.
---

# Harness Engineering

Design and maintain a repository optimized for AI agent throughput and coherence, where humans steer and agents execute.

## Operating rules

- Treat the repository as the single source of truth. If knowledge lives outside the repo (chat threads, shared docs, verbal agreements), it does not exist for the agent.
- Prefer progressive disclosure: a short entrypoint that points to deeper sources, not a monolithic instruction file.
- Enforce architecture mechanically with linters and structural tests; do not rely on convention alone.
- Encode human taste as rules and tooling, not inline corrections. When documentation falls short, promote the rule into code.
- Keep the ordinary path in this file; open `references/` only for named blockers.

## Procedure

### 1. Establish the knowledge map

Create `CLAUDE.md` as a table of contents, not an encyclopedia. Keep it under 150 lines. It should point to deeper sources of truth in `docs/`. `AGENTS.md` is a symlink to `CLAUDE.md`; both names resolve to the same file.

A monolithic `CLAUDE.md` fails in predictable ways: it crowds out task context, makes everything "important" so nothing is, rots instantly, and resists mechanical verification. Treat it as the map, not the territory.

```markdown
# CLAUDE.md

## Architecture

See `docs/ARCHITECTURE.md` for the top-level domain map and package layering.

## Active work

See `docs/exec-plans/active/` for in-progress execution plans.

## Product

See `docs/product-specs/index.md` for feature specifications.

## Design principles

See `docs/design-docs/core-beliefs.md` for agent-first operating principles.

## Quality

See `docs/QUALITY_SCORE.md` for per-domain quality grades.
```

Truncated; copy `assets/claude-md-template.md` for the full template.

### 2. Scaffold the knowledge base

Create a `docs/` directory treated as the system of record.

```text
docs/
├── ARCHITECTURE.md
├── DESIGN.md
├── FRONTEND.md
├── PLANS.md
├── PRODUCT_SENSE.md
├── QUALITY_SCORE.md
├── RELIABILITY.md
├── SECURITY.md
├── design-docs/
│   ├── index.md
│   ├── core-beliefs.md
│   └── ...
├── exec-plans/
│   ├── active/
│   ├── completed/
│   └── tech-debt-tracker.md
├── generated/
│   └── db-schema.md
├── product-specs/
│   ├── index.md
│   └── ...
└── references/
    └── ...
```

Key invariants:

- Design documentation is catalogued and indexed, including verification status.
- Execution plans are first-class versioned artifacts with progress and decision logs. Copy `assets/execution-plan-template.md` when writing plans.
- Active plans, completed plans, and known technical debt are co-located.
- Generated artifacts (e.g., schema dumps) are separated from authored docs.
- Dedicated linters and CI jobs validate cross-links, freshness, and structure.

Copy `assets/docs-directory-scaffold.md` when scaffolding the full directory tree.

Open `references/repository-knowledge-structure.md` for detailed indexing patterns and progressive disclosure mechanics.

### 3. Define and enforce the layer model

Within each business domain, restrict dependency direction through a fixed set of layers:

```text
Types → Config → Repo → Service → Runtime → UI
```

Cross-cutting concerns (auth, connectors, telemetry, feature flags) enter through a single explicit interface: Providers. Any other cross-domain edge is disallowed.

Enforce with custom linters and structural tests:

```python
"""
Structural test: assert no domain violates the forward-only layer rule.

Each domain directory MUST follow the fixed layer set. Imports that skip
or reverse layer direction MUST be rejected.
"""
def test_layer_dependencies(domain_path):
    layers = ["types", "config", "repo", "service", "runtime", "ui"]
    violations = scan_imports(domain_path, allowed_direction="forward")
    assert not violations, (
        f"Layer violations in {domain_path}: {violations}. "
        "Imports must flow forward through: "
        + " → ".join(layers)
    )
```

Enforcement rules:

- Static analysis rejects imports that violate layer direction.
- Lint error messages include remediation instructions for agent context.
- Structural tests assert each domain follows the fixed layer set.
- File size limits, naming conventions, and structured logging are enforced with custom lints.

Open `references/architecture-enforcement.md` for detailed linter patterns, taste invariant examples, and structural test templates.

### 4. Encode golden principles

Write opinionated, mechanical rules that keep the codebase legible and consistent for future agent runs.

Core principles:

1. Prefer shared utility packages over hand-rolled helpers to keep invariants centralized.
2. Parse data shapes at the boundary; do not probe data without validation.
3. Use structured logging; reject ad-hoc print statements.
4. Enforce naming conventions for schemas and types.
5. Set file size limits; split files that exceed them.
6. Favor dependencies that are fully internalizable and stable over opaque upstream behavior.
7. When a third-party library is opaque or unstable, reimplement the subset you need rather than working around it.

When a principle is violated, encode the fix in tooling, not in review comments. See `references/architecture-enforcement.md` for the taste invariants table mapping each principle to its enforcement mechanism.

### 5. Manage entropy

Schedule recurring cleanup to prevent drift:

- A doc-gardening agent scans for stale or obsolete documentation that does not reflect real code behavior and opens fix-up pull requests.
- Quality grades per domain and layer track gaps over time in `docs/QUALITY_SCORE.md`.
- Technical debt items in `docs/exec-plans/tech-debt-tracker.md` are reviewed and resolved in small increments.

Technical debt is a high-interest loan: pay it down continuously rather than letting it compound. Most cleanup PRs should be reviewable in under one minute and eligible for auto-merge.

Agents replicate patterns that already exist in the repository, including suboptimal ones. Without continuous correction, drift is inevitable.

Open `references/entropy-management.md` for doc-gardening cadence, quality grading rubrics, and cleanup automation patterns.

### 6. Make the application legible to agents

Wire the runtime so agents can observe and validate behavior directly:

- Make the app bootable per git worktree so each change gets an isolated instance. Tear down the instance when the task completes.
- Expose logs, metrics, and traces through a local ephemeral observability stack per worktree.
- Enable agents to query logs (LogQL) and metrics (PromQL).
- Connect browser automation (Chrome DevTools Protocol) for UI validation: DOM snapshots, screenshots, and navigation.

With this context, prompts like "ensure service startup completes in under 800 ms" or "no span in these four critical user journeys exceeds two seconds" become tractable.

Open `references/agent-legibility.md` for per-worktree isolation setup, DevTools integration, and observability stack configuration.

### 7. Optimize merge philosophy for throughput

In high-throughput agent environments:

- Keep merge gates minimal and non-blocking where possible.
- Keep pull requests short-lived.
- Address test flakes with follow-up runs rather than blocking indefinitely.
- Corrections are cheap; waiting is expensive.

This would be irresponsible in a low-throughput environment. In agent-first development, it is often the correct tradeoff.

## Edge cases

- If the existing repository has a monolithic `CLAUDE.md`, refactor it into the table-of-contents pattern before adding new documentation.
- If agents struggle with a task, treat it as a signal: identify what capability is missing (tools, guardrails, documentation) and add it to the repository, always by having the agent itself write the fix.
- If agent output does not match human stylistic preferences but is correct, maintainable, and legible to future agent runs, accept it. Encode taste as rules, not as inline corrections.
- If documentation falls short, promote the rule into code or linting.
- If the codebase has no architecture constraints yet, add them before scaling agent throughput. Constraints are a prerequisite for speed, not a deferred cleanup task.

## Output contract

Return:

1. The `CLAUDE.md` table of contents
2. The `docs/` directory structure with index files
3. Architecture enforcement configuration (linter rules, structural tests)
4. Golden principles document
5. Validation results and remaining risks

## Gotchas

- Do not treat `CLAUDE.md` as the encyclopedia; it is the map, not the territory.
- Do not postpone architecture enforcement until the codebase grows large; in agent-first development, constraints enable speed.
- Do not let humans write code directly; if something breaks, fix it by adding capability to the agent environment.
- Do not rely on Slack threads, shared documents, or verbal agreements as sources of truth; if it is not in the repository, it does not exist for the agent.

## Support files

- `references/repository-knowledge-structure.md` -- open for detailed `docs/` hierarchy design, indexing patterns, and progressive disclosure mechanics
- `references/architecture-enforcement.md` -- open for custom linter patterns, structural test templates, and taste invariant examples
- `references/entropy-management.md` -- open for doc-gardening cadence, quality grading rubrics, and cleanup automation patterns
- `references/agent-legibility.md` -- open for per-worktree isolation setup, DevTools integration, and observability stack configuration
- `assets/claude-md-template.md` -- copy when creating `CLAUDE.md` from scratch
- `assets/docs-directory-scaffold.md` -- copy when scaffolding the `docs/` directory
- `assets/execution-plan-template.md` -- copy when writing execution plans
