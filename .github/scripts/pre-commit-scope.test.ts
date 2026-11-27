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

import { selectChecks } from "./pre-commit-scope.ts";

const alwaysRunChecks = [
  "check:markdownlint-cli2",
  "check:typescript",
  "check:ultracite",
  "check:versions",
  "check:yaml-sequences"
];

const versionTest =
  "check:test:unit --exclude=plugins/harness/** .github/scripts/sync-versions.test.ts";

test("paths outside every suite keep only the always-run checks", () => {
  expect(
    selectChecks(["README.md", "docs/verification.md", "LICENSE"])
  ).toEqual([...alwaysRunChecks, versionTest]);
});

test("an empty staged set keeps the always-run checks", () => {
  expect(selectChecks([])).toEqual([...alwaysRunChecks, versionTest]);
});

test("harness and root configuration paths add the root rules suite", () => {
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
    expect(selectChecks([stagedPath])).toEqual([
      ...alwaysRunChecks,
      `${versionTest} rules`
    ]);
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

test("formal verification model and config changes select Promtool", () => {
  for (const [directory, model, extension] of [
    ["child-lifecycle", "ChildLifecycle", "tla"],
    ["download-lifecycle", "DownloadLifecycle", "cfg"]
  ]) {
    const stagedPath = `.claude/skills/formal-verification/assets/${directory}/${model}.${extension}`;
    expect(selectChecks([stagedPath])).toEqual([
      ...alwaysRunChecks,
      "check:promtool",
      versionTest
    ]);
  }
  expect(
    selectChecks([
      ".claude/skills/formal-verification/references/child-lifecycle/README.md"
    ])
  ).toEqual([...alwaysRunChecks, versionTest]);
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
    `${versionTest} rules plugins/java/tests`
  ]);
});

test.each([
  { stagedPath: "plugins/java/README.md", status: 0 },
  { stagedPath: "plugins/java/skills/junit/SKILL.md", status: 7 }
])(
  "the selector runs one filtered test task for $stagedPath and respects test exit $status",
  async ({ stagedPath, status }) => {
    const fixture = await mkdtemp(path.join(tmpdir(), "sinon-hook-scope-"));
    onTestFinished(async () => {
      await rm(fixture, { force: true, recursive: true });
    });
    await symlink(
      path.resolve(import.meta.dirname, "../../node_modules"),
      path.join(fixture, "node_modules"),
      "dir"
    );
    const manifest = path.join(fixture, stagedPath);
    await mkdir(path.dirname(manifest), { recursive: true });
    await writeFile(manifest, "{}\n");
    await writeFile(
      path.join(fixture, "package.json"),
      JSON.stringify({
        private: true,
        scripts: {
          "check:markdownlint-cli2": "node --version",
          "check:test:unit": "node record-tests.mjs",
          "check:typescript": "node --version",
          "check:ultracite": "node --version",
          "check:versions": "node --version",
          "check:yaml-sequences": "node --version"
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
      spawnSync("git", ["add", stagedPath], {
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
      '["--exclude=plugins/harness/**",".github/scripts/sync-versions.test.ts","plugins/java/tests"]\n'
    );
  }
);
