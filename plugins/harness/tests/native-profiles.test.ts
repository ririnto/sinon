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
    commitCalls: [
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
    executables: ["cargo"],
    profile: "rust",
    pushExtra: [["cargo", "test", "--workspace"]]
  },
  {
    commitCalls: [
      ["uv", "run", "ruff", "check", "."],
      ["uv", "run", "ruff", "format", "--check", "."]
    ],
    executables: ["uv"],
    profile: "uv",
    pushExtra: []
  },
  {
    commitCalls: [
      ["shellcheck", "-S", "warning", "--", ...trackedScripts],
      ["shfmt", "-d", "--", ...trackedScripts]
    ],
    executables: ["shellcheck", "shfmt"],
    profile: "shell",
    pushExtra: []
  }
] as const;

/**
 * Builds the environment for spawned fixture tools.
 * Drops Git local variables and Corepack markers so pnpm honors each fixture's packageManager pin instead of the caller's Corepack dispatch.
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
    Object.entries(process.env).filter(
      ([name]) => !localNames.has(name) && !name.startsWith("COREPACK")
    )
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
      commitCalls,
      pushExtra
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
            `#!/usr/bin/env sh\nset -e\nprintf '%s\\n' '${name}' "$@" >> "$HARNESS_FIXTURE_TRACE"\nif [ "$HARNESS_FIXTURE_FAIL" = '${name}' ]; then exit 7; fi\n`,
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
        const calls =
          state === "native"
            ? [commitCalls[0]]
            : [...commitCalls, ...(hook === "pre-push" ? pushExtra : [])];
        expect(await readFile(trace, "utf-8")).toBe(
          `${calls.flat().join("\n")}\n`
        );
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
