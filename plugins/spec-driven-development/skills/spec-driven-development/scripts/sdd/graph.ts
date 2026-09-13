import path from "node:path";

import { loadFrontmatterEntry } from "./frontmatter.js";
import { listByBasename } from "./infrastructure.js";
import { extractLinkTargets } from "./links.js";
import type { LoadEntry } from "./shared.js";

/**
 * Generates a Mermaid flowchart from the SPEC documents under a spec root;
 * nodes come from titles and edges from call links between specs.
 */
export const generateMermaid = (specRoot: string): string => {
  const specFiles = listByBasename(specRoot, "SPEC.md");
  const lines = ["flowchart TD"];
  const ids = new Map<string, string>();
  const entries = new Map<string, LoadEntry>();
  for (const filePath of specFiles) {
    const rel = path.relative(specRoot, filePath).split(path.sep).join("/");
    const id = rel.replaceAll(/[^A-Za-z0-9_]/gu, "_");
    const result = loadFrontmatterEntry(filePath);
    const entry = result.kind === "entry" ? result.entry : undefined;
    if (entry) {
      entries.set(filePath, entry);
    }
    const title = String(
      entry?.data["title"] ?? path.basename(path.dirname(filePath))
    );
    ids.set(filePath, id);
    lines.push(`  ${id}["${title.replaceAll('"', "'")}"]`);
  }
  const edges = new Set<string>();
  for (const filePath of specFiles) {
    const from = ids.get(filePath);
    const entry = entries.get(filePath);
    if (!from || !entry) {
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
