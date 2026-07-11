// -*- coding: utf-8 -*-

import { spawnSync } from "node:child_process";

export const checkNativeTools = (): void => {
  for (const tool of [
    "bun",
    "uv",
    "shellcheck",
    "shfmt",
    "markdownlint-cli2"
  ]) {
    const result = spawnSync(tool, ["--version"], { encoding: "utf-8" });
    if (result.error === undefined) {
      console.error(`[tool] ${tool} OK`);
    } else {
      console.error(`[warning] ${tool} not found; smoke test skipped`);
    }
  }
};
