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

test("paths outside maintained check scopes select no checks", () => {
  expect(selectChecks(["LICENSE", "unknown.data"])).toEqual([]);
  expect(selectChecks(["README.md"])).toEqual(["check:markdownlint-cli2"]);
});

test("an empty staged set selects no checks", () => {
  expect(selectChecks([])).toEqual([]);
});

test("source and configuration changes select affected checks and tests", () => {
  expect(selectChecks([".github/scripts/pre-commit-scope.ts"])).toEqual([
    "check:typescript",
    "check:ultracite",
    "check:formal-layout",
    "check:test:unit --exclude=plugins/harness/** .github/scripts/pre-commit-scope.test.ts"
  ]);
  expect(selectChecks(["plugins/java/tests/fixture.yaml"])).toEqual([
    "check:yaml-sequences",
    "check:test:unit --exclude=plugins/harness/** plugins/java/tests"
  ]);
  expect(selectChecks(["pnpm-lock.yaml"])).toEqual(["check:*"]);
  expect(selectChecks([".github/scripts/sync-versions.test.ts"])).toContain(
    "check:test:unit --exclude=plugins/harness/** .github/scripts/sync-versions.test.ts"
  );
  expect(
    selectChecks(["plugins/harness/tooling/pnpm/rules/no-sensitive-content.ts"])
  ).toEqual([
    "check:typescript",
    "check:ultracite",
    "check:formal-layout",
    "check:sensitive-content",
    "check:test:unit plugins/harness"
  ]);
  expect(selectChecks(["rules/no-em-dash.ts"])).toContain(
    "check:markdownlint-cli2"
  );
  expect(
    selectChecks(["plugins/harness/tooling/pnpm/runtime.fragment.json"])
  ).toEqual([
    "check:versions",
    "check:test:unit --exclude=plugins/harness/** .github/scripts/sync-versions.test.ts",
    "check:test:unit plugins/harness"
  ]);
  expect(selectChecks(["plugins/harness/package.json"])).toEqual(["check:*"]);
  expect(
    selectChecks([
      "plugins/harness/tooling/pnpm/rules/no-relative-skill-links.ts"
    ])
  ).toContain("check:markdownlint-cli2");
  expect(selectChecks([".github/scripts/sync-sensitive-content.ts"])).toEqual([
    "check:typescript",
    "check:ultracite",
    "check:formal-layout",
    "check:sensitive-content",
    "check:test:unit plugins/harness/tooling/pnpm/rules/markdown-rules.test.ts"
  ]);
  expect(selectChecks(["vitest.config.ts"])).toContain("check:test");
  expect(selectChecks([".gitignore"])).toContain("check:markdownlint-cli2");
});

test("each plugin selects its own tests and affected checks", () => {
  expect(selectChecks(["plugins/java/skills/junit/SKILL.md"])).toEqual([
    "check:markdownlint-cli2",
    "check:test:unit --exclude=plugins/harness/** plugins/java/tests"
  ]);
  expect(selectChecks(["plugins/spec-driven-development/README.md"])).toEqual([
    "check:markdownlint-cli2",
    "check:test:unit --exclude=plugins/harness/** plugins/spec-driven-development/tests"
  ]);
  expect(selectChecks(["plugins/observability-assets/README.md"])).toEqual([
    "check:markdownlint-cli2",
    "check:test:unit --exclude=plugins/harness/** plugins/observability-assets/tests/promtool/release.test.ts"
  ]);
});

test("formal model changes select the matching model and layout checks", () => {
  expect(
    selectChecks([
      ".claude/skills/formal-verification/assets/child-lifecycle/Example.tla"
    ])
  ).toEqual([
    "check:promtool:child",
    "check:formal-layout",
    "check:test:unit --exclude=plugins/harness/** rules/formal-layout.test.ts"
  ]);
  expect(
    selectChecks([
      ".claude/skills/formal-verification/assets/download-lifecycle/DownloadLifecycle.cfg"
    ])
  ).toEqual([
    "check:promtool:download",
    "check:formal-layout",
    "check:test:unit --exclude=plugins/harness/** rules/formal-layout.test.ts"
  ]);
  expect(
    selectChecks([
      ".claude/skills/formal-verification/references/child-lifecycle/ChildLifecycle.mmd"
    ])
  ).toEqual([
    "check:formal-layout",
    "check:statecharts",
    "check:test:unit --exclude=plugins/harness/** rules/formal-layout.test.ts"
  ]);
  expect(
    selectChecks([
      ".claude/skills/formal-verification/references/child-lifecycle/ChildLifecycle.mmd"
    ])
  ).toContain(
    "check:test:unit --exclude=plugins/harness/** rules/formal-layout.test.ts"
  );
});

test("hook changes select the hook scope tests", () => {
  expect(selectChecks([".husky/pre-commit"])).toEqual([
    "check:formal-layout",
    "check:test:unit --exclude=plugins/harness/** .github/scripts/pre-commit-scope.test.ts"
  ]);
});

test("mixed staged paths select affected checks and deduplicate suites", () => {
  const selected = selectChecks([
    "plugins/harness/tooling/pnpm/config.test.ts",
    "plugins/java/README.md",
    "docs/verification.md"
  ]);
  expect(selected).toEqual([
    "check:markdownlint-cli2",
    "check:typescript",
    "check:ultracite",
    "check:formal-layout",
    "check:test:unit --exclude=plugins/harness/** plugins/java/tests",
    "check:test:unit plugins/harness"
  ]);
});

test.each([
  { stagedPath: "plugins/java/README-\u00E9.md", status: 0 },
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
      '["--exclude=plugins/harness/**","plugins/java/tests"]\n'
    );
  }
);
