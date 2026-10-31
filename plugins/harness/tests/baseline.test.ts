import { execFileSync, spawnSync } from "node:child_process";
import {
  mkdir,
  mkdtemp,
  readFile,
  readdir,
  rm,
  symlink,
  writeFile
} from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { lint } from "markdownlint/sync";
import { describe, expect, onTestFinished, test } from "vitest";
import { parseDocument } from "yaml";

import { configureBaseline } from "../scripts/configure.js";
import type { BaselineProfile } from "../scripts/configure.js";

const repositoryRoot = path.resolve(import.meta.dirname, "../../..");
const profiles: BaselineProfile[] = [
  "gradle",
  "kotlin",
  "maven",
  "go",
  "rust",
  "uv",
  "shell",
  "node",
  "frontend",
  "react"
];

const targetDirectory = async (): Promise<string> => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-baseline-"));
  onTestFinished(() => rm(directory, { force: true, recursive: true }));
  return directory;
};

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

const assertSortedKeys = (value: unknown): void => {
  if (Array.isArray(value)) {
    for (const item of value) {
      assertSortedKeys(item);
    }
  } else if (value !== null && typeof value === "object") {
    expect(Object.keys(value)).toEqual(Object.keys(value).toSorted());
    for (const item of Object.values(value)) {
      assertSortedKeys(item);
    }
  }
};

describe("fresh-root baseline", () => {
  test.each([
    ["GitLab", "plugins/harness/ci/gitlab/pnpm.gitlab-ci.yaml", 0],
    ["GitLab", "plugins/harness/ci/gitlab/pnpm.gitlab-ci.yaml", 7],
    ["GitHub", "plugins/harness/ci/github/pnpm.yaml", 0],
    ["GitHub", "plugins/harness/ci/github/pnpm.yaml", 7]
  ])(
    "%s pnpm installation from %s forwards the selected version and npm exit %s",
    async (platform, file, status) => {
      const directory = await targetDirectory();
      const packageManager = "pnpm@1.2.3";
      await mkdir(path.join(directory, ".bin"));
      await writeFile(
        path.join(directory, ".bin/npm"),
        '#!/usr/bin/env sh\nfor arg do echo "$arg"; done\nexit "$INSTALL_STATUS"\n',
        { mode: 0o755 }
      );
      await writeFile(
        path.join(directory, "package.json"),
        JSON.stringify({ packageManager })
      );
      const ci = parseDocument(
        await readFile(path.join(repositoryRoot, String(file)), "utf-8")
      ).toJS();
      const command =
        platform === "GitLab"
          ? ci.pnpm.before_script[0]
          : ci.jobs.pnpm.steps.find((step: { run?: string }) =>
              step.run?.startsWith("npm install --global ")
            ).run;
      const result = spawnSync("sh", ["-c", command], {
        cwd: directory,
        encoding: "utf-8",
        env: {
          ...process.env,
          INSTALL_STATUS: String(status),
          PATH: `${path.join(directory, ".bin")}${path.delimiter}${process.env.PATH}`
        }
      });
      expect(result.error).toBeUndefined();
      expect(result.status, result.stderr).toBe(status);
      expect(result.stdout).toBe(`install\n--global\n${packageManager}\n`);
    }
  );

  test.each(profiles)(
    "%s materializes the shared gate and retains it on repetition",
    async (profile) => {
      const directory = await targetDirectory();
      const first = await configureBaseline(directory, profile, "eligible");
      expect(first.status).toBe("configured");
      const manifest = JSON.parse(
        await readFile(path.join(directory, "package.json"), "utf-8")
      );
      expect(manifest.private).toBe(true);
      expect(manifest.devDependencies["markdownlint-rule-search-replace"]).toBe(
        "catalog:"
      );
      expect(manifest.packageManager).toBeUndefined();
      const rootManifest = JSON.parse(
        await readFile(path.join(repositoryRoot, "package.json"), "utf-8")
      );
      expect(manifest.engines.node).toBe(rootManifest.engines.node);
      expect(manifest.scripts).toMatchObject({
        check: "run-p check:*",
        "check:markdownlint-cli2": "markdownlint-cli2",
        fix: "run-p fix:*",
        "fix:markdownlint-cli2": "markdownlint-cli2 --fix",
        "pre-commit": expect.stringMatching(/^run-s check(?: |$)/u),
        "pre-push": expect.stringMatching(/^run-s check(?: |$)/u),
        prepare: "husky"
      });
      const workspace = parseDocument(
        await readFile(path.join(directory, "pnpm-workspace.yaml"), "utf-8"),
        { uniqueKeys: true }
      );
      expect(workspace.errors).toEqual([]);
      const { catalog } = workspace.toJS();
      expect(workspace.toJS().allowBuilds).toEqual(
        profile === "node" || profile === "frontend" || profile === "react"
          ? { esbuild: true }
          : undefined
      );
      for (const [name, version] of Object.entries({
        ...manifest.dependencies,
        ...manifest.devDependencies
      })) {
        expect(version).toBe("catalog:");
        expect(catalog[name]).toBeTypeOf("string");
      }
      const configuration = JSON.parse(
        await readFile(
          path.join(directory, ".markdownlint-cli2.jsonc"),
          "utf-8"
        )
      );
      assertSortedKeys(configuration);
      expect(configuration.gitignore).toBe(true);
      expect(configuration.config["table-column-style"]).toEqual({
        style: "compact"
      });
      for (const [text, expected] of [
        ["# Sample\n\n| A | Longer |\n| --- | --- |\n| 1 | 2 |\n", []],
        [
          "# Sample\n\n| A   | Longer |\n| --- | ------ |\n| 1   | 2      |\n",
          ["MD060", "MD060", "MD060"]
        ]
      ] as const) {
        expect(
          lint({
            config: configuration.config,
            strings: { sample: text }
          }).sample.map(({ ruleNames }) => ruleNames[0])
        ).toEqual(expected);
      }
      const rules = await readdir(path.join(directory, "rules"));
      expect(rules.toSorted()).toEqual([
        "no-sensitive-content.mjs",
        "sentence-per-line.ts",
        "table-separators.ts"
      ]);
      expect(configuration.config["sensitive-content"]).toBeUndefined();
      expect(configuration.customRules).not.toContain(
        "rules/no-sensitive-content.mjs"
      );
      expect(
        await readFile(path.join(directory, ".husky/.gitignore"), "utf-8")
      ).toBe("_/\n");
      await Promise.all(
        ["pre-commit", "pre-push"].map(async (hook) => {
          const source = await readFile(
            path.join(directory, ".husky", hook),
            "utf-8"
          );
          expect(source).toBe(
            `#!/usr/bin/env sh\n# -*- coding: utf-8 -*-\nset -e\npnpm run ${hook}\n`
          );
        })
      );
      if (profile === "gradle" || profile === "kotlin") {
        expect(manifest.scripts).toMatchObject({
          "gradle:check": "./gradlew check",
          "gradle:ktlintCheck": "./gradlew ktlintCheck"
        });
        expect(
          await readFile(
            path.join(directory, "gradle/libs.versions.toml"),
            "utf-8"
          )
        ).toBe(
          await readFile(
            path.join(
              repositoryRoot,
              "plugins/harness/tooling",
              profile === "kotlin"
                ? "kotlin/gradle/libs.versions.toml"
                : "gradle/libs.versions.toml.fragment"
            ),
            "utf-8"
          )
        );
      }
      if (profile === "rust") {
        await Promise.all(
          [
            ".editorconfig",
            "rustfmt.toml",
            "clippy.toml",
            "rust-toolchain.toml"
          ].map(async (file) => {
            const [materialized, source] = await Promise.all([
              readFile(path.join(directory, file), "utf-8"),
              readFile(
                path.join(repositoryRoot, "plugins/harness/tooling/rust", file),
                "utf-8"
              )
            ]);
            expect(first.created).toContain(file);
            expect(materialized).toBe(source);
          })
        );
        expect(await readdir(directory)).not.toContain("Cargo.toml");
      }
      if (profile === "node") {
        expect(manifest.scripts["api:docs"]).toBe("typedoc");
        expect(manifest.devDependencies.typedoc).toBe("catalog:");
        expect(first.created).toContain("typedoc.json");
      }
      if (profile === "uv") {
        expect(first.created).toContain("docs/conf.py");
        expect(first.created).toContain("docs/index.rst");
      }
      if (profile === "node" || profile === "frontend" || profile === "react") {
        expect(manifest.scripts).toMatchObject({
          "check:ultracite": "run-p --continue-on-error ultracite:check:**",
          "fix:ultracite":
            "run-s --continue-on-error ultracite:fix:base ultracite:check:style:*",
          "ultracite:check:base": "ultracite check --type-aware",
          "ultracite:check:style:no-blank-lines-in-functions":
            "ultracite check --type-aware --config oxlint.style-no-blank-lines-in-functions.config.ts",
          "ultracite:check:style:no-inline-comments-in-functions":
            "ultracite check --type-aware --config oxlint.style-no-inline-comments-in-functions.config.ts",
          "ultracite:check:style:sibling-test-spacing":
            "ultracite check --type-aware --config oxlint.style-sibling-test-spacing.config.ts",
          "ultracite:fix:base": "ultracite fix --type-aware"
        });
        expect(
          Object.keys(manifest.scripts).filter((script) =>
            script.startsWith("check:")
          )
        ).toEqual(
          profile === "node"
            ? ["check:markdownlint-cli2", "check:ultracite"]
            : [
                "check:design",
                "check:markdownlint-cli2",
                "check:storybook",
                "check:ultracite"
              ]
        );
        expect(
          Object.keys(manifest.scripts).filter((script) =>
            script.startsWith("fix:")
          )
        ).toEqual(["fix:markdownlint-cli2", "fix:ultracite"]);
        expect(
          Object.keys(manifest.scripts).filter((script) =>
            script.startsWith("ultracite:fix:")
          )
        ).toEqual(["ultracite:fix:base"]);
        expect(
          Object.values(manifest.scripts).filter((command) =>
            /^(?:oxlint|oxfmt)\b/u.test(String(command))
          )
        ).toEqual([]);
        expect(
          await readFile(path.join(directory, "oxfmt.config.ts"), "utf-8")
        ).toBe(
          await readFile(
            path.join(
              repositoryRoot,
              "plugins/harness/tooling/pnpm/oxfmt.config.ts"
            ),
            "utf-8"
          )
        );
        const configurationFiles = [
          "oxlint.config.ts",
          "oxlint.style-no-blank-lines-in-functions.config.ts",
          "oxlint.style-no-inline-comments-in-functions.config.ts",
          "oxlint.style-sibling-test-spacing.config.ts"
        ];
        await Promise.all(
          configurationFiles.map(async (file) => {
            const [materialized, source] = await Promise.all([
              readFile(path.join(directory, file), "utf-8"),
              readFile(
                path.join(repositoryRoot, "plugins/harness/tooling/pnpm", file),
                "utf-8"
              )
            ]);
            expect(materialized).toBe(source);
          })
        );
        const pluginFiles = await readdir(path.join(directory, "plugins"));
        expect(pluginFiles.toSorted()).toEqual([
          "no-blank-lines-in-functions.ts",
          "no-direct-use-effect.ts",
          "no-inline-comments-in-functions.ts",
          "sibling-test-spacing.ts",
          "tsdoc-plugin.ts"
        ]);
      }
      if (profile === "frontend" || profile === "react") {
        expect(manifest.scripts["check:storybook"]).toBe(
          "storybook build --test"
        );
        expect(manifest.devDependencies.storybook).toBe("catalog:");
        expect(
          spawnSync("git", ["init", "--quiet"], {
            cwd: directory,
            env: fixtureEnvironment()
          }).status
        ).toBe(0);
        const ignored = spawnSync(
          "git",
          ["check-ignore", "--no-index", "storybook-static/iframe.html"],
          { cwd: directory, env: fixtureEnvironment() }
        );
        expect(ignored.status).toBe(0);
        expect(ignored.stdout.toString().trim()).toBe(
          "storybook-static/iframe.html"
        );
        expect(
          await readFile(path.join(directory, ".storybook/main.ts"), "utf-8")
        ).toBe(
          await readFile(
            path.join(
              repositoryRoot,
              `plugins/harness/tooling/${profile}/.storybook/main.ts.fragment`
            ),
            "utf-8"
          )
        );
      }
      if (profile === "react") {
        expect(manifest.dependencies).toMatchObject({
          "@radix-ui/colors": "catalog:",
          "@radix-ui/react-icons": "catalog:",
          "@radix-ui/themes": "catalog:",
          "radix-ui": "catalog:"
        });
      }
      const second = await configureBaseline(directory, profile, "eligible");
      expect(second.created).toEqual([]);
      expect(second.kept.toSorted()).toEqual(first.created.toSorted());
    }
  );

  test.each(["archived", "inactive", "configuration-only"] as const)(
    "%s leaves the repository unchanged",
    async (eligibility) => {
      const directory = await targetDirectory();
      await writeFile(path.join(directory, "config.yaml"), "enabled: true\n");
      expect(await configureBaseline(directory, "node", eligibility)).toEqual({
        created: [],
        kept: [],
        status: "skipped"
      });
      const result = spawnSync(process.execPath, [
        path.join(repositoryRoot, "plugins/harness/scripts/configure.ts"),
        "--target",
        directory,
        "--profile",
        "node",
        "--eligibility",
        eligibility
      ]);
      expect(result.status).toBe(0);
      expect(JSON.parse(result.stdout.toString()).status).toBe("skipped");
      expect(await readdir(directory)).toEqual(["config.yaml"]);
      expect(await readFile(path.join(directory, "config.yaml"), "utf-8")).toBe(
        "enabled: true\n"
      );
    }
  );

  test.each(["gradle", "rust"] as const)(
    "a conflicting %s package blocks all writes",
    async (profile) => {
      const directory = await targetDirectory();
      await writeFile(
        path.join(directory, "package.json"),
        '{"name":"existing-owner"}\n'
      );
      await expect(
        configureBaseline(directory, profile, "eligible")
      ).rejects.toThrow("Conflict at package.json");
      expect(await readdir(directory)).toEqual(["package.json"]);
      expect(
        await readFile(path.join(directory, "package.json"), "utf-8")
      ).toBe('{"name":"existing-owner"}\n');
    }
  );

  test("a symlink ancestor blocks all writes", async () => {
    const directory = await targetDirectory();
    const outside = await targetDirectory();
    await symlink(outside, path.join(directory, ".husky"), "dir");
    await expect(
      configureBaseline(directory, "node", "eligible")
    ).rejects.toThrow("Unsafe destination ancestor");
    expect(await readdir(directory)).toEqual([".husky"]);
    expect(await readdir(outside)).toEqual([]);
  });

  test("a conflicting Python documentation configuration blocks all writes", async () => {
    const directory = await targetDirectory();
    await mkdir(path.join(directory, "docs"));
    await writeFile(
      path.join(directory, "docs/conf.py"),
      "project = 'Existing'\n"
    );
    await expect(
      configureBaseline(directory, "uv", "eligible")
    ).rejects.toThrow("Conflict at docs/conf.py");
    expect(await readdir(directory)).toEqual(["docs"]);
  });

  test("an existing Gradle version catalog retains its own versions", async () => {
    const directory = await targetDirectory();
    await mkdir(path.join(directory, "gradle"));
    const catalog = '[versions]\nkotlin = "target-owned"\n';
    await writeFile(path.join(directory, "gradle/libs.versions.toml"), catalog);
    const result = await configureBaseline(directory, "kotlin", "eligible");
    expect(result.kept).toEqual(["gradle/libs.versions.toml"]);
    expect(
      await readFile(path.join(directory, "gradle/libs.versions.toml"), "utf-8")
    ).toBe(catalog);
  });

  test.each([
    ["rustfmt.toml", "rustfmt.toml", "max_width = 88\n"],
    [".rustfmt.toml", "rustfmt.toml", "max_width = 88\n"],
    ["clippy.toml", "clippy.toml", "cognitive-complexity-threshold = 15\n"],
    [".clippy.toml", "clippy.toml", "cognitive-complexity-threshold = 15\n"],
    [
      "rust-toolchain.toml",
      "rust-toolchain.toml",
      '[toolchain]\nchannel = "1.85.0"\n'
    ],
    ["rust-toolchain", "rust-toolchain.toml", "1.85.0\n"],
    [".editorconfig", ".editorconfig", "root = true\n[*.rs]\nindent_size = 2\n"]
  ])(
    "Rust retains target-owned %s without creating a competing file",
    async (relative, canonical, content) => {
      const directory = await targetDirectory();
      const cargo =
        '[package]\nname = "existing-crate"\nversion = "0.1.0"\nedition = "2021"\nrust-version = "1.85"\n';
      await writeFile(path.join(directory, "Cargo.toml"), cargo);
      await writeFile(path.join(directory, relative), content);
      const first = await configureBaseline(directory, "rust", "eligible");
      expect(first.kept).toContain(relative);
      expect(first.created).not.toContain(canonical);
      expect(await readFile(path.join(directory, relative), "utf-8")).toBe(
        content
      );
      expect(await readFile(path.join(directory, "Cargo.toml"), "utf-8")).toBe(
        cargo
      );
      if (relative !== canonical) {
        expect(await readdir(directory)).not.toContain(canonical);
      }
      const second = await configureBaseline(directory, "rust", "eligible");
      expect(second.created).toEqual([]);
      expect(second.kept).toContain(relative);
      expect(await readFile(path.join(directory, relative), "utf-8")).toBe(
        content
      );
    }
  );

  test("Rust retains both existing formatter filenames", async () => {
    const directory = await targetDirectory();
    await writeFile(path.join(directory, "rustfmt.toml"), "max_width = 88\n");
    await writeFile(path.join(directory, ".rustfmt.toml"), "max_width = 92\n");
    const result = await configureBaseline(directory, "rust", "eligible");
    expect(result.kept.toSorted()).toEqual([".rustfmt.toml", "rustfmt.toml"]);
    expect(await readFile(path.join(directory, "rustfmt.toml"), "utf-8")).toBe(
      "max_width = 88\n"
    );
    expect(await readFile(path.join(directory, ".rustfmt.toml"), "utf-8")).toBe(
      "max_width = 92\n"
    );
  });

  test.each([
    ".editorconfig",
    "rustfmt.toml",
    ".rustfmt.toml",
    "clippy.toml",
    ".clippy.toml",
    "rust-toolchain.toml",
    "rust-toolchain"
  ])("an unsafe Rust destination %s blocks all writes", async (relative) => {
    const directory = await targetDirectory();
    const outside = await targetDirectory();
    await writeFile(path.join(outside, "config"), "target-owned\n");
    await symlink(path.join(outside, "config"), path.join(directory, relative));
    await expect(
      configureBaseline(directory, "rust", "eligible")
    ).rejects.toThrow(`Unsafe destination ${relative}`);
    expect(await readdir(directory)).toEqual([relative]);
    expect(await readFile(path.join(outside, "config"), "utf-8")).toBe(
      "target-owned\n"
    );
    await rm(path.join(directory, relative));
    await mkdir(path.join(directory, relative));
    await expect(
      configureBaseline(directory, "rust", "eligible")
    ).rejects.toThrow(`Unsafe destination ${relative}`);
    expect(await readdir(directory)).toEqual([relative]);
    expect(await readdir(path.join(directory, relative))).toEqual([]);
  });

  test("the native Node CLI validates choices and configures the selected profile", async () => {
    const directory = await targetDirectory();
    const script = path.join(
      repositoryRoot,
      "plugins/harness/scripts/configure.ts"
    );
    expect(spawnSync(process.execPath, [script, "--help"]).status).toBe(0);
    const alias = path.join(directory, "plugin-alias");
    await symlink(path.dirname(script), alias, "dir");
    const aliasHelp = spawnSync(process.execPath, [
      path.join(alias, "configure.ts"),
      "--help"
    ]);
    expect(aliasHelp.status).toBe(0);
    expect(aliasHelp.stdout.toString()).toContain("--profile");
    await rm(alias);
    const invalid = spawnSync(process.execPath, [
      script,
      "--target",
      directory,
      "--profile",
      "unsupported",
      "--eligibility",
      "eligible"
    ]);
    expect(invalid.status).toBe(1);
    expect(await readdir(directory)).toEqual([]);
    const result = spawnSync(process.execPath, [
      script,
      "--target",
      directory,
      "--profile",
      "go",
      "--eligibility",
      "eligible"
    ]);
    expect(result.status).toBe(0);
    expect(JSON.parse(result.stdout.toString()).status).toBe("configured");
  });

  test("the frontend design gate rejects an invalid color token", async () => {
    const directory = await targetDirectory();
    await configureBaseline(directory, "frontend", "eligible");
    const binary = path.join(repositoryRoot, "node_modules/.bin/designmd");
    const valid = spawnSync(binary, ["lint", "DESIGN.md"], { cwd: directory });
    expect(valid.status).toBe(0);
    expect(JSON.parse(valid.stdout.toString()).summary.errors).toBe(0);
    await writeFile(
      path.join(directory, "DESIGN.md"),
      "---\nname: Project\ncolors:\n  primary: invalid-color\n---\n"
    );
    const invalid = spawnSync(binary, ["lint", "DESIGN.md"], {
      cwd: directory
    });
    expect(invalid.status).toBe(1);
    expect(
      JSON.parse(invalid.stdout.toString()).summary.errors
    ).toBeGreaterThan(0);
  });

  test.each(["git", "pnpm"])(
    "Node %s hooks run the actual Ultracite gate and reject a source violation",
    async (entrypoint) => {
      const directory = await targetDirectory();
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
            "user.name=Node Fixture",
            "-c",
            "user.email=fixture@example.invalid",
            ...args
          ],
          options
        );
      expect(git("init", "--initial-branch=main").status).toBe(0);
      await writeFile(
        path.join(directory, "README.md"),
        "# Target\n\nReady.\n"
      );
      expect(git("add", ".").status).toBe(0);
      expect(git("commit", "-m", "Initial fixture").status).toBe(0);
      const remote = path.join(directory, "remote.git");
      expect(git("init", "--bare", remote).status).toBe(0);
      expect(git("remote", "add", "origin", remote).status).toBe(0);
      await configureBaseline(directory, "node", "eligible");
      await symlink(
        path.join(repositoryRoot, "node_modules"),
        path.join(directory, "node_modules"),
        "dir"
      );
      expect(spawnSync("pnpm", ["run", "prepare"], options).status).toBe(0);
      expect(git("add", ".").status).toBe(0);
      const commit =
        entrypoint === "pnpm"
          ? spawnSync("pnpm", ["run", "pre-commit"], options)
          : git("commit", "-m", "Configure Node baseline");
      expect(
        commit.status,
        commit.stderr.toString() + commit.stdout.toString()
      ).toBe(0);
      expect(
        (entrypoint === "pnpm"
          ? spawnSync("pnpm", ["run", "pre-push"], options)
          : git("push", "origin", "main")
        ).status
      ).toBe(0);
      await writeFile(
        path.join(directory, "bad.ts"),
        "export function undocumented(): boolean {\n  return true;\n}\n"
      );
      expect(git("add", "bad.ts").status).toBe(0);
      expect(
        (entrypoint === "pnpm"
          ? spawnSync("pnpm", ["run", "pre-commit"], options)
          : git("commit", "-m", "Invalid source")
        ).status
      ).toBe(1);
      expect(
        (entrypoint === "pnpm"
          ? spawnSync("pnpm", ["run", "pre-push"], options)
          : git("push", "origin", "main")
        ).status
      ).toBe(1);
      expect(git("log", "-1", "--format=%s").stdout.toString().trim()).toBe(
        entrypoint === "pnpm" ? "Initial fixture" : "Configure Node baseline"
      );
    },
    30_000
  );
});

describe("Maven and Go hook entrypoints", () => {
  test.each(
    (
      [
        {
          calls: "mvnw spotless:check checkstyle:check\n",
          hook: "pre-commit",
          profile: "maven",
          valid: true
        },
        {
          calls: "mvnw verify\n",
          hook: "pre-push",
          profile: "maven",
          valid: true
        },
        {
          calls: "go task lint\ngo task format\ngo task nilaway\n",
          hook: "pre-commit",
          profile: "go",
          valid: true
        },
        {
          calls: "go task check\n",
          hook: "pre-push",
          profile: "go",
          valid: true
        },
        { calls: "", hook: "pre-commit", profile: "maven", valid: false },
        { calls: "", hook: "pre-push", profile: "maven", valid: false },
        { calls: "", hook: "pre-commit", profile: "go", valid: false },
        { calls: "", hook: "pre-push", profile: "go", valid: false }
      ] as const
    ).flatMap((entry) =>
      ["git", "pnpm"].map((entrypoint) => ({ ...entry, entrypoint }))
    )
  )(
    "$profile $entrypoint $hook honors the shared gate (valid=$valid)",
    async ({ profile, entrypoint, hook, valid, calls }) => {
      const directory = await targetDirectory();
      const options = {
        cwd: directory,
        env: {
          ...fixtureEnvironment(),
          PATH: `${path.join(directory, ".bin")}${path.delimiter}${path.join(repositoryRoot, "node_modules/.bin")}${path.delimiter}${process.env.PATH}`
        }
      };
      const git = (...args: string[]) =>
        spawnSync(
          "git",
          [
            "-c",
            "user.name=Baseline Fixture",
            "-c",
            "user.email=fixture@example.invalid",
            ...args
          ],
          options
        );
      expect(git("init", "--initial-branch=main").status).toBe(0);
      await writeFile(
        path.join(directory, "README.md"),
        "# Target\n\nReady.\n"
      );
      expect(git("add", ".").status).toBe(0);
      expect(git("commit", "-m", "Initial fixture").status).toBe(0);
      const remote = path.join(directory, "remote.git");
      expect(git("init", "--bare", remote).status).toBe(0);
      expect(git("remote", "add", "origin", remote).status).toBe(0);
      await configureBaseline(directory, profile, "eligible");
      await mkdir(path.join(directory, ".bin"));
      const stubs = profile === "maven" ? ["mvnw"] : ["go"];
      await Promise.all(
        stubs.map((name) =>
          writeFile(
            path.join(directory, name === "mvnw" ? name : `.bin/${name}`),
            name === "go"
              ? `#!/usr/bin/env sh\nif [ "$1" = env ]; then echo off; else printf 'go task %s\\n' "$7" >> '${path.join(directory, "native-calls.txt")}'; fi\n`
              : `#!/usr/bin/env sh\nprintf 'mvnw %s\\n' "$*" >> '${path.join(directory, "native-calls.txt")}'\n`,
            { mode: 0o755 }
          )
        )
      );
      expect(spawnSync("pnpm", ["run", "prepare"], options).status).toBe(0);
      await writeFile(
        path.join(directory, "README.md"),
        valid ? "# Target\n\nChanged.\n" : "# First\n\n# Second\n"
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
      expect(result.status).toBe(valid ? 0 : 1);
      if (valid) {
        expect(
          await readFile(path.join(directory, "native-calls.txt"), "utf-8")
        ).toBe(calls);
      } else {
        await expect(
          readFile(path.join(directory, "native-calls.txt"))
        ).rejects.toMatchObject({ code: "ENOENT" });
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
