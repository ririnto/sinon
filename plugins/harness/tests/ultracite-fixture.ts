import { spawnSync } from "node:child_process";
import { createRequire } from "node:module";
import path from "node:path";

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
  filename: string,
  action: "check" | "fix" = "check",
  configuration?: string,
  extraArguments: readonly string[] = []
): {
  diagnostics: { code?: string; message: string }[];
  status: number | null;
} => {
  const result = spawnSync(
    process.execPath,
    [
      ultraciteBin,
      action,
      "--format",
      "json",
      ...(configuration ? ["--config", configuration] : []),
      ...extraArguments,
      filename
    ],
    {
      cwd: directory,
      env: {
        ...process.env,
        PATH: `${path.join(repositoryRoot, "node_modules/.bin")}${path.delimiter}${process.env.PATH}`
      }
    }
  );
  const output = result.stdout.toString();
  const start = output.search(/\{\s*"diagnostics"\s*:/u);
  if (result.error || start < 0) {
    throw new Error(
      `Ultracite produced no lint result: ${result.error?.message ?? result.stderr.toString()}`
    );
  }
  return {
    diagnostics: JSON.parse(output.slice(start, output.lastIndexOf("}") + 1))
      .diagnostics,
    status: result.status
  };
};
