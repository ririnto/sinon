import type { SpawnSyncReturns } from "node:child_process";
import { createRequire } from "node:module";
import path from "node:path";

import type { createCommandFixture } from "./fixture-command.ts";
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
export const runUltracite = async (
  fixture: Awaited<ReturnType<typeof createCommandFixture>>,
  filenames: string | readonly string[],
  signal: AbortSignal,
  action: "check" | "fix" = "check",
  configuration?: string,
  extraArguments: readonly string[] = []
) =>
  readUltraciteResult(
    await fixture.run(
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
        env: {
          ...process.env,
          PATH: `${path.join(repositoryRoot, "node_modules/.bin")}${path.delimiter}${process.env.PATH}`
        },
        signal
      }
    )
  );

export const readUltraciteResult = (
  result: Pick<
    SpawnSyncReturns<Buffer>,
    "error" | "status" | "stderr" | "stdout"
  >
): {
  diagnostics: {
    code?: string;
    filename?: string;
    message: string;
    severity: string;
  }[];
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
        severity: stringValue(diagnostic.severity),
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
