---
name: plugin-release
description: |-
  Install scope, persistent data boundaries, and packaging review checklist.
  Open this file when the plugin work reaches installation, packaging review, or persistent-data decisions.
---

# Plugin Release

Open this file when the plugin work reaches installation, packaging review, or persistent-data decisions.

## Install scope

- use project scope when the plugin should travel with a repository
- use user scope when the plugin is personal or machine scoped

## Persistent data example split

```text
${CLAUDE_PLUGIN_ROOT}/hooks/check.ts        # shipped with the plugin (read-only)
${CLAUDE_PLUGIN_DATA}/cache/index.json      # generated at runtime (writable)
```

The package's data boundary guidance defines this invariant.
Use this split as a concrete reference when reviewing whether a starter file or script respects the boundary.

## Release review

- confirm `plugin.json` still matches the real root layout
- confirm optional components exist only when the plugin uses them
- confirm relative paths still start with `./`
- confirm default component locations are omitted from the manifest when they are the only value
- confirm custom directory-typed fields use the trailing-slash form
- confirm declared string paths resolve inside the plugin root
- confirm inline component configuration follows the official schema shape
- confirm `interface` is absent and `version` is absent unless the plugin has a semver release policy
- confirm bundled files are read from `${CLAUDE_PLUGIN_ROOT}` and generated state is written under `${CLAUDE_PLUGIN_DATA}`
- confirm no `__pycache__`, `.DS_Store`, or other build artifacts are committed
- confirm Bun scripts referenced by runtime config exist under the plugin root
