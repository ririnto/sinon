---
name: plugin-settings
description: >-
  Author Claude Code plugin settings, prompted `userConfig`, and plugin-defined per-project state with safe YAML parsing.
  Use when choosing a configuration surface, wiring prompted values, or reading `.claude/<plugin>.local.md` from plugin code.
---

# Plugin Settings

Choose the right surface for each value (`settings.json`, `userConfig`, or `.local.md`), then parse plugin-defined state with a real YAML parser and an explicit schema.

## Choose the Surface

| Need | Surface | Host behavior |
| --- | --- | --- |
| Select a default main agent | plugin-root `settings.json` or manifest `settings` | Claude Code applies the supported `agent` key |
| Render subagent rows | plugin-root `settings.json` or manifest `settings` | Claude Code applies the supported `subagentStatusLine` key |
| Prompt for a user-specific value when enabling the plugin | manifest `userConfig` | Claude Code stores and substitutes the value |
| Keep project-local plugin state that plugin code reads | `.claude/<plugin>.local.md` | Plugin-defined convention; Claude Code does not parse it |

Do not mirror one value across surfaces.

## Plugin Settings

Plugin-root `settings.json` is auto-discovered, but it currently supports only `agent` and `subagentStatusLine`.

```json
{
  "agent": "code-reviewer"
}
```

`agent` MUST name an agent that Claude Code can resolve.
`subagentStatusLine` MUST be a command configuration, not a boolean:

```json
{
  "subagentStatusLine": {
    "type": "command",
    "command": "${CLAUDE_PLUGIN_ROOT}/scripts/subagent-statusline.sh"
  }
}
```

Manifest `settings` MAY provide the same allowlisted values inline.
Do not put `permissions`, `env`, `model`, `hooks`, `statusLine`, or arbitrary plugin keys in plugin-root `settings.json`.
Delete `settings.json` when neither supported setting is needed.

## Prompted User Configuration

Declare user-specific values in `userConfig`:

```json
{
  "userConfig": {
    "api_endpoint": {
      "type": "string",
      "title": "API endpoint",
      "description": "Base URL used by the plugin.",
      "default": "https://api.example.com"
    },
    "api_token": {
      "type": "string",
      "title": "API token",
      "description": "Token used to authenticate API requests.",
      "sensitive": true,
      "required": true
    }
  }
}
```

Keys MUST be valid identifiers.
Supported types are `string`, `number`, `boolean`, `directory`, and `file`.
Claude Code exposes each value as `${user_config.KEY}` in MCP and LSP configuration, hook commands, and monitor commands.
Non-sensitive values can also be substituted in skill and agent content.
Plugin subprocesses receive `CLAUDE_PLUGIN_OPTION_<KEY>`.

Mark secrets `sensitive: true`.
Sensitive values go to secure storage and have a small shared storage budget; do not use them for large payloads.

## Plugin-Defined Project State

Use `.claude/<plugin>.local.md` only when plugin hooks, agents, or programs need user-edited per-project state.
The file is not a Claude Code settings surface.
Add it to `.gitignore` and document its schema and defaults.

```markdown
---
enabled: true
mode: standard
max_retries: 3
output_directory: .plugin-output
---

# Example Plugin State

This file is read by the plugin on each invocation.
```

Recommended ignore patterns:

```gitignore
.claude/*.local.md
.claude/*.local.json
```

Do not store secrets in this file.

## Parse YAML, Not Lines

Regex, `grep`, and `sed` do not implement YAML quoting, comments, arrays, multiline scalars, aliases, duplicate-key handling, or type rules.
Use a YAML parser and validate the parsed object against the plugin's schema.

This standalone Bun example declares its dependency in the import and treats a missing file differently from invalid state:

```ts
#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import { readFile } from "node:fs/promises";
import { parseDocument } from "yaml@2.8.1";

type PluginState = {
  enabled: boolean;
  mode: "strict" | "standard" | "lenient";
  maxRetries: number;
  outputDirectory: string;
};

const DEFAULT_STATE: PluginState = {
  enabled: true,
  mode: "standard",
  maxRetries: 3,
  outputDirectory: ".plugin-output",
};

function parseFrontmatter(source: string): unknown {
  const lines = source.split(/\r?\n/u);
  if (lines[0] !== "---") {
    throw new Error("state file must start with YAML frontmatter");
  }
  const closing = lines.indexOf("---", 1);
  if (closing < 0) {
    throw new Error("state file is missing the closing frontmatter delimiter");
  }
  const document = parseDocument(lines.slice(1, closing).join("\n"), { uniqueKeys: true });
  if (document.errors.length > 0) {
    throw new Error(document.errors.map((error) => error.message).join("; "));
  }
  return document.toJS();
}

function validateState(value: unknown): PluginState {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new Error("frontmatter must be a mapping");
  }
  const state = value as Record<string, unknown>;
  const mode = state["mode"] ?? DEFAULT_STATE.mode;
  const maxRetries = state["max_retries"] ?? DEFAULT_STATE.maxRetries;
  const outputDirectory = state["output_directory"] ?? DEFAULT_STATE.outputDirectory;
  if (typeof state["enabled"] !== "undefined" && typeof state["enabled"] !== "boolean") {
    throw new Error("enabled must be a boolean");
  }
  if (mode !== "strict" && mode !== "standard" && mode !== "lenient") {
    throw new Error("mode must be strict, standard, or lenient");
  }
  if (!Number.isInteger(maxRetries) || Number(maxRetries) < 1 || Number(maxRetries) > 10) {
    throw new Error("max_retries must be an integer from 1 through 10");
  }
  if (typeof outputDirectory !== "string" || outputDirectory.length === 0) {
    throw new Error("output_directory must be a non-empty string");
  }
  return {
    enabled: state["enabled"] ?? DEFAULT_STATE.enabled,
    mode,
    maxRetries: Number(maxRetries),
    outputDirectory,
  };
}

async function loadState(path: string): Promise<PluginState> {
  try {
    return validateState(parseFrontmatter(await readFile(path, "utf8")));
  } catch (error) {
    if (error instanceof Error && "code" in error && error.code === "ENOENT") {
      return DEFAULT_STATE;
    }
    throw error;
  }
}

const state = await loadState(".claude/example-plugin.local.md");
console.log(JSON.stringify(state));
```

Absent state uses documented defaults.
Malformed or schema-invalid state fails with a focused error instead of silently changing behavior.

Open `references/frontmatter-parsing.md` when code must also serialize state, validate user-configured paths, or preserve a Markdown body.

## Path Safety

A string that came from YAML is still untrusted.
When a setting names a path:

1. define whether it must be relative or may be absolute
2. resolve the trusted base with `realpath`
3. resolve the existing target or real parent
4. compare with `path.relative`
5. reject any path outside the trusted base

Rejecting only the literal segment `..` does not prevent symlink traversal.

## Reload Semantics

- Plugin-defined `.local.md` content is read when the plugin program reads it; a fresh process sees the next saved value.
- Changes to plugin `settings.json`, manifest `settings`, hooks, MCP, LSP, agents, and other plugin components require `/reload-plugins` or a session restart.
- `userConfig` changes follow Claude Code's plugin configuration flow.

Document these distinctions in the plugin README.

## Verification

- each value has one owning surface
- plugin `settings.json` contains only `agent` or `subagentStatusLine`
- every `userConfig` key has a valid type, title, and description
- secrets are marked sensitive and are never logged
- `.local.md` is ignored by Git
- YAML uses a parser with duplicate-key errors
- parsed data receives explicit type, enum, range, and unknown-key policy
- missing state uses documented defaults
- invalid state produces a focused error
- path-valued settings are contained after real-path resolution
- YAML writes use a serializer and atomic replacement

## Output Contract

Return:

1. the chosen surface for each value
2. final `settings.json` or manifest configuration
3. the `.local.md` schema and template when project state is in scope
4. parser and validation code
5. `.gitignore` and README changes
6. reload and verification results

## Pitfalls

- Do not treat plugin `settings.json` as a general Claude Code settings file.
- Do not hand-parse YAML with line-oriented shell commands.
- Do not escape YAML manually; serialize typed data.
- Do not accept duplicate keys or implicit type changes silently.
- Do not rely on lexical `..` checks for path containment.
- Do not commit `.local.md` state or put secrets inside it.
- Do not claim a `.local.md` convention is a host feature.

## Reference

- `references/frontmatter-parsing.md` - open for a complete read/write module, atomic serialization, and real-path containment.
