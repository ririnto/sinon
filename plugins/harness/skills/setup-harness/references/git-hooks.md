# Git Hooks Reference
Open this reference when installing, refreshing, or debugging local git hooks for a target repository.


Install hooks only when `docs/harness/config.json` declares `gates.hooks.enabled: true`.

Hooks are simple commit-time validation script callers. The setup script creates `.git/hooks/pre-commit` that invokes `scripts/harness/validate_harness.sh` directly. No separate tracked hook scripts are needed - the harness validator is the single enforcement point.

## Setup

Run the setup script after cloning or when you want to install or refresh hooks:

```bash
sh scripts/harness/setup-hooks.sh
```

The script creates an executable pre-commit hook at `.git/hooks/pre-commit`. Running the script again overwrites the hook with the latest version (idempotent).

## Generated .git/hooks/pre-commit

The generated hook is a lightweight wrapper:

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

if [ -f scripts/harness/validate_harness.sh ]; then
    sh scripts/harness/validate_harness.sh
else
    echo "warning: harness validator not found; skipping validation" >&2
fi
```

Keep hooks fast and offline. CI remains the authoritative shared enforcement surface.
