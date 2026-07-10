// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { resolveInstructionChain } from "./instruction-contract.js";

const ROOT = path.resolve(import.meta.dirname, "..");
const LIMIT = 32 * 1024;
const POINTER = "# CLAUDE.md\n\n@AGENTS.md\n";
const EXCLUDED = new Set([".git", ".worktrees", "node_modules"]);

const collectInstructionFiles = (directory: string): readonly string[] => {
  const files: string[] = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (EXCLUDED.has(entry.name) || entry.isSymbolicLink()) {
      continue;
    }
    const filePath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...collectInstructionFiles(filePath));
    } else if (
      entry.isFile() &&
      (entry.name === "AGENTS.md" || entry.name === "AGENTS.override.md")
    ) {
      files.push(filePath);
    }
  }
  return files;
};

test("every root-to-leaf project instruction chain stays below 32 KiB", () => {
  const reports = collectInstructionFiles(ROOT).map((filePath) => {
    const chain = resolveInstructionChain(ROOT, path.dirname(filePath));
    const bytes = chain.reduce((total, item) => total + statSync(item).size, 0);
    return {
      bytes,
      chain: chain.map((item) => path.relative(ROOT, item))
    };
  });
  for (const report of reports) {
    expect(
      report.bytes,
      `${report.chain.join(" -> ")} is ${report.bytes} bytes`
    ).toBeLessThan(LIMIT);
  }
});

test("instruction precedence replaces same-directory AGENTS with override", async () => {
  const root = await mkdtemp(path.join(tmpdir(), "sinon-instructions-"));
  const child = path.join(root, "plugins", "example");
  try {
    await mkdir(child, { recursive: true });
    await Promise.all([
      writeFile(path.join(root, "AGENTS.md"), "root\n", "utf-8"),
      writeFile(path.join(child, "AGENTS.md"), "ordinary\n", "utf-8"),
      writeFile(path.join(child, "AGENTS.override.md"), "override\n", "utf-8")
    ]);
    expect(
      resolveInstructionChain(root, child).map((filePath) =>
        path.relative(root, filePath)
      )
    ).toEqual(["AGENTS.md", "plugins/example/AGENTS.override.md"]);
  } finally {
    await rm(root, { force: true, recursive: true });
  }
});

test("canonical Claude rule pointers remain exact", () => {
  for (const relative of [
    "CLAUDE.md",
    "plugins/CLAUDE.md",
    "plugins/agent-capability-kit/CLAUDE.md",
    "plugins/harness/CLAUDE.md",
    "plugins/spring/CLAUDE.md",
    "plugins/workspace-workflow/CLAUDE.md"
  ]) {
    expect(readFileSync(path.join(ROOT, relative), "utf-8")).toBe(POINTER);
  }
});
