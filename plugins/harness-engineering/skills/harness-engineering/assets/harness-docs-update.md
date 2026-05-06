# Harness Update Record

Copy this file when changing harness docs, scripts, CI, hooks, build integrations, user requirement rules, readiness gates, guardrail stages, or the layer model.

````markdown
# Harness Update: {Title}

## Context

- Date: {date}
- Config: `docs/harness-engineering/harness-engineering.json`
- Stage: {0 discovery | 1 visibility | 2 advisory | 3 error gates | 4 ratchet}
- Trigger: {why the harness changed}

## Changed Surfaces

| Surface | Path | Change |
| --- | --- | --- |
| Config | docs/harness-engineering/harness-engineering.json | {summary} |
| Guardrails | docs/harness-engineering/guardrails.md | {summary} |
| Readiness | docs/harness-engineering/readiness.md | {summary} |
| Docs | docs/... | {summary} |
| Scripts | scripts/harness/... | {summary} |
| CI | .github/workflows/harness-checks.yml | {summary} |

## Docs And Scripts Alignment

| Documented Rule | Implementation Path | Validation Command |
| --- | --- | --- |
| {rule} | {path} | {command} |

## Stage Gate

| Gate | Status | Evidence |
| --- | --- | --- |
| {exit gate} | pass | {path or command} |

## Known Violations

- Ledger: `docs/harness-engineering/known-violations.md`
- Ratchet mode: {warn | error-new | reduce-budget}

## Validation

```bash
sh scripts/harness/validate_harness.sh
```

## Remaining Conflicts

- {conflict or none}
````
