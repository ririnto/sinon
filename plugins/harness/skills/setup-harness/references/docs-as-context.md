# Docs As Context

Open this reference when full specs, docs, scripts, and implementation have drifted or when the target repo needs a stronger docs-as-source-of-truth convention.

## Context chain

The target repository should let an agent move from intent to implementation without hidden context. The OpenAI article's knowledge-boundary model is the reason: external docs, Slack, and tacit human knowledge do not exist for an agent until they are encoded into repository Markdown, code, schema, or plans.

```text
CLAUDE.md -> ARCHITECTURE.md or docs index -> full spec or design doc -> source root -> validation script -> CI or hook
```

Each arrow should be represented by a real path in `docs/harness/config.json` or a linked document.
`AGENTS.md` should expose the same first step by symlinking to `CLAUDE.md`.

## Full spec contract

Use this shape for durable feature or system specs.

```markdown
# {Feature Or System} Spec

## Goal

{The user-facing outcome and why it matters.}

## Sources Of Truth

| Surface | Path | Purpose |
| --- | --- | --- |
| Implementation | src/... | Code that must follow this spec |
| Validation | scripts/harness/... | Check that protects this behavior |
| CI | .github/workflows/harness-checks.yml | Continuous enforcement |

## Requirements

1. {Requirement}
2. {Requirement}

## Update Rule

When implementation changes, update this spec and the related validation script in the same change set.
```

## Alignment checks

- Every documented source root exists.
- Every full spec links to implementation and at least one validation path.
- Every scoped path rule cites a spec, ADR, architecture section, or guardrail record.
- Every generated doc names the generation script and output path.
- Every generated doc is reproducible from checked-in source schema paths and does not become the source schema itself.
- Every script described in docs exists and is called by CI, hooks, the harness shell wrapper, or a documented manual command.
- Every architectural rule in docs maps to `checks`, `ARCHITECTURE.md`, or a target-specific check entry.
- Every docs freshness threshold appears in `docs/harness/readiness.md` with current status.

## Common drift fixes

- If code moved, update docs and config paths before changing CI.
- If a script changed behavior, update the full spec and validation command together.
- If a rule is no longer enforceable, mark it informational and document why it is not a harness check.
- If an agent cannot find context, add a link to root `CLAUDE.md`, root `ARCHITECTURE.md`, or the configured docs index rather than adding prose to the prompt. `AGENTS.md` should expose the same content by symlink.
