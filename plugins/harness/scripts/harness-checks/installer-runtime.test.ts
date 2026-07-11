// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";
import path from "node:path";

import {
  hasValidationExecutorDestinations,
  isForbiddenAgentDestination
} from "./installer-runtime-installation.js";
import { targetGitEnvironment } from "./installer-runtime-support.js";
import type { RuntimeRecord } from "./installer-runtime-support.js";
import { checkInstallerRuntime } from "./installer-runtime.js";

test("installer outcomes and ownership survive adversarial refreshes", () => {
  checkInstallerRuntime(path.resolve(import.meta.dirname, "..", ".."));
}, 30_000);

test("installer child environment removes inherited Git hook state", () => {
  const environment = targetGitEnvironment({
    GIT_DIR: "/caller/.git",
    GIT_INDEX_FILE: "/caller/.git/index",
    GIT_WORK_TREE: "/caller",
    PATH: "/usr/bin"
  });
  expect(environment).toEqual({ PATH: "/usr/bin" });
});

test("forbidden installed agents use exact destinations", () => {
  expect(isForbiddenAgentDestination(".claude/agents/explorer.md")).toBe(true);
  expect(isForbiddenAgentDestination("docs/explorer-notes.md")).toBe(false);
});

test("completed installer records require the validation executor pair exactly once", () => {
  const executorPaths = [
    ".claude/agents/validation-executor.md",
    ".codex/agents/validation-executor.toml"
  ] as const;
  const record: RuntimeRecord = {
    assets: executorPaths.map((executorPath) => ({
      kind: "file",
      outcome: "created",
      ownership: "harness",
      path: executorPath
    })),
    complete: true,
    expectedAssets: executorPaths,
    expectedPlanDigest: "test",
    schemaVersion: 2
  };

  expect(hasValidationExecutorDestinations(record)).toBe(true);
  expect(
    hasValidationExecutorDestinations({
      ...record,
      expectedAssets: [...record.expectedAssets, executorPaths[0]]
    })
  ).toBe(false);
});
