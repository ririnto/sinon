# Node.js Resource-Lifetime Audit

This audit covers checked-in Node.js scripts, tests, TypeScript configuration, and Node.js commands delegated by repository hooks.
It excludes installed dependencies and external CLI implementations because they are not repository-owned code.
The review looked for persistent processes, timers, listeners, streams, servers, watchers, and unbounded caches.

| Component | Assessment | Evidence |
| --- | --- | --- |
| `.claude-plugin/marketplace` generator and tests | No leak found. | The generator awaits filesystem operations and keeps maps and sets within each invocation. |
| `plugins/spec-driven-development` scripts and tests | No leak found. | The scripts perform per-invocation filesystem traversals and keep local collections without persistent processes, timers, listeners, or watchers. |
| `plugins/harness/tooling/pnpm` | Unverified liveness candidate; no leak confirmed. | Three test files use `spawnSync` without a timeout, so a hung tool can block its test; no hung child or retained process was observed. |
| `plugins/codegraph` hooks and runtime tests | External lifecycle unverified; no repository-owned runtime leak confirmed. | Hook configuration delegates to an external `npx @colbymchenry/codegraph` command; runtime tests use finite local Git and shell stubs without timeouts, and the host and external CLI cleanup were not exercised. |
| `plugins/observability-assets/tests/promtool` download | Confirmed medium-severity response-body leak; fixed. | A bounded local HTTP server reproduced open 200 and 503 response bodies after rejection, and separately reproduced an unread checksum body after archive consumption failed. |
| `plugins/observability-assets/tests/promtool` child execution | Confirmed low-severity child-process leak on wrapper-only SIGTERM; fixed. | A test runs the exported runner with a local 30-second child, signals only the wrapper, and verifies status 143, child exit, and signal-listener cleanup. |
| `plugins/observability-assets/tests/promtool` archive parser | No leak found. | Invalid-format and truncated-gzip tests reject; parsing is in-process and creates no persistent operating-system resources. |
| Root `oxfmt.config.ts` and `oxlint.config.ts` | No leak found. | These are scoped configuration modules with no persistent process, timer, listener, stream, server, watcher, or cache. |

The download regression tests also cover sibling cancellation when either response status or body consumption fails.
Separate child-runner checks confirm signal-listener cleanup after normal exit, spawn failure, and a child terminated independently of its wrapper.
The CodeGraph runtime tests use local stubs, but the installed host and external package lifecycle remain unverified.
