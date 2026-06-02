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

## Maven/Java and shell: end-state boundary and tracked tech-debt

The Maven/Java stack intentionally stays on the JavaParser harness engine and is NOT migrated to an ecosystem linter. This is the correct end-state under the per-stack policy, not an unfinished migration:

- The only ecosystem Java tool in the stack is Spotless (`harness-maven-plugin/pom.xml`, configured with `removeUnusedImports`/`trimTrailingWhitespace`/`endWithNewline`). Spotless is a formatter, not a detector, so it cannot express the 14 Java AST detection rules — the same intrinsic-scope reason oxlint and ruff cannot own the 19 structural rules.
- The pom deliberately omits a full Java formatter (no google-java-format, palantir, or eclipse formatter). Subsuming `leafFunctionBlankLines` the way `oxfmt` and `ruff format` do would require introducing such a formatter, which imposes an opinionated whole-repo Java reformat with high blast radius. Maven instead keeps `LeafFunctionBlankLinesRule.format()` (JavaParser `LexicalPreservingPrinter`) as a deliberate, documented end-state.
- The shell stack exposes no AST code-style rule surface to migrate; its checks are structural.

Tracked tech-debt (each requires an explicit user decision before adoption; out of scope for autonomous work):

- Checkstyle- or PMD-based detection migration for the Java code-style rules. This introduces a new build-tool dependency and a detector engine the per-stack policy does not currently include.
- Full-formatter (google-java-format or palantir) subsumption of `leafFunctionBlankLines`, replacing the bespoke JavaParser `format()`. This imposes an opinionated whole-repo Java format.

## Key Insight: The 33 bun Rules Are Not Homogeneous

| Category | Count | oxlint-eligible |
| --- | --- | --- |
| TypeScript AST code-style | 14 | yes (built-in or custom JS plugin) |
| Structural / manifest / filesystem | 19 | no (not expressible as a source-lint rule) |

Only the 14 TS AST rules are oxlint candidates. The 19 structural rules remain in the bun harness engine. This boundary is intrinsic to what oxlint is, independent of the custom-plugin API maturity.

## Architecture: bun Stack

### Rule ownership

- oxlint owns 12 of the 14 TS AST code-style rules (6 built-in + 6 custom). The bun manifest carries 13 TS AST keys; `implicitLambdaIt` is registered in code only (no manifest key, Kotlin-only convention) and is DROPPED, not migrated.
  - Built-in coverage (config keys use the slash form; the JSON `code` field uses the paren form, see Data flow): `eslint/no-console` (unstructuredLogging, ERROR), `eslint/no-empty` with `allowEmptyCatch:false` (emptyCatchBlock, ERROR), `eslint/curly` (ifStatementBraces, ERROR), `eslint/no-underscore-dangle` with `allow:["_"]` (leadingUnderscore, ERROR — confirm rule exists via `oxlint --rules` at implementation), `import/no-namespace` (wildcardImport, ERROR), `typescript/ban-ts-comment` (uncheckedCastSuppression, ERROR).
  - Custom JS-plugin rules WITH fixers for the bespoke conventions that have no built-in: `greaterThanComparison` (ERROR), `earlyReturn` (WARN), `silentCatch` (ERROR), `importOverFqn` (WARN), `publicDeclarationDocComment` (WARN), `multilineDocStyle` (WARN). The alpha status of the JS-plugin API is accepted (user decision, 2026-06-03), mirroring the gradle/ktlint full detection+fix integration.
  - `silentCatch` is custom, NOT built-in: spike verification (oxlint 1.68.0) confirmed `eslint/no-empty` flags only truly empty catch blocks and does NOT flag a non-empty catch that still swallows the error (e.g. `catch (e) { return 0; }`). Its swallow-detection (catch must rethrow, translate, or log) is therefore not reducible to built-ins. `emptyCatchBlock` (empty-only) remains built-in via `eslint/no-empty`.
- oxfmt owns formatting, including blank-line normalization. `leafFunctionBlankLines` is NOT a custom oxlint rule: spike verification (oxfmt 0.53.0) confirmed oxfmt collapses consecutive blank lines inside function bodies to one by default, so this convention is subsumed by `harness-format` running oxfmt and is removed as a lint rule.
- The bun harness engine retains only the 19 structural rules. The 14 TS AST rule modules are removed once oxlint (12 rules) and oxfmt (`leafFunctionBlankLines`) cover them and `implicitLambdaIt` is dropped.

### Components

- `.oxlintrc.json` (config) at `docs/harness/bun/.oxlintrc.json`: built-in rule severities (slash form, all set to `error` so oxlint always emits — the harness re-derives the authoritative severity from the manifest), `jsPlugins: ["./oxlint-plugins/harness.mjs"]`, and `overrides` as needed. Spike-verified (Q3): `jsPlugins` paths resolve relative to the config file location, so the adapter may run oxlint from the repo root while keeping config-relative plugin paths.
- Custom oxlint rule plugin: a single ESM module `docs/harness/bun/oxlint-plugins/harness.mjs` (default export `{ meta: { name: "harness" }, rules: { "<rule-id>": { meta: { fixable: "code" }, create(context) { ... } } } }`) declaring the 6 custom rules and reporting via `context.report({ node, message, fix })`. Spike verification (oxlint 1.68.0): a `.mjs` ESM plugin loads and autofixes WITHOUT any `package.json` (Q1), so the existing standalone-Bun self-install model is preserved — no `package.json`, no declared devDependencies. Provisioning is by `bunx oxlint@1.68.0` / `bunx oxfmt@0.53.0`, which self-provision the pinned binaries with no separate install step (Q4, Q5). oxlint shells out to `node` to load the JS plugin (Q6), so Node availability is a prerequisite for the custom-rule path; built-in oxlint rules and oxfmt run from the binary and do not need Node.
- `.oxfmtrc.json` (config) at `docs/harness/bun/.oxfmtrc.json`: `printWidth`, `tabWidth`, `semi`, `singleQuote`, import sorting. Verify defaults and key names against official oxfmt docs at implementation.
- Adapter in `harness-check`: spawns `bunx oxlint@1.68.0 --config docs/harness/bun/.oxlintrc.json --format json <ts files>`, parses results, maps each diagnostic into the harness `Finding` shape (severity/category/message/file/startLine/startColumn + fix safety) so output stays uniform.

### Data flow (check)

1. `harness-check` runs the harness engine for the 19 structural rules, producing `Finding[]`.
2. `harness-check` invokes `oxlint --format=json` over the target's TS sources.
3. The adapter maps oxlint diagnostics into `Finding[]`. The diagnostic `code` field uses the paren form `plugin(rule)` (spike-verified Q2: `eslint(no-console)`, `import(no-namespace)`, `typescript(ban-ts-comment)` for built-ins; `harness(<rule-id>)` for the custom plugin) — note this differs from the slash form (`eslint/no-console`) used in `.oxlintrc.json` rule keys. A static `OXLINT_CODE_TO_CATEGORY` map keyed on the paren-form `code` resolves the harness category. The manifest is the single source of truth for severity and enablement: the adapter DROPS diagnostics whose mapped category is disabled in the manifest and sets each `Finding.severity` from `ctx.severityOf(category)` (not from the oxlint severity). Spike verification confirmed oxlint `--format json` does NOT expose per-diagnostic fix availability or safety (diagnostic keys are `message`, `code`, `severity`, `filename`, `labels`, `help`, `url`, `causes`, `related` only). The adapter therefore assigns `fix` safety from a static per-category policy (built-in safe-fixable rules → `safe`; custom-plugin rules and suggestion/dangerous-tier built-ins → `unsafe`/`manual`). The `harness-check` reporter records that a finding is fixable plus its safety (so `[*] fixable` still renders) but does NOT carry `edits`. The default `harness-format` path does NOT run `oxlint --fix`: formatting is owned by `oxfmt --write` only, and lint-fix application is left to the explicit `oxlint --fix` command a developer may run directly. This is behavior-preserving — it mirrors the current state where, e.g., `greaterThanComparison` is `safety="safe"` yet ∉ `FORMAT_ALLOWLIST`, so it is reported-as-fixable but never auto-applied during format. The custom oxlint rules still ship fixers (`meta.fixable="code"`); only the auto-application from `harness-format` is withheld.
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
- No `package.json` and no declared devDependencies: spike-verified that a `.mjs` ESM plugin loads with zero `package.json` (Q1) and that `bunx oxlint@1.68.0` / `bunx oxfmt@0.53.0` self-provision the pinned binaries without a separate install (Q4, Q5). This preserves the existing standalone-Bun self-install model rather than introducing a `package.json` + devDeps surface.
- The custom-rule path requires Node on the target machine: oxlint loads JS plugins via a Node runtime, not Bun (spike-verified Q6). This is a new prerequisite beyond the standalone-Bun model. Built-in oxlint rules and oxfmt run from the binary and need no Node, so even without Node the bun `check` keeps built-in coverage plus the 19 structural rules; only the 6 custom-plugin conventions go dark. On plugin-load failure oxlint emits a non-silent error (`Failed to parse oxlint configuration file.` then `x Failed to load JS plugin: <path>` with the verbatim Node error); the adapter MUST surface this and fail the custom-rule capability rather than passing silently.
- oxlint `--format json` carries no per-diagnostic fix metadata (spike-verified); fix safety is therefore a static per-rule policy in the adapter rather than read from oxlint. Fix application is NOT auto-run by `harness-format` (which owns formatting via `oxfmt --write` only); `oxlint --fix` stays an explicit developer command. This is behavior-preserving against the current `harness-format` contract, which auto-applies only `FORMAT_ALLOWLIST` categories and leaves safe lint fixes (e.g. `greaterThanComparison`) reported-but-unapplied.
- Provisioning introduces a network dependency: `bunx oxlint@1.68.0` / `bunx oxfmt@0.53.0` download the pinned binaries on first use. This mirrors the gradle/ktlint precedent (ktlint resolved via gradle's network dependency resolution), so it is not a new class of dependency for the harness. Self-check fixtures exercising the oxlint/oxfmt path MUST guard on tool availability and skip with a clear message when offline/unprovisioned, mirroring the existing `bun not in PATH → skip` pattern.
- Two-engine `check` on bun (oxlint for TS code-style, harness engine for structural) is not a compromise from the alpha API; it is the necessary consequence of oxlint's scope.

## Sequencing

1. bun stack first, fully implemented and committed.
2. uv stack second.

## Open Verification Items

Resolved by spike (oxlint 1.68.0, oxfmt 0.53.0, 2026-06-03):

- oxlint `jsPlugins` config schema and the custom-rule module/`context.report({fix})` signature: confirmed working under Bun-invoked oxlint (plugin loaded via Node ESM, fixer rewrote source).
- oxlint `--format json` diagnostic shape and absence of per-diagnostic fix metadata: confirmed.
- oxfmt subsumes `leafFunctionBlankLines`: confirmed (collapses consecutive blank lines to one).
- Provisioning: `.mjs` ESM plugin loads with zero `package.json` (Q1); `bunx oxlint@1.68.0` and `bunx oxfmt@0.53.0` self-provision without a separate install (Q4, Q5). No `package.json`/devDeps required.
- Diagnostic `code` format is `plugin(rule)` paren form (Q2), distinct from the slash form used in config rule keys.
- `jsPlugins` paths resolve relative to the config file (Q3), so the adapter runs oxlint from the repo root with config-relative plugin paths.
- Node prerequisite + load-failure signal (Q6): oxlint requires `node` to load JS plugins and emits a clear `Failed to load JS plugin: <path>` error on failure.
- Comment access for the doc-comment custom rules (`multilineDocStyle`, `publicDeclarationDocComment`): the plugin `context.sourceCode` exposes `getAllComments()`/`getCommentsBefore(node)`; comments carry `range`/`start`/`end`/`loc`, and fixers can rewrite them via `replaceTextRange(range, text)`. Both doc rules migrate as custom plugins (detection; `publicDeclarationDocComment` fix safety `manual`, `multilineDocStyle` `safe`).
- `silentCatch` is not reducible to `eslint/no-empty` (non-empty-but-swallowing catch is not flagged); it stays a custom rule.

Remaining (resolve at implementation):

- Exact oxlint built-in rule IDs that cover each mappable convention, in particular whether `eslint/no-underscore-dangle` exists for `leadingUnderscore` (confirm via `oxlint --rules`).
- ruff rule-family selection that maps to existing uv conventions (uv follow-up).

<!-- @formatter:on -->
