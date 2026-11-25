import { execFileSync, spawnSync } from "node:child_process";
import { cp, mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { describe, expect, test } from "vitest";

import { configureBaseline } from "../scripts/configure.js";
import { runFixtureCommand } from "./fixture-command.ts";
import { arrayValue, stringValue } from "./structured-value.ts";

const repositoryRoot = path.resolve(import.meta.dirname, "../../..");
const trackedScripts = [
  "-leading.sh",
  "line\nbreak.sh",
  "script with spaces.sh",
  "star*.sh"
];
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
    executables: ["go"],
    nativeCalls: [
      [
        "go",
        "tool",
        "-modfile=go.mod",
        "mvdan.cc/sh/v3/cmd/shfmt",
        "-d",
        "--",
        ...trackedScripts
      ]
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
  return {
    ...Object.fromEntries(
      Object.entries(process.env).filter(([name]) => !localNames.has(name))
    ),
    PATH: `${path.join(repositoryRoot, "node_modules/.bin")}${path.delimiter}${process.env.PATH}`
  };
};

const gitIdentity = [
  "-c",
  "user.name=Native Fixture",
  "-c",
  "user.email=fixture@example.invalid"
];

const git = (
  options: { cwd: string; env: NodeJS.ProcessEnv },
  ...args: string[]
) => spawnSync("git", [...gitIdentity, ...args], options);

const profileTest = test
  .extend("environment", { scope: "file" }, fixtureEnvironment)
  .extend(
    "profileRoot",
    { scope: "file" },
    async ({ environment }, { onCleanup }) => {
      const root = await mkdtemp(path.join(tmpdir(), "sinon-native-profiles-"));
      onCleanup(async () => {
        await rm(root, { force: true, recursive: true });
      });
      await Promise.all(
        nativeProfiles.map(async ({ profile, executables }) => {
          const directory = path.join(root, profile);
          await mkdir(directory);
          const options = { cwd: directory, env: environment };
          expect(git(options, "init", "--initial-branch=main").status).toBe(0);
          await writeFile(path.join(directory, "README.md"), "# Target\n");
          if (profile === "shell") {
            await Promise.all(
              trackedScripts.map(async (name) => {
                await writeFile(
                  path.join(directory, name),
                  "#!/usr/bin/env sh\n"
                );
              })
            );
          }
          expect(git(options, "add", ".").status).toBe(0);
          expect(git(options, "commit", "-m", "Initial fixture").status).toBe(
            0
          );
          expect(git(options, "init", "--bare", "remote.git").status).toBe(0);
          expect(
            git(options, "remote", "add", "origin", "remote.git").status
          ).toBe(0);
          await configureBaseline(directory, profile, "eligible");
          await mkdir(path.join(directory, ".bin"));
          await Promise.all(
            executables.map(async (name) => {
              await writeFile(
                path.join(directory, ".bin", name),
                `#!/usr/bin/env node\nimport { appendFileSync } from "node:fs";\nappendFileSync(process.env.HARNESS_FIXTURE_TRACE, JSON.stringify(["${name}", ...process.argv.slice(2)]) + "\\n");\nif (process.env.HARNESS_FIXTURE_FAIL === "${name}") process.exit(7);\n`,
                { mode: 0o755 }
              );
            })
          );
          expect(spawnSync("pnpm", ["run", "prepare"], options).status).toBe(0);
        })
      );
      return root;
    }
  );

describe("remaining native profile Git gates", () => {
  profileTest.concurrent.for(
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
    { timeout: 30_000 },
    async (
      { profile, entrypoint, hook, state, executables, nativeCalls, testCalls },
      { environment, expect: check, onTestFinished, profileRoot, signal }
    ) => {
      const directory = await mkdtemp(
        path.join(tmpdir(), "sinon-native-gate-")
      );
      onTestFinished(async () => {
        await rm(directory, { force: true, recursive: true });
      });
      await cp(path.join(profileRoot, profile), directory, { recursive: true });
      const trace = path.join(directory, "native-calls.txt");
      const options = {
        cwd: directory,
        env: {
          ...environment,
          HARNESS_FIXTURE_FAIL: state === "native" ? executables[0] : "",
          HARNESS_FIXTURE_TRACE: trace,
          PATH: `${path.join(directory, ".bin")}${path.delimiter}${environment.PATH}`
        }
      };
      if (profile === "shell") {
        await writeFile(
          path.join(directory, "untracked.sh"),
          "untracked scripts are not formatter inputs\n"
        );
      }
      await writeFile(
        path.join(directory, "README.md"),
        state === "markdown" ? "# First\n\n# Second\n" : "# Changed\n"
      );
      check(git(options, "add", "README.md").status).toBe(0);
      const gitArguments =
        hook === "pre-commit"
          ? ["commit", "-m", "Changed fixture"]
          : ["push", "origin", "main"];
      const result = await runFixtureCommand(
        entrypoint === "pnpm" ? "pnpm" : "git",
        entrypoint === "pnpm"
          ? ["run", hook]
          : [...gitIdentity, ...gitArguments],
        { ...options, signal }
      );
      check(
        result.status,
        result.stdout.toString() + result.stderr.toString()
      ).toBe(state === "valid" ? 0 : 1);
      if (state === "markdown") {
        await check(readFile(trace)).rejects.toMatchObject({ code: "ENOENT" });
      } else {
        const recordedCalls = await readFile(trace, "utf-8");
        const calls = recordedCalls
          .trim()
          .split("\n")
          .map((line) => arrayValue(JSON.parse(line)).map(stringValue));
        const nativeLines = nativeCalls.map((call) => JSON.stringify(call));
        if (state === "native") {
          check(calls.length).toBeGreaterThan(0);
          for (const call of calls) {
            check(nativeLines).toContain(JSON.stringify(call));
          }
          check(calls.some((call) => call[0] === executables[0])).toBe(true);
        } else {
          check(
            calls
              .slice(0, nativeCalls.length)
              .map((call) => JSON.stringify(call))
              .toSorted()
          ).toEqual(nativeLines.toSorted());
          check(calls.slice(nativeCalls.length)).toEqual(testCalls);
        }
      }
      if (state !== "valid") {
        if (hook === "pre-commit") {
          check(
            git(options, "log", "-1", "--format=%s").stdout.toString().trim()
          ).toBe("Initial fixture");
        } else {
          check(
            git(
              options,
              "--git-dir",
              "remote.git",
              "show-ref",
              "refs/heads/main"
            ).status
          ).toBe(1);
        }
      }
    }
  );
});
