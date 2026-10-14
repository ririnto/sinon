import { spawnSync } from "node:child_process";
import {
  cp,
  mkdtemp,
  readdir,
  readFile,
  rm,
  writeFile
} from "node:fs/promises";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import path from "node:path";

import { lint } from "markdownlint/sync";
import { describe, expect, test } from "vitest";

import oxfmtConfig from "./oxfmt.config.js";
import oxlintConfig from "./oxlint.config.js";

const profileDirectory = import.meta.dirname;
const resolvePackage = createRequire(import.meta.url).resolve;
const oxlintBin = path.join(
  path.dirname(resolvePackage("oxlint/package.json")),
  "bin/oxlint"
);
const tscBin = path.join(
  path.dirname(resolvePackage("typescript/package.json")),
  "bin/tsc"
);
const markdownlintBin = path.join(
  path.dirname(resolvePackage("markdownlint-cli2")),
  "markdownlint-cli2-bin.mjs"
);

describe("pnpm profile configuration", () => {
  test.each([
    path.resolve(profileDirectory, "../../../..", ".markdownlint-cli2.jsonc"),
    path.join(profileDirectory, ".markdownlint-cli2.jsonc")
  ])("Markdownlint title policy in %s", async (configurationPath) => {
    const { config } = JSON.parse(await readFile(configurationPath, "utf-8"));
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
  test("oxfmt configuration skips Markdown and trailing commas", () => {
    expect(oxfmtConfig.ignorePatterns).toContain("**/*.md");
    expect(oxfmtConfig.trailingComma).toBe("none");
  });
  test("oxlint configuration enables the profile plugin rules", () => {
    expect(oxlintConfig.jsPlugins).toEqual([
      "./plugins/style-plugin.ts",
      "./plugins/tsdoc-plugin.ts"
    ]);
    expect(oxlintConfig.rules).toMatchObject({
      "style/no-blank-lines-in-functions": "deny",
      "style/no-inline-comments-in-functions": "deny",
      "tsdoc/require-export-tsdoc": "deny"
    });
  });
  test("native Oxlint loads the profile configuration and its plugins", async () => {
    const directory = await mkdtemp(path.join(tmpdir(), "sinon-profile-lint-"));
    try {
      await writeFile(
        path.join(directory, "sample.ts"),
        "export function sample() {\n// explain\n\nreturn 1;\n}\n"
      );
      const result = spawnSync(
        process.execPath,
        [
          oxlintBin,
          "--config",
          path.join(profileDirectory, "oxlint.config.ts"),
          "--format",
          "json",
          path.join(directory, "sample.ts")
        ],
        { cwd: directory }
      );
      expect(result.error).toBeUndefined();
      const { diagnostics }: { diagnostics: { code?: string }[] } = JSON.parse(
        result.stdout.toString()
      );
      const codes = diagnostics.map(({ code }) => code);
      expect(codes).toContain("style(no-blank-lines-in-functions)");
      expect(codes).toContain("style(no-inline-comments-in-functions)");
      expect(codes).toContain("tsdoc(require-export-tsdoc)");
    } finally {
      await rm(directory, { force: true, recursive: true });
    }
  });
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
      const installedRules = await readdir(path.join(directory, "rules"));
      expect(installedRules.toSorted()).toEqual([
        "no-box-drawing.ts",
        "sentence-per-line.ts",
        "table-separators.ts"
      ]);
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
      expect(output).toContain("docs/no-box-drawing");
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
