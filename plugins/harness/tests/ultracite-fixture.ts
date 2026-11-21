import { spawnSync } from "node:child_process";
import type { SpawnSyncReturns } from "node:child_process";
import { createRequire } from "node:module";
import path from "node:path";

import { arrayValue, objectValue, stringValue } from "./structured-value.ts";

const repositoryRoot = path.resolve(import.meta.dirname, "../../..");
const ultraciteBin = path.resolve(
  path.dirname(createRequire(import.meta.url).resolve("ultracite/oxfmt")),
  "../../dist/index.js"
);

/**
 * Run the installed Ultracite CLI with its real lint and format backends.
 * Preserve intentionally invalid fixture bytes during checks and read structured lint diagnostics.
 */
export const runUltracite = (
  directory: string,
  filenames: string | readonly string[],
  action: "check" | "fix" = "check",
  configuration?: string,
  extraArguments: readonly string[] = []
) =>
  readUltraciteResult(
    spawnSync(
      process.execPath,
      [
        ultraciteBin,
        action,
        "--format",
        "json",
        ...(configuration !== undefined && configuration !== ""
          ? ["--config", configuration]
          : []),
        ...(typeof filenames === "string" ? [filenames] : filenames),
        ...extraArguments
      ],
      {
        cwd: directory,
        env: {
          ...process.env,
          PATH: `${path.join(repositoryRoot, "node_modules/.bin")}${path.delimiter}${process.env.PATH}`
        }
      }
    )
  );

/**
 * Read native rule IDs and affected filenames from a completed JSON-mode check or fix.
 */
export const readUltraciteResult = (
  result: SpawnSyncReturns<Buffer>
): {
  diagnostics: { code?: string; filename?: string; message: string }[];
  status: number | null;
} => {
  const output = result.stdout.toString();
  const start = output.search(/\{\s*"diagnostics"\s*:/u);
  if (result.error || start < 0) {
    throw new Error(
      `Ultracite produced no lint result: ${result.error?.message ?? result.stderr.toString()}`
    );
  }
  return {
    diagnostics: arrayValue(
      objectValue(JSON.parse(output.slice(start, output.lastIndexOf("}") + 1)))
        .diagnostics
    ).map((entry) => {
      const diagnostic = objectValue(entry);
      return {
        message: stringValue(diagnostic.message),
        ...(diagnostic.code === undefined
          ? {}
          : { code: stringValue(diagnostic.code) }),
        ...(diagnostic.filename === undefined
          ? {}
          : { filename: stringValue(diagnostic.filename) })
      };
    }),
    status: result.status
  };
};
