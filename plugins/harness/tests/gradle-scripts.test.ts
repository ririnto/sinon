import { execFileSync, spawnSync } from "node:child_process";
import { chmod, cp, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { describe, expect, test } from "vitest";

const repositoryRoot = path.resolve(import.meta.dirname, "../../..");
const pnpmProfile = path.join(import.meta.dirname, "../tooling/pnpm");
const gradleProfile = path.join(import.meta.dirname, "../tooling/gradle");

const fixtureEnvironment = (): NodeJS.ProcessEnv => {
  const localNames = new Set(
    execFileSync("git", ["rev-parse", "--local-env-vars"], {
      encoding: "utf-8"
    })
      .trim()
      .split("\n")
  );
  return Object.fromEntries(
    Object.entries(process.env).filter(([name]) => !localNames.has(name))
  );
};

describe("Gradle Node-side script integration", () => {
  test("manifest preserves the requested script and catalog surface", async () => {
    const manifest = JSON.parse(
      await readFile(path.join(gradleProfile, "package.json"), "utf-8")
    );
    expect(manifest.scripts).toEqual({
      check: "run-p check:*",
      "check:markdownlint-cli2": "markdownlint-cli2",
      fix: "run-p fix:*",
      "fix:markdownlint-cli2": "markdownlint-cli2 --fix",
      "gradle:check": "./gradlew check",
      "gradle:ktlintCheck": "./gradlew ktlintCheck",
      "pre-commit": "run-s check gradle:ktlintCheck",
      "pre-push": "run-s check gradle:check",
      prepare: "husky"
    });
    expect(manifest.devDependencies).toEqual({
      husky: "catalog:",
      markdownlint: "catalog:",
      "markdownlint-cli2": "catalog:",
      "npm-run-all2": "catalog:"
    });
    expect(Object.keys(manifest).toSorted()).toEqual([
      "devDependencies",
      "scripts"
    ]);
  });
  test.each([
    { hook: "pre-commit", valid: true },
    { hook: "pre-push", valid: true },
    { hook: "pre-commit", valid: false },
    { hook: "pre-push", valid: false }
  ])(
    "Git $hook respects the Markdown gate (valid=$valid)",
    async ({ hook, valid }) => {
      const directory = await mkdtemp(
        path.join(tmpdir(), "sinon-gradle-scripts-")
      );
      try {
        const { scripts } = JSON.parse(
          await readFile(path.join(gradleProfile, "package.json"), "utf-8")
        );
        await writeFile(
          path.join(directory, "package.json"),
          JSON.stringify({ private: true, scripts })
        );
        await cp(
          path.join(pnpmProfile, ".markdownlint-cli2.jsonc"),
          path.join(directory, ".markdownlint-cli2.jsonc")
        );
        await cp(
          path.join(pnpmProfile, "rules"),
          path.join(directory, "rules"),
          {
            filter: (source) => !source.endsWith(".test.ts"),
            recursive: true
          }
        );
        await writeFile(
          path.join(directory, "README.md"),
          "# Target\n\nReady.\n"
        );
        await writeFile(
          path.join(directory, "gradlew"),
          "#!/usr/bin/env sh\n# -*- coding: utf-8 -*-\nset -e\nprintf '%s\\n' \"$*\" >> gradle-calls.txt\n"
        );
        await chmod(path.join(directory, "gradlew"), 0o755);
        const options = {
          cwd: directory,
          env: {
            ...fixtureEnvironment(),
            PATH: `${path.join(repositoryRoot, "node_modules/.bin")}${path.delimiter}${process.env.PATH}`
          }
        };
        const git = (...args: string[]) =>
          spawnSync(
            "git",
            [
              "-c",
              "user.name=Hook Fixture",
              "-c",
              "user.email=fixture@example.invalid",
              ...args
            ],
            options
          );
        expect(git("init", "--initial-branch=main").status).toBe(0);
        expect(git("add", ".").status).toBe(0);
        expect(git("commit", "-m", "Initial fixture").status).toBe(0);
        const remote = path.join(directory, "remote.git");
        expect(git("init", "--bare", remote).status).toBe(0);
        expect(git("remote", "add", "origin", remote).status).toBe(0);
        await cp(
          path.join(pnpmProfile, ".husky"),
          path.join(directory, ".husky"),
          {
            recursive: true
          }
        );
        expect(spawnSync("pnpm", ["run", "prepare"], options).status).toBe(0);
        expect(
          git("config", "--get", "core.hooksPath").stdout.toString().trim()
        ).toBe(".husky/_");
        await writeFile(
          path.join(directory, "README.md"),
          valid ? "# Target\n\nChanged.\n" : "# First\n\n# Second\n"
        );
        expect(git("add", "README.md").status).toBe(0);
        const result =
          hook === "pre-commit"
            ? git("commit", "-m", "Changed fixture")
            : git("push", "origin", "main");
        expect(result.error).toBeUndefined();
        if (valid) {
          expect(result.status).toBe(0);
          expect(
            await readFile(path.join(directory, "gradle-calls.txt"), "utf-8")
          ).toBe(hook === "pre-commit" ? "ktlintCheck\n" : "check\n");
        } else {
          expect(result.status).toBe(1);
          await expect(
            readFile(path.join(directory, "gradle-calls.txt"))
          ).rejects.toMatchObject({ code: "ENOENT" });
          if (hook === "pre-commit") {
            expect(
              git("log", "-1", "--format=%s").stdout.toString().trim()
            ).toBe("Initial fixture");
          } else {
            expect(
              git("--git-dir", remote, "show-ref", "refs/heads/main").status
            ).toBe(1);
          }
        }
      } finally {
        await rm(directory, { force: true, recursive: true });
      }
    }
  );
});
