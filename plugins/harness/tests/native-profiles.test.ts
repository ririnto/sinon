import { execFileSync, spawnSync } from "node:child_process";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { describe, expect, onTestFinished, test } from "vitest";

import { configureBaseline } from "../scripts/configure.js";

const repositoryRoot = path.resolve(import.meta.dirname, "../../..");
const trackedScripts = ["-leading.sh", "script with spaces.sh", "star*.sh"];
const nativeProfiles = [
  {
    executables: ["cargo"],
    nativeCalls: [
      ["cargo", "fmt", "--all", "--", "--check"],
      [
        "cargo",
        "clippy",
        "--workspace",
        "--all-targets",
        "--all-features",
        "--",
        "-D",
        "clippy::cognitive_complexity",
        "-D",
        "warnings"
      ]
    ],
    profile: "rust",
    testCalls: [["cargo", "test", "--workspace"]]
  },
  {
    executables: ["uv"],
    nativeCalls: [
      ["uv", "run", "ruff", "check", "."],
      ["uv", "run", "ruff", "format", "--check", "."]
    ],
    profile: "uv",
    testCalls: []
  },
  {
    executables: ["shellcheck", "shfmt"],
    nativeCalls: [
      ["shellcheck", "-S", "warning", "--", ...trackedScripts],
      ["shfmt", "-d", "--", ...trackedScripts]
    ],
    profile: "shell",
    testCalls: []
  }
] as const;

/**
 * Builds the environment for spawned fixture tools.
 * Drops Git local variables so fixture Git commands stay within their own repository.
 */
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

describe("remaining native profile Git gates", () => {
  test.each(
    nativeProfiles.flatMap((profile) =>
      ["pre-commit", "pre-push"].flatMap((hook) =>
        ["valid", "markdown", "native"].flatMap((state) =>
          ["git", "pnpm"].map((entrypoint) => ({
            ...profile,
            entrypoint,
            hook,
            state
          }))
        )
      )
    )
  )(
    "$profile $entrypoint $hook propagates $state outcomes",
    async ({
      profile,
      entrypoint,
      hook,
      state,
      executables,
      nativeCalls,
      testCalls
    }) => {
      const directory = await mkdtemp(
        path.join(tmpdir(), "sinon-native-gate-")
      );
      onTestFinished(() => rm(directory, { force: true, recursive: true }));
      const trace = path.join(directory, "native-calls.txt");
      const options = {
        cwd: directory,
        env: {
          ...fixtureEnvironment(),
          HARNESS_FIXTURE_FAIL: state === "native" ? executables[0] : "",
          HARNESS_FIXTURE_TRACE: trace,
          PATH: `${path.join(directory, ".bin")}${path.delimiter}${path.join(repositoryRoot, "node_modules/.bin")}${path.delimiter}${process.env.PATH}`
        }
      };
      const git = (...args: string[]) =>
        spawnSync(
          "git",
          [
            "-c",
            "user.name=Native Fixture",
            "-c",
            "user.email=fixture@example.invalid",
            ...args
          ],
          options
        );
      expect(git("init", "--initial-branch=main").status).toBe(0);
      await writeFile(path.join(directory, "README.md"), "# Target\n");
      if (profile === "shell") {
        await Promise.all(
          trackedScripts.map((name) =>
            writeFile(path.join(directory, name), "#!/usr/bin/env sh\n")
          )
        );
      }
      expect(git("add", ".").status).toBe(0);
      expect(git("commit", "-m", "Initial fixture").status).toBe(0);
      const remote = path.join(directory, "remote.git");
      expect(git("init", "--bare", remote).status).toBe(0);
      expect(git("remote", "add", "origin", remote).status).toBe(0);
      await configureBaseline(directory, profile, "eligible");
      await mkdir(path.join(directory, ".bin"));
      await Promise.all(
        executables.map((name) =>
          writeFile(
            path.join(directory, ".bin", name),
            `#!/usr/bin/env node\nimport { appendFileSync } from "node:fs";\nappendFileSync(process.env.HARNESS_FIXTURE_TRACE, JSON.stringify(["${name}", ...process.argv.slice(2)]) + "\\n");\nif (process.env.HARNESS_FIXTURE_FAIL === "${name}") process.exit(7);\n`,
            { mode: 0o755 }
          )
        )
      );
      if (profile === "shell") {
        await writeFile(
          path.join(directory, "untracked.sh"),
          "untracked scripts are not native lint inputs\n"
        );
      }
      expect(spawnSync("pnpm", ["run", "prepare"], options).status).toBe(0);
      await writeFile(
        path.join(directory, "README.md"),
        state === "markdown" ? "# First\n\n# Second\n" : "# Changed\n"
      );
      expect(git("add", "README.md").status).toBe(0);
      const gitArguments =
        hook === "pre-commit"
          ? ["commit", "-m", "Changed fixture"]
          : ["push", "origin", "main"];
      const result =
        entrypoint === "pnpm"
          ? spawnSync("pnpm", ["run", hook], options)
          : git(...gitArguments);
      expect(
        result.status,
        result.stdout.toString() + result.stderr.toString()
      ).toBe(state === "valid" ? 0 : 1);
      if (state === "markdown") {
        await expect(readFile(trace)).rejects.toMatchObject({ code: "ENOENT" });
      } else {
        const recordedCalls = await readFile(trace, "utf-8");
        const calls = recordedCalls
          .trim()
          .split("\n")
          .map((line) => JSON.parse(line) as string[]);
        const nativeLines = nativeCalls.map((call) => JSON.stringify(call));
        if (state === "native") {
          expect(calls.length).toBeGreaterThan(0);
          for (const call of calls) {
            expect(nativeLines).toContain(JSON.stringify(call));
          }
          expect(calls.some((call) => call[0] === executables[0])).toBe(true);
        } else {
          expect(
            calls
              .slice(0, nativeCalls.length)
              .map((call) => JSON.stringify(call))
              .toSorted()
          ).toEqual(nativeLines.toSorted());
          expect(calls.slice(nativeCalls.length)).toEqual(testCalls);
        }
      }
      if (state !== "valid") {
        if (hook === "pre-commit") {
          expect(git("log", "-1", "--format=%s").stdout.toString().trim()).toBe(
            "Initial fixture"
          );
        } else {
          expect(
            git("--git-dir", remote, "show-ref", "refs/heads/main").status
          ).toBe(1);
        }
      }
    },
    30_000
  );
});
