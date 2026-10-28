import { spawnSync } from "node:child_process";
import {
  cp,
  mkdir,
  mkdtemp,
  readdir,
  readFile,
  rm,
  symlink,
  writeFile
} from "node:fs/promises";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import path from "node:path";

import { lint } from "markdownlint/sync";
import { describe, expect, test } from "vitest";
import { parseDocument } from "yaml";

import { configureBaseline } from "../../scripts/configure.js";
import { runUltracite } from "../../tests/ultracite-fixture.js";

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
const stylePasses = [
  {
    configurationFile: "oxlint.style-no-blank-lines-in-functions.config.ts",
    pluginFile: "no-blank-lines-in-functions.ts",
    ruleCode: "style(no-blank-lines-in-functions)"
  },
  {
    configurationFile: "oxlint.style-no-inline-comments-in-functions.config.ts",
    pluginFile: "no-inline-comments-in-functions.ts",
    ruleCode: "style(no-inline-comments-in-functions)"
  },
  {
    configurationFile: "oxlint.style-sibling-test-spacing.config.ts",
    pluginFile: "sibling-test-spacing.ts",
    ruleCode: "style(sibling-test-spacing)"
  }
] as const;
const nodeModulesDirectory = path.resolve(
  profileDirectory,
  "../../../../node_modules"
);
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
  "  // Explain the return value",
  "  return withBlankLine();",
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
 * Drops Corepack markers so the fixture's packageManager pin governs the run instead of the caller's Corepack dispatch.
 */
const runPackageScript = (directory: string, script: string) => {
  const environment = Object.fromEntries(
    Object.entries(process.env).filter(([name]) => !name.startsWith("COREPACK"))
  );
  return spawnSync("pnpm", ["run", script], {
    cwd: directory,
    env: {
      ...environment,
      PATH: `${path.join(nodeModulesDirectory, ".bin")}${path.delimiter}${environment.PATH}`
    }
  });
};

const markdownFiles = async (directory: string): Promise<string[]> => {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = await Promise.all(
    entries.map((entry) =>
      entry.isDirectory()
        ? markdownFiles(path.join(directory, entry.name))
        : Promise.resolve([path.join(directory, entry.name)])
    )
  );
  return files.flat().filter((file) => file.endsWith(".md"));
};

describe("pnpm profile configuration", () => {
  test.each([
    ["../../../../package.json", "run-s check", "run-s check"],
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
      const { scripts } = JSON.parse(
        await readFile(path.resolve(profileDirectory, manifestFile), "utf-8")
      );
      expect(scripts).toMatchObject({
        check: "run-p check:*",
        fix: "run-p fix:*",
        "pre-commit": preCommit,
        "pre-push": prePush,
        prepare: "husky"
      });
      const directory = await mkdtemp(
        path.join(tmpdir(), "sinon-hook-direction-")
      );
      try {
        await mkdir(path.join(directory, ".bin"));
        await writeFile(
          path.join(directory, ".bin/pnpm"),
          '#!/usr/bin/env sh\n# -*- coding: utf-8 -*-\nset -e\nprintf \'%s\\n\' "$@" > "$HARNESS_FIXTURE_TRACE"\nexit "$HARNESS_FIXTURE_EXIT"\n',
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

  test.each(
    [
      {
        commands: [
          "node --import tsx .claude-plugin/marketplace/generate.ts --check",
          "vitest run .claude-plugin/marketplace/generate.test.ts"
        ],
        gate: "check:marketplace"
      },
      {
        commands: [
          "ultracite check",
          "ultracite check .claude-plugin/marketplace"
        ],
        gate: "check:ultracite"
      }
    ].flatMap((entry) => [-1, 0, 1].map((failure) => ({ ...entry, failure })))
  )(
    "$gate preserves tool order with failure $failure",
    async ({ commands, failure, gate }) => {
      const { scripts }: { scripts: Record<string, string> } = JSON.parse(
        await readFile(
          path.resolve(profileDirectory, "../../../../package.json"),
          "utf-8"
        )
      );
      const [runner, ...tasks] = scripts[gate].split(" ");
      expect(runner).toBe("run-s");
      expect(tasks.map((task) => scripts[task])).toEqual(commands);
      const directory = await mkdtemp(
        path.join(tmpdir(), "sinon-script-order-")
      );
      try {
        await symlink(
          path.resolve(profileDirectory, "../../../../node_modules"),
          path.join(directory, "node_modules"),
          "dir"
        );
        await writeFile(
          path.join(directory, "record.cjs"),
          [
            "require('node:fs').appendFileSync('calls', process.argv[2] + '\\n');",
            "process.exit(process.argv[3] === 'fail' ? 1 : 0);"
          ].join("\n")
        );
        await writeFile(
          path.join(directory, "package.json"),
          JSON.stringify({
            scripts: {
              check: scripts.check,
              [gate]: scripts[gate],
              ...Object.fromEntries(
                tasks.map((task, index) => [
                  task,
                  `node record.cjs ${task}${index === failure ? " fail" : ""}`
                ])
              )
            }
          })
        );
        const result = spawnSync("pnpm", ["run", "check"], {
          cwd: directory,
          encoding: "utf-8"
        });
        expect(result.status, result.stdout + result.stderr).toBe(
          failure < 0 ? 0 : 1
        );
        expect(await readFile(path.join(directory, "calls"), "utf-8")).toBe(
          `${tasks.slice(0, failure < 0 ? tasks.length : failure + 1).join("\n")}\n`
        );
      } finally {
        await rm(directory, { force: true, recursive: true });
      }
    }
  );

  test("Harness guidance parses and follows the frontmatter title policy", async () => {
    const harnessRoot = path.resolve(profileDirectory, "../..");
    const [documents, skills] = await Promise.all([
      markdownFiles(path.join(harnessRoot, "docs")),
      markdownFiles(path.join(harnessRoot, "skills"))
    ]);
    const files = [
      path.join(harnessRoot, "README.md"),
      ...documents,
      ...skills
    ];
    expect(files.length).toBeGreaterThan(0);
    await Promise.all(
      files.map(async (file) => {
        const text = await readFile(file, "utf-8");
        const frontmatter = /^(?<block>---\n(?<source>[\s\S]*?)\n---\n)/u.exec(
          text
        );
        const block = frontmatter?.groups?.block;
        const source = frontmatter?.groups?.source;
        if (block && source) {
          const document = parseDocument(source, { uniqueKeys: true });
          expect(document.errors, file).toEqual([]);
          expect(document.toJS().title, file).toBeTypeOf("string");
          expect(document.toJS().title.trim(), file).not.toBe("");
          expect(/^# /mu.test(text.slice(block.length)), file).toBe(false);
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
    const { config, gitignore } = JSON.parse(
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
        rule ? [rule] : []
      );
    }
  });

  test("Ultracite loads the base configuration without style rules", async () => {
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
        `${packageStyleFixture}\nexport const undocumented = 1;\n`
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
      expect(
        diagnostics.filter(({ code }) => code?.startsWith("style("))
      ).toEqual([]);
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
        'export const image = <img src="example.png" />;\nconsole.log(image);\n'
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

  test.each(stylePasses)(
    "Ultracite loads copied $configurationFile with peer plugins absent",
    async (stylePass) => {
      const directory = await mkdtemp(
        path.join(tmpdir(), "sinon-style-profile-lint-")
      );
      try {
        await writeFile(path.join(directory, ".oxlintrc.json"), "{}\n");
        await configureBaseline(directory, "node", "eligible");
        await symlink(
          path.resolve(profileDirectory, "../../../../node_modules"),
          path.join(directory, "node_modules"),
          "dir"
        );
        await Promise.all(
          stylePasses
            .filter(({ pluginFile }) => pluginFile !== stylePass.pluginFile)
            .map(({ pluginFile }) =>
              rm(path.join(directory, "plugins", pluginFile))
            )
        );
        await rm(path.join(directory, "plugins/no-direct-use-effect.ts"));
        const pluginFiles = await readdir(path.join(directory, "plugins"));
        expect(pluginFiles.toSorted()).toEqual(
          [stylePass.pluginFile, "tsdoc-plugin.ts"].toSorted()
        );
        await writeFile(path.join(directory, "sample.ts"), packageStyleFixture);
        const { diagnostics, status } = runUltracite(
          directory,
          "sample.ts",
          "check",
          path.join(directory, stylePass.configurationFile)
        );
        expect(status).toBe(1);
        expect(
          diagnostics.filter(({ code }) => code?.startsWith("style("))
        ).toEqual([
          expect.objectContaining({
            code: stylePass.ruleCode,
            severity: "error"
          })
        ]);
      } finally {
        await rm(directory, { force: true, recursive: true });
      }
    },
    30_000
  );

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

  test("materialized fix script formats once before read-only style checks", async () => {
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

  test("materialized scripts ignore inherited Corepack markers", async () => {
    const directory = await mkdtemp(path.join(tmpdir(), "sinon-corepack-"));
    const corepackRoot = process.env.COREPACK_ROOT;
    process.env.COREPACK_ROOT = "/nonexistent-corepack-root";
    try {
      await preparePackageRoot(directory);
      await writeFile(
        path.join(directory, "README.md"),
        "# Target\n\nReady.\n"
      );
      const result = runPackageScript(directory, "check:markdownlint-cli2");
      expect(
        result.status,
        result.stderr.toString() + result.stdout.toString()
      ).toBe(0);
    } finally {
      if (corepackRoot === undefined) {
        delete process.env.COREPACK_ROOT;
      } else {
        process.env.COREPACK_ROOT = corepackRoot;
      }
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
        path.join(directory, "oxlint.config.ts"),
        ...stylePasses.map(({ configurationFile }) =>
          path.join(directory, configurationFile)
        )
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
      await cp(
        path.join(profileDirectory, ".markdownlint-cli2.jsonc"),
        path.join(directory, ".markdownlint-cli2.jsonc")
      );
      await cp(
        path.join(profileDirectory, "rules"),
        path.join(directory, "rules"),
        {
          filter: (source) => !source.endsWith(".test.ts"),
          recursive: true
        }
      );
      await symlink(
        nodeModulesDirectory,
        path.join(directory, "node_modules"),
        "dir"
      );
      const installedRules = await readdir(path.join(directory, "rules"));
      expect(installedRules.toSorted()).toEqual([
        "no-sensitive-content.mjs",
        "sentence-per-line.ts",
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
      const ignored = spawnSync(process.execPath, [markdownlintBin], {
        cwd: directory
      });
      expect(ignored.status).toBe(0);
      await writeFile(
        path.join(directory, "sample.md"),
        "# Sample\n\nFirst sentence. Second sentence.\n\n| A |\n| -- |\n| 1 |\n\n──\n"
      );
      const result = spawnSync(process.execPath, [markdownlintBin], {
        cwd: directory
      });
      const output = result.stderr.toString() + result.stdout.toString();
      expect(result.status).toBe(1);
      expect(output).toContain("docs/sentence-per-line");
      expect(output).toContain("docs/table-separators");
      expect(output).toContain("search-replace");
    } finally {
      await rm(directory, { force: true, recursive: true });
    }
  });

  test("tsconfig fragment options compile under the installed TypeScript", async () => {
    const directory = await mkdtemp(path.join(tmpdir(), "sinon-profile-ts-"));
    try {
      const { compilerOptions } = JSON.parse(
        await readFile(
          path.join(profileDirectory, "tsconfig.fragment.json"),
          "utf-8"
        )
      );
      await writeFile(
        path.join(directory, "tsconfig.json"),
        JSON.stringify({
          compilerOptions: {
            ...compilerOptions,
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
