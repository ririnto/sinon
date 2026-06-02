<!-- @formatter:off -->

# Harness Linter Migration: bun (oxlint + oxfmt) and uv (ruff)

Status: design. Author: ririnto. Date: 2026-06-03.

This spec covers migrating the harness bespoke rule engines toward ecosystem linters for the bun/TypeScript stack (primary) and the uv/Python stack (follow-up). It extends the per-stack migration policy already applied to gradle/Kotlin (ktlint, committed in `46aecf1`).

## Goals

- The bun stack MUST provide a working `check` (lint/detection) capability through oxlint.
- The bun stack SHOULD provide `format` through oxfmt (feasible, therefore adopted).
- The uv stack MUST provide a working `check` through ruff and SHOULD provide `format` through `ruff format`.
- Output MUST stay consistent with the harness reporter shape: `file:line:column [SEVERITY] category: message` plus fix safety (`safe`/`unsafe`/`manual`).
- Each stack's self-check and rule-count/manifest invariants MUST stay green after migration.

## Non-Goals

- Migrating the 19 structural/manifest/filesystem rules off the harness engine. oxlint and ruff are source linters and cannot express directory presence, manifest-field-to-file consistency, hook executability, frontmatter, CI parity, or symlink-safety checks.
- Touching maven/Java or shell stacks in this spec.

## Key Insight: The 33 bun Rules Are Not Homogeneous

| Category | Count | oxlint-eligible |
| --- | --- | --- |
| TypeScript AST code-style | 14 | yes (built-in or custom JS plugin) |
| Structural / manifest / filesystem | 19 | no (not expressible as a source-lint rule) |

Only the 14 TS AST rules are oxlint candidates. The 19 structural rules remain in the bun harness engine. This boundary is intrinsic to what oxlint is, independent of the custom-plugin API maturity.

## Architecture: bun Stack

### Rule ownership

- oxlint owns all 14 TS AST code-style rules.
  - Built-in coverage (verify exact rule IDs against official oxc docs at implementation): candidates include `no-console` (unstructuredLogging), `no-empty` with `allowEmptyCatch:false` (emptyCatchBlock), `curly` (ifStatementBraces), `no-underscore-dangle` (leadingUnderscore), `import/no-namespace` (wildcardImport), `@typescript-eslint/ban-ts-comment` (uncheckedCastSuppression).
  - Custom JS-plugin rules WITH fixers for the bespoke conventions that have no built-in: `earlyReturn`, `implicitLambdaIt`, `importOverFqn`, `multilineDocStyle`, `publicDeclarationDocComment`, `greaterThanComparison`. The alpha status of the JS-plugin API is accepted (user decision, 2026-06-03), mirroring the gradle/ktlint full detection+fix integration.
- oxfmt owns formatting, including blank-line normalization. `leafFunctionBlankLines` is NOT a custom oxlint rule: spike verification (oxfmt 0.53.0) confirmed oxfmt collapses consecutive blank lines inside function bodies to one by default, so this convention is subsumed by `harness-format` running oxfmt and is removed as a lint rule.
- The bun harness engine retains only the 19 structural rules. The 14 TS AST rule modules are removed once oxlint (13 rules) and oxfmt (`leafFunctionBlankLines`) cover them.

### Components

- `.oxlintrc.json` (config): `categories`, built-in rule severities, `jsPlugins` pointing to custom rule files, and `overrides` as needed.
- Custom oxlint rule plugins: ESLint v9-compatible JS modules using `create(context)` returning AST visitors and reporting via `context.report({ node, message, fix })`. Spike verification (oxlint 1.68.0) confirmed: oxlint loads JS plugins through a Node runtime (not Bun), and the plugin module MUST be ESM. Therefore the bun harness asset root ships a `package.json` with `"type": "module"` that also declares `oxlint` and `oxfmt` as devDependencies; this single file satisfies both ESM loading and tool provisioning. Node availability is a prerequisite for the custom-rule path (built-in oxlint rules and oxfmt run from the binary and do not need Node).
- `.oxfmtrc.json` (config): `printWidth`, `tabWidth`, `semi`, `singleQuote`, import sorting. Verify defaults and key names against official oxfmt docs at implementation.
- Adapter in `harness-check`: invokes `oxlint --format=json`, parses results, maps each diagnostic into the harness `Finding` shape (severity/category/message/file/startLine/startColumn + fix safety) so output stays uniform.

### Data flow (check)

1. `harness-check` runs the harness engine for the 19 structural rules, producing `Finding[]`.
2. `harness-check` invokes `oxlint --format=json` over the target's TS sources.
3. The adapter maps oxlint diagnostics into `Finding[]`, normalizing severity. Spike verification confirmed oxlint `--format json` does NOT expose per-diagnostic fix availability or safety (diagnostic keys are `message`, `code`, `severity`, `filename`, `labels`, `help`, `url`, `causes`, `related` only). The adapter therefore assigns `fix` safety from a static per-rule policy keyed on the diagnostic `code` (built-in safe-fixable rules → `safe`; custom-plugin rules and suggestion/dangerous-tier built-ins → `unsafe`). oxlint owns fix APPLICATION via its own `--fix`/`--fix-suggestions` passes; the harness reporter records that a finding is fixable plus its safety but does NOT carry `edits`, and the harness format/edit applier does not apply oxlint fixes (mirroring how oxfmt owns formatting).
4. Findings are merged, deduped on the existing key, and reported. Exit non-zero if any ERROR finding exists.

### Data flow (format)

1. `harness-format` invokes `oxfmt` over the target's TS sources for write-mode formatting.
2. `leafFunctionBlankLines` is removed from the harness FORMAT_ALLOWLIST and from the lint rules: oxfmt subsumes it (verified: collapses consecutive blank lines to one). No reconciliation of conflicting edits is needed because the harness no longer emits blank-line edits for TS sources.

### Error handling

- If oxlint or oxfmt is not installed, `harness-check`/`harness-format` MUST emit a clear, non-silent diagnostic (no `/dev/null` discards) and fail the affected capability rather than passing silently.
- oxlint JSON parse failures MUST surface the raw oxlint output for diagnosis.

### Testing

- A self-scan equivalent: the harness's own TS runtime sources MUST pass the migrated oxlint config.
- Positive/negative fixtures per migrated rule (built-in and custom plugin), mirroring the gradle `KtlintPositiveTest`/self-scan pattern.
- Rule-count and manifest invariants updated and asserted by `plugin-self-check.sh`.

## Architecture: uv Stack (Follow-up)

- ruff has no custom-rule plugin API (authoritative: official FAQ). Therefore ruff owns only the standard rule families it implements plus `ruff format`.
- The bespoke libcst engine retains the custom Python-convention rules ruff cannot express (detection-only on the ruff side; existing libcst behavior preserved).
- Config in `pyproject.toml [tool.ruff]` or `ruff.toml`. Verify selected rule families and `ruff format` behavior against official ruff docs at implementation.
- Output mapping mirrors the bun adapter approach so the harness reporter shape stays consistent.

## Tradeoffs (Surfaced)

- oxlint JS-plugin custom-rule API is alpha. Accepted by user decision (2026-06-03).
- oxfmt is beta. Formatting is feasible, therefore adopted.
- The custom-rule path requires Node on the target machine: oxlint loads JS plugins via a Node runtime, not Bun (spike-verified). This is a new prerequisite beyond the standalone-Bun model. Built-in oxlint rules and oxfmt run from the binary and need no Node, so even without Node the bun `check` keeps built-in coverage plus the 19 structural rules; only the 6 custom-plugin conventions go dark. The bun `check` MUST emit a clear non-silent diagnostic if Node is required but absent.
- oxlint `--format json` carries no per-diagnostic fix metadata (spike-verified); fix safety is therefore a static per-rule policy in the adapter rather than read from oxlint, and oxlint owns fix application.
- Two-engine `check` on bun (oxlint for TS code-style, harness engine for structural) is not a compromise from the alpha API; it is the necessary consequence of oxlint's scope.

## Sequencing

1. bun stack first, fully implemented and committed.
2. uv stack second.

## Open Verification Items

Resolved by spike (oxlint 1.68.0, oxfmt 0.53.0, 2026-06-03):

- oxlint `jsPlugins` config schema and the custom-rule module/`context.report({fix})` signature: confirmed working under Bun-invoked oxlint (plugin loaded via Node ESM, fixer rewrote source).
- oxlint `--format json` diagnostic shape and absence of per-diagnostic fix metadata: confirmed.
- oxfmt subsumes `leafFunctionBlankLines`: confirmed (collapses consecutive blank lines to one).

Remaining (resolve at implementation):

- Exact oxlint built-in rule IDs that cover each mappable convention, in particular whether `eslint/no-underscore-dangle` exists for `leadingUnderscore` (confirm via `oxlint --rules`).
- ruff rule-family selection that maps to existing uv conventions (uv follow-up).

<!-- @formatter:on -->
