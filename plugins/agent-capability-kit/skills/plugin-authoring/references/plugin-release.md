---
name: plugin-release
description: |-
  Install scope, persistent data boundaries, and packaging review checklist. Open this file when the plugin work reaches installation, packaging review, or persistent-data decisions.
---

# Plugin Release

Open this file when the plugin work reaches installation, packaging review, or persistent-data decisions.

## Install scope

- use project scope when the plugin should travel with a repository
- use user scope when the plugin is personal or machine scoped

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
- confirm directory-typed fields use the trailing-slash form (`"skills": "./skills/"`, `"commands": "./commands/"`)
- confirm file-typed fields use the canonical exact filename (`"hooks": "./hooks/hooks.json"`, `"mcpServers": "./.mcp.json"`, `"lspServers": "./.lsp.json"`, `"settings": "./settings.json"`)
- confirm each declared file-typed key has the matching plugin-root file (`hooks/hooks.json`, `.mcp.json`, `.lsp.json`, `settings.json`), and each existing plugin-root file is declared in the manifest
- confirm `agents`, `version`, and `interface` keys remain absent from the manifest (forbidden manifest keys per `plugin-validator` Critical rules)
- confirm bundled files are read from `${CLAUDE_PLUGIN_ROOT}` and generated state is written under `${CLAUDE_PLUGIN_DATA}`
- confirm no `__pycache__`, `.DS_Store`, or other build artifacts are committed
- confirm shell scripts under `assets/` have executable permission bits
