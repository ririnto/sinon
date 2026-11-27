# Promtool child lifecycle

The model checks that the runner reports its result only after the child closes and its signal listeners are removed.
It also checks that the runner forwards signals in order, including a signal received before a child PID is known.

![Child lifecycle statechart](ChildLifecycle.svg)

- [TLA+ model](../../assets/child-lifecycle/ChildLifecycle.tla)
- [TLC configuration](../../assets/child-lifecycle/ChildLifecycle.cfg)
- [Mermaid source](ChildLifecycle.mmd)
- [Statechart view](../../scripts/child-lifecycle/view.ts)

Run `pnpm run check:promtool:child` from the repository root.
The [shared model notes](../../../../../plugins/observability-assets/tests/promtool/model/README.md#child-lifecycle) describe the one-child, two-signal bound and the mapping to runtime behavior.
