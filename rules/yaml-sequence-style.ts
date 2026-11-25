import { execFileSync } from "node:child_process";
import { lstatSync, readFileSync } from "node:fs";

import { LineCounter, parseAllDocuments, visit } from "yaml";

const files = execFileSync(
  "git",
  [
    "ls-files",
    "-z",
    "--",
    "*.yaml",
    "*.yml",
    ":(exclude,glob)**/pnpm-lock.yaml"
  ],
  { encoding: "utf-8" }
)
  .split("\0")
  .filter(Boolean);

for (const file of files) {
  let source: string;
  try {
    source = readFileSync(file, "utf-8");
  } catch (error) {
    if (
      error instanceof Error &&
      "code" in error &&
      error.code === "ENOENT" &&
      lstatSync(file, { throwIfNoEntry: false }) === undefined
    ) {
      continue;
    }
    throw error;
  }
  const lineCounter = new LineCounter();
  for (const document of parseAllDocuments(source, {
    lineCounter,
    prettyErrors: false
  })) {
    for (const error of document.errors) {
      const { col, line } = lineCounter.linePos(error.pos[0]);
      process.stderr.write(`${file}:${line}:${col} ${error.message}\n`);
      process.exitCode = 1;
    }
    visit(document, {
      Seq: (_key, sequence) => {
        const offset = sequence.range?.[0];
        if (
          sequence.flow === true &&
          sequence.items.length > 0 &&
          offset !== undefined
        ) {
          const { col, line } = lineCounter.linePos(offset);
          process.stderr.write(
            `${file}:${line}:${col} Use a block YAML sequence with one item per line.\n`
          );
          process.exitCode = 1;
        }
      }
    });
  }
}
