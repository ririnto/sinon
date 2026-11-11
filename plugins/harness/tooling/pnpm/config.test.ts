import { spawnSync } from "node:child_process";
import {
  copyFile,
  mkdir,
  mkdtemp,
  readdir,
  readFile,
  realpath,
  rm,
  symlink,
  writeFile
} from "node:fs/promises";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import path from "node:path";
import { pathToFileURL } from "node:url";

import { lint } from "markdownlint/sync";
import { describe, expect, test } from "vitest";
import { parseDocument } from "yaml";

import rootManifest from "../../../../package.json" with { type: "json" };
import { configureBaseline } from "../../scripts/configure.js";
import { runUltracite } from "../../tests/ultracite-fixture.js";
import tsconfig from "./tsconfig.fragment.json" with { type: "json" };

const profileDirectory = import.meta.dirname;
const resolvePackage = createRequire(import.meta.url).resolve;
const tscBin = path.join(
  path.dirname(resolvePackage("typescript/package.json")),
  "bin/tsc"
);
const markdownlintBin = path.join(
  path.dirname(resolvePackage("markdownlint-cli2")),
  "markdownlint-cli2-bin.mjs"
);
const nodeModulesDirectory = path.resolve(
  profileDirectory,
  "../../../../node_modules"
);
const emDash = "\u2014";
const packageStyleFixture = [
  'import { describe, expect, test } from "vitest";',
  "",
  'describe("suite", () => {',
  '  test("first", () => {',
  "    expect(1).toBe(1);",
  "  });",
  '  test("second", () => {',
  "    expect(2).toBe(2);",
  "  });",
  "});",
  "",
  "function withBlankLine() {",
  "  const value = 1;",
  "",
  "  return value;",
  "}",
  "",
  "function withComment() {",
  `  // Explain the return value ${emDash} across ranges`,
  `  const glyph = "${emDash}";`,
  "  return withBlankLine() + glyph.length;",
  "}",
  "",
  "withComment();"
].join("\n");

const preparePackageRoot = async (directory: string): Promise<void> => {
  await configureBaseline(directory, "node", "eligible");
  await symlink(
    nodeModulesDirectory,
    path.join(directory, "node_modules"),
    "dir"
  );
  await writeFile(
    path.join(directory, "tsconfig.json"),
    JSON.stringify({
      compilerOptions: {
        module: "esnext",
        moduleResolution: "bundler",
        noEmit: true,
        skipLibCheck: true,
        strict: true,
        target: "es2022"
      },
      include: ["**/*.ts", "**/*.tsx"]
    })
  );
};

/**
 * Runs a package script in a materialized fixture as a directly invoked pnpm.
 */
const runPackageScript = (directory: string, script: string) =>
  spawnSync("pnpm", ["run", script], {
    cwd: directory,
    env: {
      ...process.env,
      PATH: `${path.join(nodeModulesDirectory, ".bin")}${path.delimiter}${process.env.PATH}`
    }
  });

const trackedMarkdownFiles = (
  directory: string,
  pathspecs: readonly string[]
): string[] => {
  const tracked = spawnSync("git", ["ls-files", "--", ...pathspecs], {
    cwd: directory,
    encoding: "utf-8"
  });
  if (tracked.status !== 0 || tracked.stdout === null) {
    throw new Error(
      `git ls-files failed in ${directory} with status ${tracked.status}: ${tracked.stderr}`
    );
  }
  return tracked.stdout
    .split("\n")
    .filter((file) => file.endsWith(".md"))
    .map((file) => path.join(directory, file));
};

describe("pnpm profile configuration", () => {
  test.each([
    [
      "../../../../package.json",
      "run-p $(node --import tsx .github/scripts/pre-commit-scope.ts)",
      "run-s check"
    ],
    ["package.fragment.json", "run-s check", "run-s check"],
    [
      "../go/package.json",
      "run-s check go:lint go:format go:nilaway",
      "run-s check go:check"
    ],
    [
      "../gradle/package.json",
      "run-s check gradle:ktlintCheck",
      "run-s check gradle:check"
    ],
    [
      "../maven/package.json",
      "run-s check maven:lint",
      "run-s check maven:check"
    ],
    [
      "../rust/package.json",
      "run-s check rust:format rust:lint",
      "run-s check rust:check"
    ],
    [
      "../shell/package.json",
      "run-s check shell:check",
      "run-s check shell:check"
    ],
    ["../uv/package.json", "run-s check uv:check", "run-s check uv:check"]
  ])(
    "%s owns its hook sequences and receives hook delegation",
    async (manifestFile, preCommit, prePush) => {
      expect(
        JSON.parse(
          await readFile(path.resolve(profileDirectory, manifestFile), "utf-8")
        )
      ).toMatchObject({
        scripts: {
          check: "run-p check:*",
          fix: "run-p fix:*",
          "pre-commit": preCommit,
          "pre-push": prePush,
          prepare: "husky"
        }
      });
      const directory = await mkdtemp(
        path.join(tmpdir(), "sinon-hook-direction-")
      );
      try {
        await mkdir(path.join(directory, ".bin"));
        await writeFile(
          path.join(directory, ".bin/pnpm"),
          '#!/usr/bin/env sh\n# vim: set fileencoding=utf-8 :\nset -e\nprintf \'%s\\n\' "$@" > "$HARNESS_FIXTURE_TRACE"\nexit "$HARNESS_FIXTURE_EXIT"\n',
          { mode: 0o755 }
        );
        await Promise.all(
          ["pre-commit", "pre-push"].flatMap((hook) =>
            [0, 7].map(async (status) => {
              const trace = path.join(directory, `${hook}-${status}`);
              const result = spawnSync(
                "sh",
                [
                  path.resolve(
                    profileDirectory,
                    path.dirname(manifestFile),
                    ".husky",
                    hook
                  )
                ],
                {
                  cwd: directory,
                  encoding: "utf-8",
                  env: {
                    ...process.env,
                    HARNESS_FIXTURE_EXIT: String(status),
                    HARNESS_FIXTURE_TRACE: trace,
                    PATH: `${path.join(directory, ".bin")}${path.delimiter}${process.env.PATH}`
                  }
                }
              );
              expect(result.status, result.stdout + result.stderr).toBe(status);
              expect(await readFile(trace, "utf-8")).toBe(`run\n${hook}\n`);
            })
          )
        );
      } finally {
        await rm(directory, { force: true, recursive: true });
      }
    }
  );

  test("repository scripts preserve disjoint typed and untyped lint scopes with one config", async () => {
    const directory = await mkdtemp(path.join(tmpdir(), "sinon-root-lint-"));
    try {
      await preparePackageRoot(directory);
      await copyFile(
        path.resolve(profileDirectory, "../../../../oxlint.config.ts"),
        path.join(directory, "oxlint.config.ts")
      );
      const typedDirectory = path.join(
        directory,
        "plugins/harness/tooling/pnpm"
      );
      await mkdir(path.join(typedDirectory, "plugins"), { recursive: true });
      await copyFile(
        path.join(profileDirectory, "plugins/tsdoc-plugin.ts"),
        path.join(typedDirectory, "plugins/tsdoc-plugin.ts")
      );
      await writeFile(
        path.join(directory, "package.json"),
        JSON.stringify({ scripts: rootManifest.scripts, type: "module" })
      );
      await Promise.all(
        [
          path.join(directory, "ordinary.ts"),
          path.join(typedDirectory, "violation.ts")
        ].map(async (filename) => {
          await writeFile(
            filename,
            "export const undocumented = (): number => 1;\nvoid Promise.resolve();\n"
          );
        })
      );
      const formatted = runPackageScript(directory, "ultracite:fix");
      expect(
        formatted.status,
        formatted.stderr.toString() + formatted.stdout.toString()
      ).toBe(0);
      const result = runPackageScript(directory, "check:ultracite");
      const output = result.stderr.toString() + result.stdout.toString();
      expect(result.status, output).toBe(1);
      expect(output).toContain("plugins/harness/tooling/pnpm/violation.ts:");
      expect(output).toContain("tsdoc(require-export-tsdoc)");
      expect(output).toContain("typescript(no-floating-promises)");
      expect(output).not.toContain("ordinary.ts:");
      expect(output.match(/tsdoc\(require-export-tsdoc\)/gu)).toHaveLength(1);
    } finally {
      await rm(directory, { force: true, recursive: true });
    }
  }, 30_000);

  test("Harness guidance parses and uses body titles", async () => {
    const harnessRoot = path.resolve(profileDirectory, "../..");
    const files = trackedMarkdownFiles(harnessRoot, [
      "README.md",
      "docs",
      "skills"
    ]);
    expect(files.length).toBeGreaterThan(0);
    await Promise.all(
      files.map(async (file) => {
        const text = await readFile(file, "utf-8");
        const frontmatter = /^(?<block>---\n(?<source>[\s\S]*?)\n---\n)/u.exec(
          text
        );
        const block = frontmatter?.groups?.block;
        const source = frontmatter?.groups?.source;
        if (block !== undefined && source !== undefined) {
          const document = parseDocument(source, { uniqueKeys: true });
          expect(document.errors, file).toEqual([]);
          expect(document.has("title"), file).toBe(false);
          expect(/^# \S/mu.test(text.slice(block.length)), file).toBe(true);
        } else {
          expect(/^# /mu.test(text), file).toBe(true);
        }
      })
    );
  });

  test.each([
    path.resolve(profileDirectory, "../../../..", ".markdownlint-cli2.jsonc"),
    path.join(profileDirectory, ".markdownlint-cli2.jsonc")
  ])("Markdownlint title policy in %s", async (configurationPath) => {
    const parseConfiguration: (text: string) => {
      config: NonNullable<Parameters<typeof lint>[0]>["config"];
      gitignore: boolean;
    } = JSON.parse;
    const { config, gitignore } = parseConfiguration(
      await readFile(configurationPath, "utf-8")
    );
    expect(gitignore).toBe(true);
    const cases = [
      {
        rule: undefined,
        text: "---\ntitle: Example\n---\n\n## Section\n\nBody.\n"
      },
      { rule: undefined, text: "# Example\n\nBody.\n" },
      {
        rule: "MD025",
        text: "---\ntitle: Example\n---\n\n# Example\n\nBody.\n"
      },
      { rule: "MD041", text: "Body.\n" }
    ];
    for (const { text, rule } of cases) {
      const errors = lint({ config, strings: { sample: text } }).sample;
      expect(errors.map(({ ruleNames }) => ruleNames[0])).toEqual(
        rule === undefined ? [] : [rule]
      );
    }
  });

  test("Ultracite runs every installed policy together without duplicate diagnostics", async () => {
    const directory = await mkdtemp(path.join(tmpdir(), "sinon-profile-lint-"));
    try {
      await writeFile(path.join(directory, ".oxlintrc.json"), "{}\n");
      await configureBaseline(directory, "node", "eligible");
      await symlink(
        path.resolve(profileDirectory, "../../../../node_modules"),
        path.join(directory, "node_modules"),
        "dir"
      );
      await writeFile(
        path.join(directory, "sample.ts"),
        `${packageStyleFixture}\nexport function undocumented() { return 1; }\n`
      );
      const { diagnostics, status } = runUltracite(
        directory,
        "sample.ts",
        "check",
        path.join(directory, "oxlint.config.ts")
      );
      expect(status).toBe(1);
      expect(diagnostics.map(({ code }) => code)).toContain(
        "tsdoc(require-export-tsdoc)"
      );
      for (const code of [
        "function-spacing(no-blank-lines-in-functions)",
        "prose-style(no-em-dash)",
        "function-comments(no-inline-comments-in-functions)",
        "test-spacing(sibling-test-spacing)"
      ]) {
        expect(
          diagnostics.filter((diagnostic) => diagnostic.code === code)
        ).toHaveLength(1);
      }
    } finally {
      await rm(directory, { force: true, recursive: true });
    }
  });

  test("Ultracite composes the base with a framework preset and target rules", async () => {
    const directory = await mkdtemp(
      path.join(tmpdir(), "sinon-profile-compose-")
    );
    try {
      await writeFile(path.join(directory, ".oxlintrc.json"), "{}\n");
      await preparePackageRoot(directory);
      await writeFile(
        path.join(directory, "composed.config.ts"),
        [
          'import { defineConfig } from "oxlint";',
          'import react from "ultracite/oxlint/react";',
          'import base from "./oxlint.config.ts";',
          'export default defineConfig({ ...base, extends: [...(base.extends ?? []), react], rules: { ...base.rules, "eslint/no-console": "error" } });'
        ].join("\n")
      );
      await writeFile(
        path.join(directory, "sample.tsx"),
        'export function image() { return <img src="example.png" />; }\nconsole.log(image);\n'
      );
      const { diagnostics, status } = runUltracite(
        directory,
        "sample.tsx",
        "check",
        path.join(directory, "composed.config.ts")
      );
      expect(status).toBe(1);
      expect(diagnostics.map(({ code }) => code)).toEqual(
        expect.arrayContaining([
          "tsdoc(require-export-tsdoc)",
          "jsx-a11y(alt-text)",
          "eslint(no-console)"
        ])
      );
    } finally {
      await rm(directory, { force: true, recursive: true });
    }
  }, 30_000);

  test("materialized scripts discover nested style violations and ignore generated files", async () => {
    const directory = await mkdtemp(
      path.join(tmpdir(), "sinon-script-discovery-")
    );
    try {
      await preparePackageRoot(directory);
      await mkdir(path.join(directory, "src/nested"), { recursive: true });
      await mkdir(path.join(directory, "dist"));
      await writeFile(
        path.join(directory, "src/nested/style-fixture.ts"),
        packageStyleFixture
      );
      await writeFile(
        path.join(directory, "dist/ignored.ts"),
        packageStyleFixture
      );
      await writeFile(
        path.join(directory, "src/client.generated.ts"),
        packageStyleFixture
      );
      const result = runPackageScript(directory, "check:ultracite");
      const output = result.stderr.toString() + result.stdout.toString();
      expect(result.status, output).toBe(1);
      expect(output).toContain("src/nested/style-fixture.ts");
      expect(output).toContain("Remove blank lines from function bodies.");
      expect(output).toContain(
        "Replace the em dash with a comma, colon, or a separate sentence."
      );
      expect(output).toContain(
        "Use declaration documentation instead of explanatory comments."
      );
      expect(output).toContain(
        "Separate sibling tests and lifecycle registrations with one blank line."
      );
      expect(output).not.toContain("dist/ignored.ts");
      expect(output).not.toContain("src/client.generated.ts");
    } finally {
      await rm(directory, { force: true, recursive: true });
    }
  }, 90_000);

  test("materialized fix script formats once and retains diagnostic-only policies", async () => {
    const directory = await mkdtemp(path.join(tmpdir(), "sinon-script-fix-"));
    try {
      await preparePackageRoot(directory);
      await mkdir(path.join(directory, "src/nested"), { recursive: true });
      const fixture = path.join(directory, "src/nested/style-fixture.ts");
      await writeFile(
        fixture,
        [
          packageStyleFixture,
          "function formatterTarget(){return  1}",
          "formatterTarget();",
          'const formatterValues={firstLongPropertyName:"first value that keeps the object multiline",secondLongPropertyName:"second value that keeps the object multiline",};',
          "Object.keys(formatterValues);"
        ].join("\n")
      );
      await writeFile(
        path.join(directory, "README.md"),
        "# Markdown\n\n*   item\n"
      );
      const result = runPackageScript(directory, "fix:ultracite");
      const output = result.stderr.toString() + result.stdout.toString();
      expect(result.status, output).toBe(1);
      const formatterInvocations =
        output.match(/ultracite fix --type-aware/gu) ?? [];
      expect(formatterInvocations).toHaveLength(1);
      const formatterPosition = output.indexOf("ultracite fix --type-aware");
      const diagnosticPositions = [
        "Remove blank lines from function bodies.",
        "Replace the em dash with a comma, colon, or a separate sentence.",
        "Use declaration documentation instead of explanatory comments.",
        "Separate sibling tests and lifecycle registrations with one blank line."
      ].map((message) => output.indexOf(message));
      expect(formatterPosition).toBeGreaterThanOrEqual(0);
      expect(
        diagnosticPositions.every((position) => position > formatterPosition),
        output
      ).toBe(true);
      const fixed = await readFile(fixture, "utf-8");
      expect(fixed).toContain("function formatterTarget() {");
      expect(fixed).toContain("  return 1;");
      expect(fixed).not.toContain("function formatterTarget(){");
      expect(fixed).toContain(
        [
          "const formatterValues = {",
          '  firstLongPropertyName: "first value that keeps the object multiline",',
          '  secondLongPropertyName: "second value that keeps the object multiline"',
          "};"
        ].join("\n")
      );
      expect(await readFile(path.join(directory, "README.md"), "utf-8")).toBe(
        "# Markdown\n\n*   item\n"
      );
      expect(fixed).toContain(
        "function withBlankLine() {\n  const value = 1;\n\n  return value;"
      );
      expect(fixed).toContain("  // Explain the return value");
      expect(fixed).toContain('  });\n  test("second"');
    } finally {
      await rm(directory, { force: true, recursive: true });
    }
  }, 90_000);

  test("Ultracite keeps implied-eval diagnostics in root and materialized configs", async () => {
    const directory = await mkdtemp(path.join(tmpdir(), "sinon-implied-eval-"));
    try {
      await writeFile(path.join(directory, ".oxlintrc.json"), "{}\n");
      await preparePackageRoot(directory);
      await writeFile(
        path.join(directory, "sample.js"),
        'setTimeout("alert(1)", 0);\n'
      );
      for (const configuration of [
        path.resolve(profileDirectory, "../../../..", "oxlint.config.ts"),
        path.join(directory, "oxlint.config.ts")
      ]) {
        const { diagnostics, status } = runUltracite(
          directory,
          "sample.js",
          "check",
          configuration
        );
        expect(status).toBe(1);
        expect(diagnostics.map(({ code }) => code)).toContain(
          "eslint(no-implied-eval)"
        );
      }
    } finally {
      await rm(directory, { force: true, recursive: true });
    }
  }, 30_000);

  test("Markdownlint configuration resolves from an installed copy", async () => {
    const directory = await mkdtemp(path.join(tmpdir(), "sinon-profile-md-"));
    try {
      await configureBaseline(directory, "node", "eligible");
      await symlink(
        nodeModulesDirectory,
        path.join(directory, "node_modules"),
        "dir"
      );
      const schema = await realpath(
        path.join(
          path.dirname(resolvePackage("markdownlint-cli2")),
          "schema/markdownlint-cli2-config-schema.json"
        )
      );
      const parseConfiguration: (text: string) => { $schema: string } =
        JSON.parse;
      await Promise.all(
        [
          path.join(profileDirectory, ".markdownlint-cli2.jsonc"),
          path.join(directory, ".markdownlint-cli2.jsonc")
        ].map(async (configPath) => {
          const configuration = parseConfiguration(
            await readFile(configPath, "utf-8")
          );
          expect(
            await realpath(
              new URL(configuration.$schema, pathToFileURL(configPath))
            )
          ).toBe(schema);
        })
      );
      const installedRules = await readdir(path.join(directory, "rules"));
      expect(installedRules.toSorted()).toEqual([
        "markdown-prose.ts",
        "no-em-dash.ts",
        "no-relative-skill-links.ts",
        "no-sensitive-content.mjs",
        "table-separators.ts"
      ]);
      await mkdir(path.join(directory, "nested"));
      await writeFile(
        path.join(directory, "nested/.gitignore"),
        "ignored.md\n"
      );
      await writeFile(
        path.join(directory, "nested/ignored.md"),
        "# First\n\n# Second\n"
      );
      await writeFile(
        path.join(directory, "sample.md"),
        "# Sample\n\nFirst sentence. Second sentence.\n"
      );
      const ignored = spawnSync(process.execPath, [markdownlintBin], {
        cwd: directory
      });
      expect(ignored.status).toBe(0);
      await writeFile(
        path.join(directory, "sample.md"),
        "# Sample\n\nFirst sentence. Second sentence.\n\nAvoid – punctuation and [relative skills](../SKILL.md).\n\n| A |\n| -- |\n| 1 |\n\n──\n"
      );
      const result = spawnSync(process.execPath, [markdownlintBin], {
        cwd: directory
      });
      const output = result.stderr.toString() + result.stdout.toString();
      expect(result.status).toBe(1);
      expect(output).not.toContain("docs/sentence-per-line");
      expect(output).toContain("docs/no-relative-skill-links");
      expect(output).toContain("docs/table-separators");
      expect(output).toContain("search-replace");
    } finally {
      await rm(directory, { force: true, recursive: true });
    }
  });

  test("tsconfig fragment options compile under the installed TypeScript", async () => {
    const directory = await mkdtemp(path.join(tmpdir(), "sinon-profile-ts-"));
    try {
      await writeFile(
        path.join(directory, "tsconfig.json"),
        JSON.stringify({
          compilerOptions: {
            ...tsconfig.compilerOptions,
            module: "esnext",
            noEmit: true,
            strict: true,
            target: "es2022"
          },
          include: ["sample.ts"]
        })
      );
      await writeFile(
        path.join(directory, "sample.ts"),
        "export const ready = true;\n"
      );
      const result = spawnSync(process.execPath, [tscBin, "-p", directory]);
      expect(result.stdout.toString()).toBe("");
      expect(result.status).toBe(0);
    } finally {
      await rm(directory, { force: true, recursive: true });
    }
  });
});
