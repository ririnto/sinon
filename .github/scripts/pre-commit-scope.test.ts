import { expect, test } from "vitest";

import { selectChecks } from "./pre-commit-scope";

const alwaysRunChecks = [
  "check:markdownlint-cli2",
  "check:typescript",
  "check:ultracite",
  "check:versions"
];

test("paths outside every suite keep only the always-run checks", () => {
  expect(
    selectChecks(["README.md", "docs/verification.md", "LICENSE"])
  ).toEqual(alwaysRunChecks);
});

test("an empty staged set keeps the always-run checks", () => {
  expect(selectChecks([])).toEqual(alwaysRunChecks);
});

test("harness and root configuration paths add the lint-rules suite", () => {
  for (const stagedPath of [
    "plugins/harness/tooling/pnpm/config.test.ts",
    "rules/markdownlint-cli2.mjs",
    "package.json",
    "pnpm-lock.yaml",
    "pnpm-workspace.yaml",
    "vitest.config.ts",
    "tsconfig.json",
    "oxlint.config.ts",
    ".markdownlint-cli2.jsonc"
  ]) {
    expect(selectChecks([stagedPath])).toContain("check:lint-rules");
  }
});

test("each plugin suite is added only by its own plugin paths", () => {
  expect(selectChecks(["plugins/java/skills/junit/SKILL.md"])).toEqual([
    ...alwaysRunChecks,
    "check:java"
  ]);
  expect(selectChecks(["plugins/spec-driven-development/README.md"])).toEqual([
    ...alwaysRunChecks,
    "check:sdd"
  ]);
  expect(selectChecks(["plugins/observability-assets/README.md"])).toEqual([
    ...alwaysRunChecks,
    "check:promtool"
  ]);
});

test("hook script paths add the hook-scope suite", () => {
  expect(selectChecks([".github/scripts/pre-commit-scope.ts"])).toEqual([
    ...alwaysRunChecks,
    "check:hook-scope"
  ]);
  expect(selectChecks([".husky/pre-commit"])).toEqual([
    ...alwaysRunChecks,
    "check:hook-scope"
  ]);
});

test("a mixed staging selects every touched suite without duplicates", () => {
  const selected = selectChecks([
    "package.json",
    "plugins/harness/README.md",
    "plugins/java/README.md",
    "docs/verification.md"
  ]);
  expect(selected).toEqual([
    ...alwaysRunChecks,
    "check:lint-rules",
    "check:java"
  ]);
});
