# Plugin Release

Open this file when the plugin work reaches installation, packaging review, or persistent-data decisions.

## Install scope

- use project scope when the plugin should travel with a repository
- use user scope when the plugin is personal or machine-local

## Persistent data

- use `${CLAUDE_PLUGIN_ROOT}` for bundled files
- use `${CLAUDE_PLUGIN_DATA}` for generated or persistent runtime data

Example split:

```text
${CLAUDE_PLUGIN_ROOT}/hooks/check.sh        # shipped with the plugin
${CLAUDE_PLUGIN_DATA}/cache/index.json      # generated at runtime
```

## Release review

- confirm `plugin.json` still matches the real root layout
- confirm optional components exist only when the plugin uses them
- confirm relative paths still start with `./`
- confirm bundled files are read from `${CLAUDE_PLUGIN_ROOT}` and generated state is written under `${CLAUDE_PLUGIN_DATA}`
