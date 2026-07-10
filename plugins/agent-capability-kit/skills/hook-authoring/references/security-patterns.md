---
description: >-
  Input validation, filesystem containment, credential handling, and command safety for Claude Code hooks.
---

# Hook Security Patterns

Open this reference when hook input controls a filesystem path, command, network request, credential, or persistent write.

## Treat Input as Untrusted

Validate the event name, tool name, required fields, types, and size before acting.
Do not interpolate hook input into a shell command.
Prefer exec-form commands with a fixed executable and argument vector.

This Bun hook denies `Write` and `Edit` targets whose real parent is outside the project root:

```ts
#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import { realpathSync } from "node:fs";
import { basename, dirname, isAbsolute, join, relative, resolve } from "node:path";

type HookInput = {
  tool_name?: unknown;
  tool_input?: {
    file_path?: unknown;
  };
};

function deny(reason: string): never {
  console.log(JSON.stringify({
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "deny",
      permissionDecisionReason: reason,
    },
  }));
  process.exit(0);
}

let input: HookInput;
try {
  input = JSON.parse(await Bun.stdin.text()) as HookInput;
} catch {
  deny("The hook input is not valid JSON.");
}
const filePath = input.tool_input?.file_path;
const projectDir = process.env["CLAUDE_PROJECT_DIR"];
if (input.tool_name !== "Write" && input.tool_name !== "Edit") {
  process.exit(0);
}
if (typeof filePath !== "string" || !projectDir) {
  deny("The write request is missing a valid path or project root.");
}
try {
  const projectRoot = realpathSync(projectDir);
  const requested = isAbsolute(filePath) ? filePath : resolve(projectRoot, filePath);
  const realParent = realpathSync(dirname(requested));
  const target = join(realParent, basename(requested));
  const projectRelative = relative(projectRoot, target);
  if (projectRelative.startsWith("..") || isAbsolute(projectRelative)) {
    deny("The target path is outside the project root.");
  }
} catch {
  deny("The target path cannot be resolved safely.");
}
```

The example fails closed when the parent directory does not exist or cannot be resolved.
Adapt that behavior only when the tool contract explicitly permits creating missing parent directories.

## Command Safety

- Keep `command` fixed and move path placeholders into `args`.
- Do not use shell form for untrusted values.
- If shell syntax is required, pass untrusted data over stdin or a validated file, not string interpolation.
- Treat handler-level `if` as a best-effort filter, not an authorization boundary.
- Use Claude Code permissions for broad allow or deny policy and hook code for narrower deterministic checks.

## Credential and Network Safety

- Read credentials from the environment or host-managed secure configuration.
- Never include credentials in hook output, logs, error text, or URLs.
- Use HTTPS for remote HTTP hooks except explicit localhost development.
- Validate destination hosts when hook input can influence a URL.
- Bound response size and timeout for network handlers.

## Writable Data

- `${CLAUDE_PLUGIN_ROOT}` is ephemeral, bundled, and read-only by contract.
- `${CLAUDE_PLUGIN_DATA}` owns plugin-generated state.
- `${CLAUDE_PROJECT_DIR}` owns authorized project changes.
- Persistent writes MUST be atomic or concurrency-safe because matching handlers run in parallel.

## Security Verification

- relative path inside the project
- absolute path inside the project
- `..` traversal
- symlinked parent outside the project
- missing parent directory
- path containing spaces and shell metacharacters
- malformed or oversized JSON
- missing environment variables
- secret-bearing input and error paths
