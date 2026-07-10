// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";
import path from "node:path";

import {
  checkInstallerRuntime,
  targetGitEnvironment
} from "./installer-runtime.js";

test("installer outcomes and ownership survive adversarial refreshes", () => {
  checkInstallerRuntime(path.resolve(import.meta.dirname, "..", ".."));
}, 15_000);

test("installer child environment removes inherited Git hook state", () => {
  const environment = targetGitEnvironment({
    GIT_DIR: "/caller/.git",
    GIT_INDEX_FILE: "/caller/.git/index",
    GIT_WORK_TREE: "/caller",
    PATH: "/usr/bin"
  });
  expect(environment).toEqual({ PATH: "/usr/bin" });
});
