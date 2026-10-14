import { spawnSync } from "node:child_process";
import {
  cp,
  mkdir,
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
import { parseDocument } from "yaml";

import { runUltracite } from "../../tests/ultracite-fixture.js";
import oxfmtConfig from "./oxfmt.config.js";
import oxlintConfig from "./oxlint.config.js";

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
        const frontmatter = /^---\n(?<source>[\s\S]*?)\n---\n/u.exec(text);
        const source = frontmatter?.groups?.source;
        if (frontmatter && source) {
          const document = parseDocument(source, { uniqueKeys: true });
          expect(document.errors, file).toEqual([]);
          expect(document.toJS().title, file).toBeTypeOf("string");
          expect(document.toJS().title.trim(), file).not.toBe("");
          expect(/^# /mu.test(text.slice(frontmatter[0].length)), file).toBe(
            false
          );
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
  test("Ultracite loads the profile configuration and its plugins", async () => {
    const directory = await mkdtemp(path.join(tmpdir(), "sinon-profile-lint-"));
    try {
      await writeFile(path.join(directory, ".oxlintrc.json"), "{}\n");
      await writeFile(
        path.join(directory, "sample.ts"),
        "export function sample() {\n// explain\n\nreturn 1;\n}\n"
      );
      const { diagnostics, status } = runUltracite(
        directory,
        "sample.ts",
        "check",
        path.join(profileDirectory, "oxlint.config.ts")
      );
      expect(status).toBe(1);
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
