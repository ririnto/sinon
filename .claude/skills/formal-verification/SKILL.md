---
name: formal-verification
description: Check the Promtool runner's bounded TLA+ models when asset downloads, child-process signaling, or cleanup order changes.
---

# Formal verification

Use this skill for changes to the [Promtool runner](../../../plugins/observability-assets/tests/promtool/run.ts).

- [Child lifecycle](references/child-lifecycle/README.md) checks signal forwarding and the result reported after the child closes.
- [Download lifecycle](references/download-lifecycle/README.md) checks that success waits for both assets and that failure waits for pending work to settle.

From the repository root, use the installed workspace dependencies and Java.

```sh
pnpm run check:formal-layout
pnpm run check:promtool
pnpm run check:statecharts
```

Use pstack `/statechart` to interpret a TLC counterexample or update a Mermaid projection.
Regenerate both SVGs from their Mermaid sources with `pnpm run statecharts:render`.
The models bound the modeled interleavings and do not replace the runner's process and archive tests.
