import { existsSync, statSync } from "node:fs";
import path from "node:path";

import { commandSpecPath } from "../args.js";
import { generateMermaid } from "../graph.js";
import { fail } from "../infrastructure.js";
import type { ParsedArgs } from "../shared.js";

export const cmdGenerateDiagram = (args: ParsedArgs): number => {
  const specRoot = commandSpecPath(args, 0, "spec_root");
  if (!specRoot) {
    return 1;
  }
  if (!existsSync(specRoot) || !statSync(specRoot).isDirectory()) {
    fail(`FAIL: Directory not found: ${specRoot}`);
    return 1;
  }
  console.log(generateMermaid(path.resolve(specRoot)));
  return 0;
};
