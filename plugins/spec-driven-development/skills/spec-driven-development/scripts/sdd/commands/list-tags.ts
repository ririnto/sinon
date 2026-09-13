import { existsSync, statSync } from "node:fs";
import path from "node:path";

import {
  commandSpecPath,
  optionBool,
  optionString,
  requireChoice
} from "../args.js";
import { loadFrontmatterEntry, matchesKind } from "../frontmatter.js";
import { collectMarkdownFiles, fail } from "../infrastructure.js";
import { LIST_KINDS } from "../shared.js";
import type { ParsedArgs } from "../shared.js";

/**
 * Runs the document tag list command and returns the process exit code.
 */
export const cmdListTags = (args: ParsedArgs): number => {
  const specPath = commandSpecPath(args, 0, "spec_path");
  if (!specPath) {
    return 1;
  }
  const kind = optionString(args, "kind") ?? "any";
  if (!requireChoice(kind, LIST_KINDS, "kind")) {
    return 1;
  }
  if (!existsSync(specPath) || !statSync(specPath).isDirectory()) {
    fail(`FAIL: Path is not a directory: ${specPath}`);
    return 1;
  }
  const counter = new Map<string, number>();
  for (const filePath of collectMarkdownFiles(path.resolve(specPath)).filter(
    (candidate) => matchesKind(candidate, kind)
  )) {
    const result = loadFrontmatterEntry(filePath);
    if (result.kind === "error") {
      fail(`FAIL [${filePath}]: ${result.message}`);
      return 1;
    }
    if (result.kind === "missing") {
      continue;
    }
    for (const tag of result.entry.tags) {
      counter.set(tag, (counter.get(tag) ?? 0) + 1);
    }
  }
  for (const tag of [...counter.keys()].toSorted()) {
    console.log(
      optionBool(args, "count") ? `${tag}\t${counter.get(tag) ?? 0}` : tag
    );
  }
  return 0;
};
