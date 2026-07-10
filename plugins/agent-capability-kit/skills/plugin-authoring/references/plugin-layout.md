---
name: plugin-layout
description: >-
  Composite plugin-root trees and custom-path examples for selected runtime surfaces.
---

# Plugin Layout

Open this reference when a plugin combines several runtime surfaces or needs custom paths in the manifest.

## Composite Root

```text
your-plugin/
+-- .claude-plugin/
|   +-- plugin.json
+-- README.md
+-- agents/
+-- skills/
+-- hooks/
|   +-- hooks.json
|   +-- check.ts
+-- .mcp.json
+-- servers/
|   +-- example-mcp.ts
+-- .lsp.json
+-- lsp/
|   +-- example-lsp.ts
+-- output-styles/
+-- themes/
+-- monitors/
|   +-- monitors.json
|   +-- watch.ts
+-- bin/
```

Keep only selected surfaces.
Helper programs stay beside or under the surface that owns them.

## Replacing Paths

`commands`, `agents`, `outputStyles`, `experimental.themes`, and `experimental.monitors` replace their default scan when declared.
Include the default path explicitly when adding a custom path:

```json
{
  "agents": ["./agents/", "./special/agents/"],
  "outputStyles": ["./output-styles/", "./branding/styles/"],
  "experimental": {
    "themes": ["./themes/", "./branding/themes/"]
  }
}
```

`experimental.monitors` has two valid shapes.
Use one relative path string for a non-default monitor file:

```json
{
  "experimental": {
    "monitors": "./operations/monitors.json"
  }
}
```

Alternatively, declare monitor objects inline:

```json
{
  "experimental": {
    "monitors": [
      {
        "name": "deployment-status",
        "command": "tail -F ./logs/deployment.log",
        "description": "Deployment status changes"
      }
    ]
  }
}
```

Do not use an array of monitor path strings.
When several monitor definitions belong in files, consolidate them into one JSON array and point `experimental.monitors` at that file.

## Additive Skills

`skills` adds to the default `skills/` scan:

```json
{
  "skills": ["./shared-skills/"]
}
```

## Components with Merge Rules

`hooks`, `mcpServers`, and `lspServers` combine sources according to their component-specific rules.
Use their default files for the ordinary path and manifest fields only for custom paths or inline definitions.

Manifest `settings` is an inline object.
Plugin-root `settings.json` is a separate auto-discovered file and is not declared through a path.
