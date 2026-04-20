# Plugin Release

Open this file when the plugin work reaches installation, packaging review, or persistent-data decisions.

## Install scope

- use project scope when the plugin should travel with a repository
- use user scope when the plugin is personal or machine-local

## Persistent data example split

```text
${CLAUDE_PLUGIN_ROOT}/hooks/check.sh        # shipped with the plugin (read-only)
${CLAUDE_PLUGIN_DATA}/cache/index.json      # generated at runtime (writable)
```

The invariant is stated in `SKILL.md` under Data boundary guidance. Use this split as a concrete reference when reviewing whether a starter file or script respects the boundary.

## Release review

- confirm `plugin.json` still matches the real root layout
- confirm optional components exist only when the plugin uses them
- confirm relative paths still start with `./`
- confirm bundled files are read from `${CLAUDE_PLUGIN_ROOT}` and generated state is written under `${CLAUDE_PLUGIN_DATA}`
- confirm no `__pycache__`, `.DS_Store`, or other build artifacts are committed
- confirm shell scripts under `assets/` have executable permission bits
