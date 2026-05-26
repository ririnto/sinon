# Bun Harness Validator

Run `bun --install=fallback run docs/harness/bun/harness-check.ts`.

## Structural parity

The Bun validator mirrors the Gradle harness model through a shared `RuleContext`, a `HarnessCheckRule` interface, and the enum-style `HarnessCheck` registry in `harness-check.ts`. Rules stay in a flat `rules/` directory because TypeScript imports and bundling do not need Gradle's `rules/fs`, `rules/text`, and `rules/ast` package hierarchy; the registry category and manifest key provide the grouping boundary.
