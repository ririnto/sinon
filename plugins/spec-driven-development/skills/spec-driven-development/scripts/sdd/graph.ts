import path from "node:path";

import { loadFrontmatterEntry } from "./frontmatter.js";
import { listByBasename } from "./infrastructure.js";
import { extractLinkTargets } from "./links.js";

export const generateMermaid = (specRoot: string): string => {
  const specFiles = listByBasename(specRoot, "SPEC.md");
  const lines = ["flowchart TD"];
  const ids = new Map<string, string>();
  for (const filePath of specFiles) {
    const rel = path.relative(specRoot, filePath).split(path.sep).join("/");
    const id = rel.replaceAll(/[^A-Za-z0-9_]/gu, "_");
    const entry = loadFrontmatterEntry(filePath);
    const title =
      entry !== undefined && typeof entry !== "string"
        ? String(entry.data["title"] ?? path.basename(path.dirname(filePath)))
        : path.basename(path.dirname(filePath));
    ids.set(filePath, id);
    lines.push(`  ${id}["${title.replaceAll('"', "'")}"]`);
  }
  const edges = new Set<string>();
  for (const filePath of specFiles) {
    const from = ids.get(filePath);
    const entry = loadFrontmatterEntry(filePath);
    if (!from || entry === undefined || typeof entry === "string") {
      continue;
    }
    for (const target of extractLinkTargets(entry.data, filePath)) {
      const to = ids.get(target.resolved);
      if (to) {
        edges.add(`  ${from} --> ${to}`);
      }
    }
  }
  lines.push(...[...edges].toSorted());
  return lines.join("\n");
};
