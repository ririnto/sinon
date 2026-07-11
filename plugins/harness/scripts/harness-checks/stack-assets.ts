// -*- coding: utf-8 -*-

import path from "node:path";

import {
  rejectTextFragments,
  requireDir,
  requireFile,
  requireTexts
} from "./check-support.js";

const checkGithubWorkflow = (
  filePath: string,
  command: null | string,
  fragments: readonly string[]
): void => {
  requireFile(filePath);
  if (command !== null) {
    requireTexts([{ fragments: [`run: ${command}`], path: filePath }]);
  }
  requireTexts([{ fragments, path: filePath }]);
};

const checkGitlabCi = (
  filePath: string,
  job: string,
  command: string
): void => {
  requireTexts([{ fragments: [`${job}:`, command], path: filePath }]);
};

export const checkGradleAssets = (root: string): void => {
  const assets = path.join(
    root,
    "skills",
    "harness-install",
    "assets",
    "gradle"
  );
  requireDir(
    path.join(
      assets,
      "buildSrc",
      "src",
      "main",
      "kotlin",
      "com",
      "ririnto",
      "sinon",
      "ktlint"
    )
  );
  requireFile(path.join(assets, "build.gradle.kts"));
  requireTexts([
    {
      fragments: [
        "checkMarkdown",
        'tasks.named("ktlintCheck")',
        "markdownlint-cli2"
      ],
      path: path.join(assets, "build.gradle.kts")
    }
  ]);
  rejectTextFragments(path.join(assets, "settings.gradle.kts"), [
    "createHooks()",
    "pre-commit-git-hooks"
  ]);
  checkGithubWorkflow(
    path.join(assets, ".github", "workflows", "ktlint.yaml"),
    "./gradlew ktlintCheck",
    ["actions/checkout@v7", "gradle/actions/setup-gradle@v6"]
  );
  checkGitlabCi(
    path.join(assets, ".gitlab-ci.yml"),
    "ktlint",
    "./gradlew ktlintCheck"
  );
  console.error("[gradle assets] OK");
};

export const checkBunAssets = (root: string): void => {
  const assets = path.join(root, "skills", "harness-install", "assets", "bun");
  requireFile(path.join(assets, "package.json"));
  rejectTextFragments(path.join(assets, "package.json"), [
    '"prepare"',
    '"husky"'
  ]);
  checkGithubWorkflow(
    path.join(assets, ".github", "workflows", "ultracite.yaml"),
    "bun run check",
    ["actions/checkout@v7", "oven-sh/setup-bun@v2"]
  );
  checkGitlabCi(
    path.join(assets, ".gitlab-ci.yml"),
    "ultracite",
    "bun run check"
  );
  console.error("[bun assets] OK");
};

export const checkShellAssets = (root: string): void => {
  const assets = path.join(
    root,
    "skills",
    "harness-install",
    "assets",
    "shell"
  );
  for (const filePath of [
    ".githooks/pre-commit",
    ".githooks/pre-push",
    "scripts/check.sh",
    "scripts/fix.sh"
  ]) {
    requireFile(path.join(assets, filePath));
  }
  requireTexts([
    {
      fragments: ["shellcheck -S warning", "shfmt -d", "markdownlint-cli2"],
      path: path.join(assets, "scripts", "check.sh")
    },
    {
      fragments: ["shfmt", "markdownlint-cli2", "--fix"],
      path: path.join(assets, "scripts", "fix.sh")
    }
  ]);
  checkGithubWorkflow(
    path.join(assets, ".github", "workflows", "shellcheck.yaml"),
    "sh scripts/check.sh",
    ["actions/checkout@v7", "shellcheck shfmt"]
  );
  checkGitlabCi(
    path.join(assets, ".gitlab-ci.yml"),
    "shellcheck",
    "sh scripts/check.sh"
  );
  console.error("[shell assets] OK");
};
