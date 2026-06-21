---
name: plugin-layout
description: |-
  Expanded plugin-root tree examples for composite surfaces, helper directories, and layout exceptions.
  Open this file when `SKILL.md` already covers the baseline shape and a richer tree is needed.
---

# Plugin Layout

Open this file when `SKILL.md` already covers the baseline shape and you need a deeper layout for a plugin that combines surfaces, adds helper directories, or hits a layout exception.

## Composite roots

Use this when a Claude Code plugin ships multiple optional surfaces and the simple starter tree is no longer descriptive.

```text
your-plugin/
+-- .claude-plugin/
|   +-- plugin.json
+-- README.md
+-- agents/
+-- skills/
+-- hooks/
|   +-- check.sh
|   +-- hooks.json
+-- .mcp.json
+-- servers/
|   +-- example-mcp.py
+-- bin/
|   +-- my-tool
+-- output-styles/
    +-- executive-summary.md
```

This shape is useful when policy, server, and formatting surfaces all ship together for Claude Code.

## Surface-specific helper directories

Some surfaces need adjacent helper files that do not fit the bare root list:

```text
your-plugin/
+-- .claude-plugin/
|   +-- plugin.json
+-- README.md
+-- hooks/
|   +-- check.sh
|   +-- hooks.json
+-- lsp/
|   +-- example-lsp.py
+-- monitors/
|   +-- monitors.json
|   +-- watch.sh
+-- servers/
    +-- example-mcp.py
```

Use these helper directories only when the associated surface needs local code beside its manifest or content file.

## Layout exceptions

- prefer one helper directory per surface so the plugin tree stays readable when a surface grows beyond one file
- when a surface needs multiple files, group them under a directory named after the surface rather than scattering files at the root
- keep runtime output and generated state outside the shipped surface files unless the specific component explicitly expects it

## Default surfaces and the manifest

Open this subsection whenever a composite-root tree contains optional surfaces.
Use it to verify manifest entries against Claude Code discovery rules.

Claude Code auto-discovers default component locations at the plugin root.
Do not declare default entries in `plugin.json` when the default path is the only value.
Default entries include:

- `skills/`, `agents/`, `output-styles/`, and `themes/`
- `hooks/hooks.json`, `.mcp.json`, `.lsp.json`, `settings.json`, and `monitors/monitors.json`

Executable files under `bin/` are added to Bash `PATH` while the plugin is enabled and do not use a manifest path field.

Use manifest component fields only for custom paths or inline configuration.
When a manifest component field declares a string path, the matching plugin-root file or directory MUST exist.
Inline object component fields do not need companion files.

Path fields have field-specific merge behavior:

- `skills` adds to the default `skills/` scan.
- `agents` and `outputStyles` replace the default unless the default path is listed explicitly.
- `experimental.themes` and `experimental.monitors` follow the same replacement behavior.
- hooks, MCP servers, and LSP servers follow their own merge rules.

## When this file matters

Open this file when you need to compare a combined plugin tree against the manifest, or when a surface needs extra files that would make the baseline tree misleading.
