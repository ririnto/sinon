import { spawnSync } from "node:child_process";
import {
  mkdir,
  mkdtemp,
  readFile,
  rm,
  symlink,
  writeFile
} from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test } from "vitest";

import { selectChecks } from "./pre-commit-scope";

const alwaysRunChecks = [
  "check:markdownlint-cli2",
  "check:typescript",
  "check:ultracite",
  "check:versions"
];

const versionTest = "check:test:unit .github/scripts/sync-versions.test.ts";

test("paths outside every suite keep only the always-run checks", () => {
  expect(
    selectChecks(["README.md", "docs/verification.md", "LICENSE"])
  ).toEqual([...alwaysRunChecks, versionTest]);
});

test("an empty staged set keeps the always-run checks", () => {
  expect(selectChecks([])).toEqual([...alwaysRunChecks, versionTest]);
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
    expect(selectChecks([stagedPath])).toContain(
      `${versionTest} rules plugins/harness/tooling/pnpm plugins/harness/tests`
    );
  }
});

test("each plugin suite is added only by its own plugin paths", () => {
  expect(selectChecks(["plugins/java/skills/junit/SKILL.md"])).toEqual([
    ...alwaysRunChecks,
    `${versionTest} plugins/java/tests`
  ]);
  expect(selectChecks(["plugins/spec-driven-development/README.md"])).toEqual([
    ...alwaysRunChecks,
    `${versionTest} plugins/spec-driven-development/tests`
  ]);
  expect(selectChecks(["plugins/observability-assets/README.md"])).toEqual([
    ...alwaysRunChecks,
    "check:promtool",
    `${versionTest} plugins/observability-assets/tests/promtool/release.test.ts`
  ]);
});

test("hook script paths add the hook-scope suite", () => {
  expect(selectChecks([".github/scripts/pre-commit-scope.ts"])).toEqual([
    ...alwaysRunChecks,
    `${versionTest} .github/scripts/pre-commit-scope.test.ts`
  ]);
  expect(selectChecks([".husky/pre-commit"])).toEqual([
    ...alwaysRunChecks,
    `${versionTest} .github/scripts/pre-commit-scope.test.ts`
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
    `${versionTest} rules plugins/harness/tooling/pnpm plugins/harness/tests plugins/java/tests`
  ]);
});

test.each([0, 7])(
  "the hook runs one filtered test task and respects test exit %s",
  async (status) => {
    const fixture = await mkdtemp(path.join(tmpdir(), "sinon-hook-scope-"));
    onTestFinished(() => rm(fixture, { force: true, recursive: true }));
    await symlink(
      path.resolve(import.meta.dirname, "../../node_modules"),
      path.join(fixture, "node_modules"),
      "dir"
    );
    await mkdir(path.join(fixture, "plugins/java"), { recursive: true });
    await writeFile(
      path.join(fixture, "plugins/java/example.ts"),
      "export {};\n"
    );
    await writeFile(
      path.join(fixture, "package.json"),
      JSON.stringify({
        private: true,
        scripts: {
          "check:markdownlint-cli2": "node --version",
          "check:test:unit": "node record-tests.mjs",
          "check:typescript": "node --version",
          "check:ultracite": "node --version",
          "check:versions": "node --version"
        }
      })
    );
    await writeFile(
      path.join(fixture, "record-tests.mjs"),
      'import { appendFileSync } from "node:fs"; appendFileSync("calls.jsonl", JSON.stringify(process.argv.slice(2)) + "\\n"); process.exitCode = Number(process.env.HARNESS_FIXTURE_EXIT);\n'
    );
    const localVariables = spawnSync("git", ["rev-parse", "--local-env-vars"], {
      encoding: "utf-8"
    });
    expect(localVariables.status).toBe(0);
    const localVariableNames = new Set(
      localVariables.stdout.trim().split("\n")
    );
    const environment = {
      ...Object.fromEntries(
        Object.entries(process.env).filter(
          ([name]) => !localVariableNames.has(name)
        )
      ),
      HARNESS_FIXTURE_EXIT: String(status)
    };
    expect(
      spawnSync("git", ["init", "--quiet"], { cwd: fixture, env: environment })
        .status
    ).toBe(0);
    expect(
      spawnSync("git", ["add", "plugins/java/example.ts"], {
        cwd: fixture,
        env: environment
      }).status
    ).toBe(0);
    const result = spawnSync(
      process.execPath,
      [path.join(import.meta.dirname, "pre-commit-scope.ts")],
      {
        cwd: fixture,
        encoding: "utf-8",
        env: environment
      }
    );
    expect(result.status, result.stdout + result.stderr).toBe(
      status === 0 ? 0 : 1
    );
    expect(await readFile(path.join(fixture, "calls.jsonl"), "utf-8")).toBe(
      '[".github/scripts/sync-versions.test.ts","plugins/java/tests"]\n'
    );
  }
);
