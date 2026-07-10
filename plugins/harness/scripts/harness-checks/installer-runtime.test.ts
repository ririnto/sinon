// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";
import path from "node:path";

import { checkInstallerRuntime } from "./installer-runtime.js";

test("installer outcomes and ownership survive adversarial refreshes", () => {
  checkInstallerRuntime(path.resolve(import.meta.dirname, "..", ".."));
}, 15_000);

test("installer runtime ignores Git hook environment", () => {
  const root = path.resolve(import.meta.dirname, "..", "..");
  const gitDir = Bun.spawnSync(["git", "rev-parse", "--git-dir"], {
    cwd: root,
    stderr: "pipe",
    stdout: "pipe"
  });
  const gitIndex = Bun.spawnSync(["git", "rev-parse", "--git-path", "index"], {
    cwd: root,
    stderr: "pipe",
    stdout: "pipe"
  });
  expect(gitDir.success).toBe(true);
  expect(gitIndex.success).toBe(true);
  const original = {
    GIT_DIR: process.env.GIT_DIR,
    GIT_INDEX_FILE: process.env.GIT_INDEX_FILE,
    GIT_WORK_TREE: process.env.GIT_WORK_TREE
  };
  process.env.GIT_DIR = gitDir.stdout.toString().trim();
  process.env.GIT_INDEX_FILE = gitIndex.stdout.toString().trim();
  process.env.GIT_WORK_TREE = root;
  try {
    checkInstallerRuntime(root);
  } finally {
    for (const [key, value] of Object.entries(original)) {
      if (value === undefined) {
        Reflect.deleteProperty(process.env, key);
      } else {
        process.env[key] = value;
      }
    }
  }
}, 15_000);
