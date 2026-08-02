# Astrolabe

Astrolabe packages portable engineering guidance for Claude Code and Codex.
Both runtimes use the canonical shared skill tree under `skills/`.

## Package Layout

- `.claude-plugin/plugin.json` declares the Claude package.
- `.codex-plugin/plugin.json` declares Codex metadata and the shared `./skills/` path.
- `hooks/` contains Claude hook commands.
- `skills/` contains the canonical shared skill inventory and skill-owned references.

The package root contains every runtime asset and declared path.
The package does not load external instruction state, use network services, or maintain persistent runtime state.

## Claude Hook Commands

Claude hook commands require `node` on `PATH`.
The `main` and `subagent` role commands are declared in [`hooks/hooks.json`](hooks/hooks.json).

## Skills

The `skills/` tree is the canonical shared inventory for both runtimes.

- `orchestrating-work` selects dependency-aware work graphs and reconciles results.
- `executing-delegated-work` executes scoped assignments and reports evidence.
- `authoring-instructions` writes durable instructions and references.
- `authoring-code` applies language-scoped source conventions.
- `building-linter-adapters` designs native-linter adapters, diagnostics, and fixtures.
- `using-workflow-tools` selects structural navigation and output-reduction tools.
- `validating-observability-tools` preserves observability facts and package checks.

## Installation And Checks

Install from the Sinon marketplace:

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

claude plugin install astrolabe@sinon
```

Load a local checkout:

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

claude --plugin-dir {{repository-path}}/plugins/astrolabe
```

Run the required package test from the repository root:

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

bun test scripts/tests/astrolabe
```

Run the optional Claude validator when the CLI is available:

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-

if command -v claude >/dev/null 2>&1; then
  claude plugin validate plugins/astrolabe
else
  printf '%s\n' 'claude CLI unavailable; skipped optional plugin validation.'
fi
```

Inspect validator output as well as its exit status.

## License

This package uses Apache-2.0 in `LICENSE`.
`ATTRIBUTION.txt` records the authorship and independent implementation statement.
