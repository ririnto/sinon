// -*- coding: utf-8 -*-

import { existsSync } from "node:fs";
import path from "node:path";

/** Resolve one directory's instruction file using official Codex precedence. */
export const resolveDirectoryInstruction = (
  directory: string,
  fallbackNames: readonly string[] = []
): string | undefined => {
  for (const name of ["AGENTS.override.md", "AGENTS.md", ...fallbackNames]) {
    const candidate = path.join(directory, name);
    if (existsSync(candidate)) {
      return candidate;
    }
  }
  return undefined;
};

/** Resolve the root-to-leaf Codex project instruction chain for one directory. */
export const resolveInstructionChain = (
  root: string,
  targetDirectory: string,
  fallbackNames: readonly string[] = []
): readonly string[] => {
  const relative = path.relative(root, targetDirectory);
  if (relative.startsWith("..") || path.isAbsolute(relative)) {
    throw new Error(`${targetDirectory}: target must stay inside ${root}`);
  }
  const directories: string[] = [];
  let current = targetDirectory;
  while (true) {
    directories.push(current);
    if (current === root) {
      break;
    }
    current = path.dirname(current);
  }
  return directories.toReversed().flatMap((directory) => {
    const instruction = resolveDirectoryInstruction(directory, fallbackNames);
    return instruction === undefined ? [] : [instruction];
  });
};
