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

import { lstatSync, realpathSync } from "node:fs";
import { basename, dirname, isAbsolute, join, relative, resolve, sep } from "node:path";

interface HookInput {
  tool_name?: unknown;
  tool_input?: {
    file_path?: unknown;
  };
}

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

function isMissingPathError(error: unknown): boolean {
  return error instanceof Error && "code" in error && error.code === "ENOENT";
}

function assertInside(root: string, candidate: string): void {
  const fromRoot = relative(root, candidate);
  if (isAbsolute(fromRoot) || fromRoot === ".." || fromRoot.startsWith(`..${sep}`)) {
    deny("The target path is outside the project root.");
  }
}

function resolveWriteTarget(root: string, filePath: string): string {
  const requested = resolve(root, filePath);
  try {
    lstatSync(requested);
  } catch (error) {
    if (!isMissingPathError(error)) {
      throw error;
    }
    const realParent = realpathSync(dirname(requested));
    return join(realParent, basename(requested));
  }
  return realpathSync(requested);
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
  const target = resolveWriteTarget(projectRoot, filePath);
  assertInside(projectRoot, target);
} catch {
  deny("The target path cannot be resolved safely.");
}
```

The example resolves an existing final component, including a symlink, before checking containment.
For a nonexistent destination, it resolves the real parent and appends only the new basename.
It fails closed for dangling final symlinks and when the parent directory does not exist or cannot be resolved.
Adapt missing-parent behavior only when the tool contract explicitly permits creating and validating parent directories.

## Symlink Attack Examples

- Existing final escape: `project/output.txt` points to `/tmp/outside.txt`.
  The helper resolves `/tmp/outside.txt`, and `assertInside` denies the request.
- Dangling final escape: `project/output.txt` points to a nonexistent outside file.
  `lstatSync` detects the link, `realpathSync` fails, and the hook denies the request.
- New contained file: `project/generated/output.txt` does not exist and `project/generated` is a real contained directory.
  The helper returns the canonical parent plus `output.txt`.
- Symlinked parent escape: `project/generated` points to `/tmp/generated`.
  Resolving the parent exposes the outside target, and `assertInside` denies the request.

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
- existing final symlink outside the project
- dangling final symlink
- nonexistent destination under a contained real parent
- missing parent directory
- path containing spaces and shell metacharacters
- malformed or oversized JSON
- missing environment variables
- secret-bearing input and error paths
