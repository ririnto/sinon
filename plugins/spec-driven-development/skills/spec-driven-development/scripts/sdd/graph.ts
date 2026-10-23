import path from "node:path";

import { sort } from "fast-sort";

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
  const usedIds = new Set<string>();
  for (const filePath of specFiles) {
    const rel = path.relative(specRoot, filePath).split(path.sep).join("/");
    const baseId = rel.replaceAll(/[^A-Za-z0-9_]/gu, "_");
    let id = baseId;
    let suffix = 2;
    while (usedIds.has(id)) {
      id = `${baseId}_${suffix}`;
      suffix += 1;
    }
    usedIds.add(id);
    const result = loadFrontmatterEntry(filePath);
    const entry = result.kind === "entry" ? result.entry : undefined;
    if (entry) {
      entries.set(filePath, entry);
    }
    const title = String(
      entry?.data.title ?? path.basename(path.dirname(filePath))
    );
    ids.set(filePath, id);
    lines.push(`  ${id}["${title.replaceAll('"', "'")}"]`);
  }
  const edges = new Set<string>();
  for (const filePath of specFiles) {
    const from = ids.get(filePath);
    const entry = entries.get(filePath);
    if (from !== undefined && from.length > 0 && entry) {
      for (const target of extractLinkTargets(entry.data, filePath)) {
        const to = ids.get(target.resolved);
        if (to !== undefined && to.length > 0) {
          edges.add(`  ${from} --> ${to}`);
        }
      }
    }
  }
  lines.push(...sort([...edges]).asc());
  return lines.join("\n");
};
