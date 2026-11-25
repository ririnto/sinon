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
import { format } from "oxfmt";
import { describe, expect, onTestFinished, test } from "vitest";
import { parseDocument } from "yaml";

import { configureBaseline } from "../scripts/configure.js";
import type {
  BaselineProfile,
  TypeScriptProfile
} from "../scripts/configure.js";
import formatter from "../tooling/pnpm/oxfmt.config.ts";
import { createCommandFixture } from "./fixture-command.ts";
import { valueAt, objectValue, optionalObject } from "./structured-value.ts";

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
  onTestFinished(async () => {
    await rm(directory, { force: true, recursive: true });
  });
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
    ["node", "@tsconfig/node24/tsconfig.json", "@tsconfig/node24"],
    [
      "vite-react",
      "@tsconfig/vite-react/tsconfig.json",
      "@tsconfig/vite-react"
    ],
    ["vue", "@vue/tsconfig/tsconfig.dom.json", "@vue/tsconfig"],
    [
      "react-native",
      "@tsconfig/react-native/tsconfig.json",
      "@tsconfig/react-native"
    ],
    ["next", "@tsconfig/next/tsconfig.json", "@tsconfig/next"]
  ] as [TypeScriptProfile, string, string][])(
    "materializes the %s TypeScript profile with target-owned dependencies",
    async (selected, config, dependency) => {
      const directory = await targetDirectory();
      await configureBaseline(directory, "frontend", "eligible", selected);
      const tsconfigSource = await readFile(
        path.join(directory, "tsconfig.json"),
        "utf-8"
      );
      const { code } = await format("tsconfig.json", tsconfigSource, formatter);
      expect(code).toBe(tsconfigSource);
      const tsconfig: unknown = JSON.parse(tsconfigSource);
      const manifest: unknown = JSON.parse(
        await readFile(path.join(directory, "package.json"), "utf-8")
      );
      const workspace = parseDocument(
        await readFile(path.join(directory, "pnpm-workspace.yaml"), "utf-8")
      );
      expect(valueAt(tsconfig, "extends")).toBe(config);
      expect(
        valueAt(tsconfig, "compilerOptions", "noUncheckedIndexedAccess")
      ).toBe(true);
      expect(valueAt(manifest, "devDependencies", dependency)).toBe("catalog:");
      expect(workspace.getIn(["catalog", dependency])).toBeTruthy();
      if (selected === "vue") {
        expect(valueAt(manifest, "devDependencies", "typescript")).toBe(
          "catalog:vue"
        );
        expect(workspace.getIn(["catalogs", "vue", "typescript"])).toBe(
          "^6.0.3"
        );
      }
      expect(valueAt(tsconfig, "include")).toEqual(["**/*", "**/.*/**/*"]);
      expect(valueAt(tsconfig, "compilerOptions", "allowJs")).toBe(true);
      expect(valueAt(tsconfig, "compilerOptions", "noEmit")).toBeUndefined();
      if (selected === "vite-react") {
        expect(valueAt(tsconfig, "compilerOptions", "tsBuildInfoFile")).toBe(
          "./node_modules/.tmp/tsconfig.app.tsbuildinfo"
        );
      }
      const second = await configureBaseline(
        directory,
        "frontend",
        "eligible",
        selected
      );
      expect(second.created).toEqual([]);
    }
  );

  test.each(["node", "frontend", "react"] as const)(
    "creates a type-aware JavaScript project for %s without an explicit TypeScript profile",
    async (profile) => {
      const directory = await targetDirectory();
      await configureBaseline(directory, profile, "eligible");
      const config: unknown = JSON.parse(
        await readFile(path.join(directory, "tsconfig.json"), "utf-8")
      );
      expect(valueAt(config, "compilerOptions", "allowJs")).toBe(true);
      expect(valueAt(config, "compilerOptions", "noEmit")).toBe(true);
      expect(valueAt(config, "include")).toEqual(["**/*", "**/.*/**/*"]);
    }
  );

  test("rejects a conflicting TypeScript configuration before writing", async () => {
    const directory = await targetDirectory();
    await writeFile(path.join(directory, "tsconfig.json"), "{}\n");
    await expect(
      configureBaseline(directory, "node", "eligible", "node")
    ).rejects.toThrow("Conflict at tsconfig.json");
    expect(await readdir(directory)).toEqual(["tsconfig.json"]);
  });

  test.each([
    ["ci/github/pnpm.yaml", ".github/workflows/pnpm.yaml"],
    ["ci/gitlab/pnpm.gitlab-ci.yaml", ".gitlab-ci.yml"]
  ])(
    "ships the pnpm bootstrap from %s in the workspace sample",
    async (template, sample) => {
      const harness = path.join(repositoryRoot, "plugins/harness");
      const yaml = await readFile(path.join(harness, template), "utf-8");
      expect(
        await readFile(
          path.join(harness, "samples/pnpm-workspace", sample),
          "utf-8"
        )
      ).toBe(yaml);
      const ci: unknown = parseDocument(yaml).toJS();
      if (sample.startsWith(".github/")) {
        const steps = valueAt(ci, "jobs", "pnpm", "steps");
        expect(steps).toHaveLength(5);
        expect(valueAt(steps, 0, "uses")).toMatch(/^actions\/checkout@/u);
        expect(valueAt(steps, 1, "uses")).toMatch(/^actions\/setup-node@/u);
        expect(valueAt(steps, 1, "with")).toEqual({
          "node-version-file": "package.json"
        });
        expect(valueAt(steps, 2, "uses")).toMatch(/^pnpm\/action-setup@/u);
        expect(valueAt(steps, 2, "with")).toEqual({
          package_json_file: "package.json"
        });
        expect(valueAt(steps, 3, "run")).toBe("pnpm install --frozen-lockfile");
        expect(valueAt(steps, 4, "run")).toBe("pnpm run check");
      } else {
        expect(valueAt(ci, "pnpm", "before_script")).toEqual(
          expect.arrayContaining([expect.stringContaining("packageManager")])
        );
        expect(valueAt(ci, "pnpm", "script")).toEqual([
          "pnpm install --frozen-lockfile",
          "pnpm run check"
        ]);
      }
    }
  );

  test.each(profiles)(
    "%s materializes the shared gate and retains it on repetition",
    async (profile) => {
      const directory = await targetDirectory();
      const first = await configureBaseline(directory, profile, "eligible");
      expect(first.status).toBe("configured");
      const manifest: unknown = JSON.parse(
        await readFile(path.join(directory, "package.json"), "utf-8")
      );
      expect(valueAt(manifest, "private")).toBe(true);
      expect(
        valueAt(manifest, "devDependencies", "markdownlint-rule-search-replace")
      ).toBe("catalog:");
      expect(valueAt(manifest, "packageManager")).toBeUndefined();
      const rootManifest: unknown = JSON.parse(
        await readFile(path.join(repositoryRoot, "package.json"), "utf-8")
      );
      expect(valueAt(manifest, "engines", "node")).toBe(
        valueAt(rootManifest, "engines", "node")
      );
      const workspace = parseDocument(
        await readFile(path.join(directory, "pnpm-workspace.yaml"), "utf-8"),
        { uniqueKeys: true }
      );
      expect(workspace.errors).toEqual([]);
      const catalog = objectValue(valueAt(workspace.toJS(), "catalog"));
      expect(valueAt(workspace.toJS(), "allowBuilds")).toEqual(
        profile === "node" || profile === "frontend" || profile === "react"
          ? { esbuild: true }
          : undefined
      );
      for (const [name, version] of Object.entries({
        ...optionalObject(valueAt(manifest, "dependencies")),
        ...objectValue(valueAt(manifest, "devDependencies"))
      })) {
        expect(version).toBe("catalog:");
        expect(catalog[name]).toBeTypeOf("string");
      }
      const configuration: unknown = JSON.parse(
        await readFile(
          path.join(directory, ".markdownlint-cli2.jsonc"),
          "utf-8"
        )
      );
      assertSortedKeys(configuration);
      expect(valueAt(configuration, "gitignore")).toBe(true);
      expect(valueAt(configuration, "config", "table-column-style")).toEqual({
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
            config: objectValue(valueAt(configuration, "config")),
            strings: { sample: text }
          }).sample.map(({ ruleNames }) => ruleNames[0])
        ).toEqual(expected);
      }
      const rules = await readdir(path.join(directory, "rules"));
      expect(rules.toSorted()).toEqual([
        "markdown-prose.ts",
        "no-em-dash.ts",
        "no-relative-skill-links.ts",
        "no-sensitive-content.mjs",
        "table-separators.ts"
      ]);
      expect(
        valueAt(configuration, "config", "sensitive-content")
      ).toBeUndefined();
      expect(valueAt(configuration, "customRules")).not.toContain(
        "rules/no-sensitive-content.mjs"
      );
      if (profile === "gradle" || profile === "kotlin") {
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
        expect(valueAt(manifest, "devDependencies", "typedoc")).toBe(
          "catalog:"
        );
        expect(first.created).toContain("typedoc.json");
      }
      if (profile === "uv") {
        expect(first.created).toContain("docs/conf.py");
        expect(first.created).toContain("docs/index.rst");
      }
      if (profile === "go") {
        expect(first.created).toContain("Taskfile.yaml");
        expect(await readdir(directory)).not.toContain("tools");
      }
      if (profile === "node" || profile === "frontend" || profile === "react") {
        expect(
          Object.keys(objectValue(valueAt(manifest, "scripts"))).filter(
            (script) => script.startsWith("check:")
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
          Object.keys(objectValue(valueAt(manifest, "scripts"))).filter(
            (script) => script.startsWith("fix:")
          )
        ).toEqual(["fix:markdownlint-cli2", "fix:ultracite"]);
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
        const installedFiles = await readdir(directory);
        expect(
          installedFiles
            .filter(
              (file) => file.startsWith("oxlint") || file.startsWith("oxfmt")
            )
            .toSorted()
        ).toEqual(["oxfmt.config.ts", "oxlint.config.ts"]);
        expect(
          await readFile(path.join(directory, "oxlint.config.ts"), "utf-8")
        ).toBe(
          await readFile(
            path.join(
              repositoryRoot,
              "plugins/harness/tooling/pnpm/oxlint.config.ts"
            ),
            "utf-8"
          )
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
        expect(valueAt(manifest, "devDependencies", "storybook")).toBe(
          "catalog:"
        );
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
        expect(valueAt(manifest, "dependencies")).toMatchObject({
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
      expect(valueAt(JSON.parse(result.stdout.toString()), "status")).toBe(
        "skipped"
      );
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
    const moduleFile = path.join(directory, "go.mod");
    const module = "module example.com/target\n\ngo 1.27.0\n";
    await writeFile(moduleFile, module);
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
    expect(valueAt(JSON.parse(result.stdout.toString()), "status")).toBe(
      "configured"
    );
    expect(await readFile(moduleFile, "utf-8")).toBe(module);
  });

  test("the frontend design gate rejects an invalid color token", async () => {
    const directory = await targetDirectory();
    await configureBaseline(directory, "frontend", "eligible");
    const binary = path.join(repositoryRoot, "node_modules/.bin/designmd");
    const valid = spawnSync(binary, ["lint", "DESIGN.md"], { cwd: directory });
    expect(valid.status).toBe(0);
    expect(
      valueAt(JSON.parse(valid.stdout.toString()), "summary", "errors")
    ).toBe(0);
    await writeFile(
      path.join(directory, "DESIGN.md"),
      "---\nname: Project\ncolors:\n  primary: invalid-color\n---\n"
    );
    const invalid = spawnSync(binary, ["lint", "DESIGN.md"], {
      cwd: directory
    });
    expect(invalid.status).toBe(1);
    expect(
      valueAt(JSON.parse(invalid.stdout.toString()), "summary", "errors")
    ).toBeGreaterThan(0);
  });

  test.concurrent.for([
    ["git", undefined],
    ["pnpm", "node"]
  ] as const)(
    "Node %s hooks run the actual Ultracite gate and reject a source violation",
    { timeout: 30_000 },
    async ([entrypoint, typescriptProfile], { expect: check, signal }) => {
      await using fixture = await createCommandFixture("sinon-baseline-");
      const { directory } = fixture;
      const options = {
        cwd: directory,
        signal,
        env: {
          ...fixtureEnvironment(),
          PATH: `${path.join(repositoryRoot, "node_modules/.bin")}${path.delimiter}${process.env.PATH}`
        }
      };
      const identity = [
        "-c",
        "user.name=Node Fixture",
        "-c",
        "user.email=fixture@example.invalid"
      ];
      const git = (...args: string[]) =>
        spawnSync("git", [...identity, ...args], options);
      check(git("init", "--initial-branch=main").status).toBe(0);
      await writeFile(
        path.join(directory, "README.md"),
        "# Target\n\nReady.\n"
      );
      check(git("add", ".").status).toBe(0);
      check(git("commit", "-m", "Initial fixture").status).toBe(0);
      const remote = path.join(directory, "remote.git");
      check(git("init", "--bare", remote).status).toBe(0);
      check(git("remote", "add", "origin", remote).status).toBe(0);
      await configureBaseline(directory, "node", "eligible", typescriptProfile);
      await symlink(
        path.join(repositoryRoot, "node_modules"),
        path.join(directory, "node_modules"),
        "dir"
      );
      const preparation = await fixture.run(
        "pnpm",
        ["run", "prepare"],
        options
      );
      check(preparation.status).toBe(0);
      if (typescriptProfile === "node") {
        const build = await fixture.run("pnpm", ["exec", "tsc"], options);
        check(
          build.status,
          build.stdout.toString() + build.stderr.toString()
        ).toBe(0);
        const load = await fixture.run(
          process.execPath,
          [
            "--input-type=module",
            "--eval",
            'await import("./dist/rules/table-separators.js")'
          ],
          options
        );
        check(load.status, load.stderr.toString()).toBe(0);
      }
      await writeFile(
        path.join(directory, "good.ts"),
        "export const isReady = (): boolean => true;\n"
      );
      check(git("add", ".").status).toBe(0);
      check(git("ls-files", "--", ".husky/_").stdout.toString()).toBe("");
      const commit =
        entrypoint === "pnpm"
          ? await fixture.run("pnpm", ["run", "pre-commit"], options)
          : await fixture.run(
              "git",
              [...identity, "commit", "-m", "Configure Node baseline"],
              options
            );
      check(
        commit.status,
        commit.stderr.toString() + commit.stdout.toString()
      ).toBe(0);
      check(
        (entrypoint === "pnpm"
          ? await fixture.run("pnpm", ["run", "pre-push"], options)
          : await fixture.run(
              "git",
              [...identity, "push", "origin", "main"],
              options
            )
        ).status
      ).toBe(0);
      await writeFile(
        path.join(directory, "bad.ts"),
        "// Bad \u2014 prose\n/** Incorrect delimiters. */\nexport const invalidDocumentation = (): boolean => true;\n"
      );
      check(git("add", "bad.ts").status).toBe(0);
      const rejectedCommit =
        entrypoint === "pnpm"
          ? await fixture.run("pnpm", ["run", "pre-commit"], options)
          : await fixture.run(
              "git",
              [...identity, "commit", "-m", "Invalid source"],
              options
            );
      const rejectedPush =
        entrypoint === "pnpm"
          ? await fixture.run("pnpm", ["run", "pre-push"], options)
          : await fixture.run(
              "git",
              [...identity, "push", "origin", "main"],
              options
            );
      for (const result of [rejectedCommit, rejectedPush]) {
        const output = result.stderr.toString() + result.stdout.toString();
        check(result.status, output).toBe(1);
        check(output).toContain("tsdoc(require-export-tsdoc)");
        check(output).toContain("Use multiline TSDoc");
      }
      check(git("log", "-1", "--format=%s").stdout.toString().trim()).toBe(
        entrypoint === "pnpm" ? "Initial fixture" : "Configure Node baseline"
      );
    }
  );
});

describe("Maven and Go hook entrypoints", () => {
  const goTaskCalls = ["lint", "format", "nilaway", "test"]
    .map(
      (task) =>
        `go tool -modfile=go.mod github.com/go-task/task/v3/cmd/task ${task}`
    )
    .join("\n");
  test.concurrent.for(
    (
      [
        {
          calls: "mvnw verify\n",
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
          calls: goTaskCalls,
          hook: "pre-commit",
          profile: "go",
          valid: true
        },
        {
          calls: goTaskCalls,
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
    { timeout: 30_000 },
    async (
      { profile, entrypoint, hook, valid, calls },
      { expect: check, signal }
    ) => {
      await using fixture = await createCommandFixture("sinon-baseline-");
      const { directory } = fixture;
      const options = {
        cwd: directory,
        signal,
        env: {
          ...fixtureEnvironment(),
          PATH: `${path.join(directory, ".bin")}${path.delimiter}${path.join(repositoryRoot, "node_modules/.bin")}${path.delimiter}${process.env.PATH}`
        }
      };
      const identity = [
        "-c",
        "user.name=Baseline Fixture",
        "-c",
        "user.email=fixture@example.invalid"
      ];
      const git = (...args: string[]) =>
        spawnSync("git", [...identity, ...args], options);
      check(git("init", "--initial-branch=main").status).toBe(0);
      await writeFile(
        path.join(directory, "README.md"),
        "# Target\n\nReady.\n"
      );
      check(git("add", ".").status).toBe(0);
      check(git("commit", "-m", "Initial fixture").status).toBe(0);
      const remote = path.join(directory, "remote.git");
      check(git("init", "--bare", remote).status).toBe(0);
      check(git("remote", "add", "origin", remote).status).toBe(0);
      await configureBaseline(directory, profile, "eligible");
      await mkdir(path.join(directory, ".bin"));
      const stubs = profile === "maven" ? ["mvnw"] : ["go"];
      await Promise.all(
        stubs.map(async (name) => {
          await writeFile(
            path.join(directory, name === "mvnw" ? name : `.bin/${name}`),
            name === "go"
              ? `#!/usr/bin/env sh\nif [ "$1" = env ]; then\n  echo off\nelse\n  printf 'go %s\\n' "$*" >> '${path.join(directory, "native-calls.txt")}'\nfi\n`
              : `#!/usr/bin/env sh\nprintf 'mvnw %s\\n' "$*" >> '${path.join(directory, "native-calls.txt")}'\n`,
            { mode: 0o755 }
          );
        })
      );
      const preparation = await fixture.run(
        "pnpm",
        ["run", "prepare"],
        options
      );
      check(preparation.status).toBe(0);
      await writeFile(
        path.join(directory, "README.md"),
        valid ? "# Target\n\nChanged.\n" : "# First\n\n# Second\n"
      );
      check(git("add", "README.md").status).toBe(0);
      const gitArguments =
        hook === "pre-commit"
          ? ["commit", "-m", "Changed fixture"]
          : ["push", "origin", "main"];
      const result =
        entrypoint === "pnpm"
          ? await fixture.run("pnpm", ["run", hook], options)
          : await fixture.run("git", [...identity, ...gitArguments], options);
      check(result.status).toBe(valid ? 0 : 1);
      if (valid) {
        const recordedCalls = await readFile(
          path.join(directory, "native-calls.txt"),
          "utf-8"
        );
        check(recordedCalls.trim().split("\n").toSorted()).toEqual(
          calls.trim().split("\n").toSorted()
        );
      } else {
        await check(
          readFile(path.join(directory, "native-calls.txt"))
        ).rejects.toMatchObject({ code: "ENOENT" });
        if (hook === "pre-commit") {
          check(git("log", "-1", "--format=%s").stdout.toString().trim()).toBe(
            "Initial fixture"
          );
        } else {
          check(
            git("--git-dir", remote, "show-ref", "refs/heads/main").status
          ).toBe(1);
        }
      }
    }
  );
});
