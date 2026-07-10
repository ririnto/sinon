// -*- coding: utf-8 -*-

import { test } from "bun:test";
import path from "node:path";

import { checkInstallerRuntime } from "./installer-runtime.js";

test("installer outcomes and ownership survive adversarial refreshes", () => {
  checkInstallerRuntime(path.resolve(import.meta.dirname, "..", ".."));
}, 15_000);
