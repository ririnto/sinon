# Promtool download lifecycle

The model checks that success waits for both downloads.
On failure, the runner must signal cancellation, wait for both requests to settle, and report the original rejection.

![Download lifecycle statechart](DownloadLifecycle.svg)

- [TLA+ model](../../assets/download-lifecycle/DownloadLifecycle.tla)
- [TLC configuration](../../assets/download-lifecycle/DownloadLifecycle.cfg)
- [Mermaid source](DownloadLifecycle.mmd)
- [Statechart view](../../scripts/download-lifecycle/view.ts)

Run `pnpm run check:promtool:download` from the repository root.
The [shared model notes](../../../../../plugins/observability-assets/tests/promtool/model/README.md#download-lifecycle) describe the two-asset bound and why the model does not prove network cancellation.
