---
name: frontmatter-parsing
description: >-
  Complete Bun and YAML patterns for parsing, validating, serializing, and safely locating plugin-defined `.local.md` state.
---

# YAML Frontmatter Parsing

Open this reference when plugin code must both read and write `.claude/<plugin>.local.md`, preserve a Markdown body, or consume path-valued settings.

## Dependency

Use a real YAML parser.
The example is a standalone Bun module with a versioned package import:

```ts
import { parseDocument, stringify } from "yaml@2.8.1";
```

Pin or update the version according to the plugin's dependency policy.

## Read and Validate

Keep frontmatter extraction, YAML parsing, and schema validation separate so errors name the failing boundary.

```ts
#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import { randomUUID } from "node:crypto";
import { lstatSync, realpathSync } from "node:fs";
import { readFile, rename, rm, writeFile } from "node:fs/promises";
import { basename, dirname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { parseDocument, stringify } from "yaml@2.8.1";

interface ParsedMarkdown {
  data: Record<string, unknown>;
  body: string;
}

function parseLocalMarkdown(source: string): ParsedMarkdown {
  const lines = source.split(/\r?\n/u);
  if (lines[0] !== "---") {
    throw new Error("state file must begin with ---");
  }
  const closing = lines.indexOf("---", 1);
  if (closing === -1) {
    throw new Error("state file is missing its closing --- delimiter");
  }
  const document = parseDocument(lines.slice(1, closing).join("\n"), { uniqueKeys: true });
  if (document.errors.length > 0) {
    throw new Error(document.errors.map((error) => error.message).join("; "));
  }
  const value = document.toJS();
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new Error("frontmatter must be a YAML mapping");
  }
  return {
    data: value as Record<string, unknown>,
    body: lines.slice(closing + 1).join("\n").replace(/^\n/u, ""),
  };
}

function serializeLocalMarkdown(data: Record<string, unknown>, body: string): string {
  const yaml = stringify(data, { lineWidth: 100 }).trimEnd();
  const normalizedBody = body.trimEnd();
  return `---\n${yaml}\n---\n\n${normalizedBody}\n`;
}

function isMissingPathError(error: unknown): boolean {
  return error instanceof Error && "code" in error && error.code === "ENOENT";
}

function assertInside(base: string, candidate: string): void {
  const fromBase = relative(base, candidate);
  if (isAbsolute(fromBase) || fromBase === ".." || fromBase.startsWith(`..${sep}`)) {
    throw new Error("configured path escapes the allowed directory");
  }
}

function resolveExistingInside(baseDirectory: string, configuredPath: string): string {
  const base = realpathSync(baseDirectory);
  const candidate = realpathSync(resolve(base, configuredPath));
  assertInside(base, candidate);
  return candidate;
}

function resolveWriteInside(baseDirectory: string, configuredPath: string): string {
  const base = realpathSync(baseDirectory);
  const requested = resolve(base, configuredPath);
  try {
    lstatSync(requested);
  } catch (error) {
    if (!isMissingPathError(error)) {
      throw error;
    }
    const realParent = realpathSync(dirname(requested));
    const candidate = join(realParent, basename(requested));
    assertInside(base, candidate);
    return candidate;
  }
  const candidate = realpathSync(requested);
  assertInside(base, candidate);
  return candidate;
}

async function writeAtomic(path: string, content: string): Promise<void> {
  const temporary = join(
    dirname(path),
    `.${basename(path)}.${process.pid}.${randomUUID()}.tmp`
  );
  try {
    await writeFile(temporary, content, {
      encoding: "utf-8",
      flag: "wx",
      mode: 0o600,
    });
    await rename(temporary, path);
  } catch (error) {
    await rm(temporary, { force: true });
    throw error;
  }
}

const projectDirectory = process.env["CLAUDE_PROJECT_DIR"] ?? process.cwd();
const configuredPath = ".claude/example-plugin.local.md";
const sourcePath = resolveExistingInside(projectDirectory, configuredPath);
const source = await readFile(sourcePath, "utf-8");
const parsed = parseLocalMarkdown(source);
parsed.data["enabled"] = false;
const statePath = resolveWriteInside(projectDirectory, configuredPath);
await writeAtomic(statePath, serializeLocalMarkdown(parsed.data, parsed.body));
```

`resolveExistingInside` is for an existing configured input path.
It resolves the final component before the read, so an in-project symlink to an outside file is rejected.

`resolveWriteInside` resolves an existing final component before a write.
For a new destination, it resolves the real parent and appends only the new basename.
It rejects dangling final symlinks and fails when the parent does not exist.
Create and validate an authorized parent explicitly when that is part of the contract.

`writeAtomic` creates an unpredictable same-directory temporary file with exclusive creation.
The final rename replaces a raced-in symlink instead of following it.

## Symlink Attack Examples

- Existing read escape: `.claude/example-plugin.local.md` points to `/tmp/outside.md`.
  `resolveExistingInside` resolves `/tmp/outside.md` and rejects it before `readFile`.
- Existing write escape: the configured destination points to `/tmp/outside.md`.
  `resolveWriteInside` resolves the final symlink and rejects the outside target.
- Dangling write escape: the destination is a symlink to a nonexistent outside file.
  `lstatSync` detects the link, `realpathSync` fails, and the write is rejected.
- New contained destination: the final file does not exist, but its real parent is inside the project.
  The helper returns the canonical parent plus the new basename.
- Parent escape: `.claude/plugin-state` points to an outside directory.
  Resolving the parent exposes the escape and the containment check rejects it.

## Schema Policy

After parsing, define these decisions explicitly:

- required and optional keys
- default values
- accepted primitive and collection types
- enum members
- numeric bounds
- whether unknown keys are rejected or ignored
- whether YAML aliases and custom tags are accepted
- behavior for an absent file versus an invalid file

Reject duplicate keys through the parser.
Do not let YAML coercion silently convert a value that the plugin expects to be a string.

## Serialization Rules

- Serialize typed values with the YAML library.
- Preserve the Markdown body intentionally.
- Write to a temporary file in the same directory, then rename atomically.
- Use restrictive permissions for user-local state.
- Do not write secrets into the state file.
- Do not construct YAML by quoting user input manually.

## Error Contract

Use focused errors that distinguish:

- file absent
- missing delimiter
- malformed YAML
- duplicate key
- non-mapping frontmatter
- schema violation
- path containment failure
- filesystem read or write failure

An absent optional state file MAY use defaults.
Malformed or invalid state SHOULD fail closed unless the plugin documents a safer recovery policy.

## Test Matrix

- quoted strings containing `:` and `#`
- boolean and numeric types
- arrays and nested mappings
- literal and folded multiline strings
- duplicate keys
- missing closing delimiter
- YAML syntax error
- unknown key
- invalid enum and out-of-range number
- relative contained path
- absolute or relative escape
- symlinked parent escape
- existing final symlink escape on read and write
- dangling final symlink on write
- nonexistent destination under a contained real parent
- path containing spaces
- atomic write interruption
- body preservation after serialization
