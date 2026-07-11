import { readdirSync } from "node:fs";
import path from "node:path";

import { relativePath } from "./agent-routing-contract.js";

type DiscoveredAgents = Readonly<{
  claude: ReadonlySet<string>;
  codex: ReadonlySet<string>;
}>;

const EXCLUDED_DIRECTORIES = new Set([".git", ".worktrees", "node_modules"]);

export const discoverAgents = (
  root: string,
  errors: string[]
): DiscoveredAgents => {
  const claude = new Set<string>();
  const codex = new Set<string>();
  const inspectAgentsDirectory = (directory: string): void => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const filePath = path.join(directory, entry.name);
      if (entry.isDirectory()) {
        errors.push(
          `${relativePath(root, filePath)}: agent directories may not contain nested files or directories`
        );
        for (const nested of readdirSync(filePath, { recursive: true })) {
          if (typeof nested === "string") {
            errors.push(
              `${relativePath(root, path.join(filePath, nested))}: agent directories may not contain nested files or directories`
            );
          }
        }
      } else if (entry.isSymbolicLink()) {
        errors.push(
          `${relativePath(root, filePath)}: agent directories may not contain symlink entries`
        );
      } else if (entry.isFile() && entry.name.endsWith(".md")) {
        claude.add(relativePath(root, filePath));
      } else if (entry.isFile() && entry.name.endsWith(".toml")) {
        codex.add(relativePath(root, filePath));
      } else {
        errors.push(
          `${relativePath(root, filePath)}: agent directories may contain only direct Markdown or TOML agent files`
        );
      }
    }
  };
  const walk = (directory: string): void => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      if (
        EXCLUDED_DIRECTORIES.has(entry.name) ||
        entry.isSymbolicLink() ||
        !entry.isDirectory()
      ) {
        continue;
      }
      const filePath = path.join(directory, entry.name);
      if (entry.name === "agents") {
        inspectAgentsDirectory(filePath);
      } else {
        walk(filePath);
      }
    }
  };
  walk(root);
  return { claude, codex };
};

export const validateInventory = (
  label: string,
  expected: ReadonlySet<string>,
  actual: ReadonlySet<string>,
  errors: string[]
): void => {
  const missing = [...expected].filter((item) => !actual.has(item)).toSorted();
  const unexpected = [...actual]
    .filter((item) => !expected.has(item))
    .toSorted();
  if (missing.length > 0 || unexpected.length > 0) {
    errors.push(
      `${label} inventory drift; missing: ${missing.join(", ") || "none"}; unexpected: ${unexpected.join(", ") || "none"}`
    );
  }
};
