// -*- coding: utf-8 -*-

import { existsSync } from "node:fs";
import path from "node:path";

import {
  fail,
  readRequiredCapture,
  rejectTextFragments,
  requireFile,
  requireText,
  requireTexts
} from "./check-support.js";

export const checkUvAssets = (root: string): void => {
  const assets = path.join(root, "skills", "harness-install", "assets", "uv");
  const checkRunner = path.join(assets, "scripts", "check.py");
  const fixRunner = path.join(assets, "scripts", "fix.py");
  const githubWorkflow = path.join(assets, ".github", "workflows", "ruff.yaml");
  const ruffSpec = readRequiredCapture(
    checkRunner,
    /RUFF_SPEC:\s*Final\s*=\s*"(?<spec>[^"]+)"/u,
    "Ruff check runner constraint"
  );
  const fixRuffSpec = readRequiredCapture(
    fixRunner,
    /RUFF_SPEC:\s*Final\s*=\s*"(?<spec>[^"]+)"/u,
    "Ruff fix runner constraint"
  );
  const setupUvVersion = readRequiredCapture(
    githubWorkflow,
    /astral-sh\/setup-uv@(?<version>v\d+\.\d+\.\d+)/u,
    "setup-uv version"
  );
  if (!/^ruff>=\d+\.\d+\.\d+,<\d+\.\d+\.\d+$/u.test(ruffSpec)) {
    fail(`[assetVersion] invalid Ruff constraint: ${ruffSpec}`);
  }
  if (fixRuffSpec !== ruffSpec) {
    fail("[uv assets] Ruff check and fix constraints must match");
  }
  if (!/^v\d+\.\d+\.\d+$/u.test(setupUvVersion)) {
    fail(`[assetVersion] invalid setup-uv version: ${setupUvVersion}`);
  }
  requireTexts(
    ["README.md", "skills/harness-install/references/rule-interface.md"].map(
      (relativePath) => ({
        fragments: [ruffSpec],
        path: path.join(root, relativePath)
      })
    )
  );
  requireTexts(
    ["scripts/check.ts", "scripts/fix.ts"].map((relativePath) => ({
      fragments: ["readRuffSpec"],
      path: path.join(root, relativePath)
    }))
  );
  requireFile(path.join(assets, "ruff.toml"));
  requireFile(path.join(assets, "pyproject.toml"));
  requireFile(checkRunner);
  requireText(path.join(assets, "pyproject.toml"), "package = false");
  rejectTextFragments(path.join(assets, "pyproject.toml"), ["pre-commit"]);
  if (existsSync(path.join(assets, ".pre-commit-config.yaml"))) {
    fail(
      "[uv assets] .pre-commit-config.yaml must not activate generated hooks"
    );
  }
  if (existsSync(path.join(assets, "uv.toml"))) {
    fail("[uv assets] uv.toml must not exist; use pyproject.toml instead");
  }
  requireTexts([
    {
      fragments: [
        "run: uv run scripts/check.py",
        "actions/checkout@v7",
        `astral-sh/setup-uv@${setupUvVersion}`
      ],
      path: githubWorkflow
    },
    {
      fragments: ["ruff:", "uv run scripts/check.py"],
      path: path.join(assets, ".gitlab-ci.yml")
    }
  ]);
  console.error("[uv assets] OK");
};
