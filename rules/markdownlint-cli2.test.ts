import { spawnSync } from "node:child_process";
import {
  cp,
  mkdtemp,
  mkdir,
  readFile,
  rm,
  symlink,
  writeFile
} from "node:fs/promises";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, test } from "vitest";

const repositoryRoot = path.resolve(import.meta.dirname, "..");
const markdownlintBin = path.join(
  path.dirname(createRequire(import.meta.url).resolve("markdownlint-cli2")),
  "markdownlint-cli2-bin.mjs"
);

const runMarkdownlint = (directory: string, ...files: string[]) => {
  const result = spawnSync(process.execPath, [markdownlintBin, ...files], {
    cwd: directory,
    encoding: "utf-8",
    timeout: 15_000
  });
  if (result.error) {
    throw result.error;
  }
  return { output: result.stderr + result.stdout, status: result.status };
};

test("root Markdownlint entry points load canonical rules and preserve scope", async () => {
  const directory = await mkdtemp(
    path.join(tmpdir(), "sinon-root-markdownlint-")
  );
  try {
    await Promise.all([
      cp(
        path.join(repositoryRoot, ".markdownlint-cli2.jsonc"),
        path.join(directory, ".markdownlint-cli2.jsonc")
      ),
      cp(
        path.join(repositoryRoot, ".gitignore"),
        path.join(directory, ".gitignore")
      ),
      symlink(
        path.join(repositoryRoot, "node_modules"),
        path.join(directory, "node_modules"),
        "dir"
      ),
      symlink(
        path.join(repositoryRoot, "rules"),
        path.join(directory, "rules"),
        "dir"
      )
    ]);
    await writeFile(
      path.join(directory, "ignored.local.md"),
      "# Ignored\n\nA forbidden box \u2500 character.\n"
    );
    await writeFile(
      path.join(directory, "included.md"),
      "# Included\n\nFirst sentence. Second sentence.\n"
    );
    const ignored = runMarkdownlint(directory, "**/*.md");
    expect(ignored.status).toBe(0);
    await Promise.all([
      rm(path.join(directory, "ignored.local.md")),
      rm(path.join(directory, "included.md"))
    ]);
    const harnessDirectory = path.join(directory, "plugins/harness");
    const otherDirectory = path.join(
      directory,
      "plugins/spec-driven-development"
    );
    await Promise.all([
      mkdir(harnessDirectory, { recursive: true }),
      mkdir(otherDirectory, { recursive: true })
    ]);
    await Promise.all([
      writeFile(
        path.join(harnessDirectory, "rules.md"),
        "# Rule loading\n\nFirst sentence. Second sentence.\n\nA box \u2500 character.\n\n| A |\n| ---- |\n| 1 |\n"
      ),
      writeFile(
        path.join(harnessDirectory, "scoped.md"),
        "# Harness scope\n\nAn em dash \u2014 and a [skill](../skills/example/SKILL.md).\n"
      ),
      writeFile(
        path.join(otherDirectory, "scoped.md"),
        "# Other plugin\n\nAn em dash \u2014 and a [skill](../skills/example/SKILL.md).\n"
      )
    ]);
    const generalRules = runMarkdownlint(directory, "plugins/harness/rules.md");
    expect(generalRules.status).toBe(1);
    for (const rule of ["search-replace", "docs/table-separators"]) {
      expect(generalRules.output).toContain(rule);
    }
    await rm(path.join(harnessDirectory, "rules.md"));
    const harnessScope = runMarkdownlint(
      directory,
      "plugins/harness/scoped.md"
    );
    expect(harnessScope.status).toBe(1);
    expect(harnessScope.output).toContain("docs/no-em-dash");
    expect(harnessScope.output).toContain("docs/no-relative-skill-links");
    await rm(path.join(harnessDirectory, "scoped.md"));
    const skillDirectory = path.join(otherDirectory, "skills/example");
    const fixtureDirectory = path.join(
      directory,
      "fixtures/plugins/vendor/skills/example"
    );
    await Promise.all([
      mkdir(skillDirectory, { recursive: true }),
      mkdir(fixtureDirectory, { recursive: true })
    ]);
    const skillFile = path.join(skillDirectory, "SKILL.md");
    const metadata =
      "---\nname: example\ndescription: &summary |\n  A skill with name: in its prose.\nmetadata:\n  summary: *summary\n---\n\n# Example\n";
    await Promise.all([
      writeFile(skillFile, metadata),
      writeFile(path.join(fixtureDirectory, "SKILL.md"), "# Local fixture\n")
    ]);
    const otherScope = runMarkdownlint(
      directory,
      "plugins/spec-driven-development/scoped.md",
      "fixtures/plugins/vendor/skills/example/SKILL.md",
      path.relative(directory, skillFile)
    );
    expect(otherScope.status, otherScope.output).toBe(0);
    await Promise.all(
      Object.entries({
        duplicate: metadata.replace(
          "name: example",
          "name: example\nname: duplicate"
        ),
        alias: metadata.replace("*summary", "*missing")
      }).map(async ([kind, broken]) => {
        const invalidDirectory = path.join(otherDirectory, "skills", kind);
        await mkdir(invalidDirectory);
        const file = path.join(invalidDirectory, "SKILL.md");
        await writeFile(file, broken);
        const invalid = runMarkdownlint(
          directory,
          "--fix",
          path.relative(directory, file)
        );
        expect(invalid.status, invalid.output).toBe(1);
        expect(await readFile(file, "utf-8")).toBe(broken);
      })
    );
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
}, 30_000);

const boxes = [
  "# Box coverage",
  "",
  "  \u{1F600} \u2500\u2501 and \u257F",
  "",
  "`\u257F`",
  "",
  "<!-- -->\u2500 \u257F",
  "",
  "<!-- --> \u2500",
  "",
  "<!-- note",
  "end -->\u2500 \u257F",
  "",
  "<!-- \u2500 -->",
  "",
  "<!-- markdownlint-disable-next-line docs/no-box-drawing -->",
  "\u2500",
  "",
  "<!-- markdownlint-disable-next-line search-replace -->",
  "\u257F",
  "",
  "```text",
  ...Array.from({ length: 128 }, (_, index) =>
    String.fromCodePoint(0x25_00 + index)
  ),
  "ASCII +-- | and \u24FF\u2580",
  "```",
  ""
].join("\r\n");

const rangeLocations = Array.from({ length: 128 }, (_, index) => [
  index + 23,
  1
]);

test("upstream box drawing configuration preserves Unicode coverage, columns and no fixes", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-upstream-boxes-"));
  try {
    await Promise.all([
      cp(
        path.join(repositoryRoot, ".markdownlint-cli2.jsonc"),
        path.join(directory, ".markdownlint-cli2.jsonc")
      ),
      cp(
        path.join(repositoryRoot, ".gitignore"),
        path.join(directory, ".gitignore")
      ),
      symlink(
        path.join(repositoryRoot, "rules"),
        path.join(directory, "rules"),
        "dir"
      ),
      symlink(
        path.join(repositoryRoot, "node_modules"),
        path.join(directory, "node_modules"),
        "dir"
      ),
      writeFile(path.join(directory, "boxes.md"), boxes)
    ]);
    const result = runMarkdownlint(directory, "--fix", "boxes.md");
    expect(result.status, result.output).toBe(1);
    const diagnostics = result.output
      .split("\n")
      .filter((line) => line.startsWith("boxes.md:"));
    expect(
      diagnostics.map((line) => line.split(" error search-replace ")[0])
    ).toEqual(
      [[3, 6], [5, 2], [9, 10], [17, 1], ...rangeLocations].map(
        ([line, column]) => `boxes.md:${line}:${column}`
      )
    );
    expect(diagnostics[0]).toBe(
      "boxes.md:3:6 error search-replace Custom rule [no-box-drawing: Use ASCII tree markers such as +-- and | instead.] [Context: \"column: 6 text:'\u2500'\"]"
    );
    expect(await readFile(path.join(directory, "boxes.md"), "utf-8")).toBe(
      boxes
    );
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
}, 30_000);

test("a copied local box drawing rule preserves its diagnostics through CLI2", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-copied-boxes-"));
  try {
    await Promise.all([
      cp(
        path.join(repositoryRoot, "rules/no-box-drawing.mjs"),
        path.join(directory, "no-box-drawing.mjs")
      ),
      writeFile(
        path.join(directory, ".markdownlint-cli2.jsonc"),
        JSON.stringify({ customRules: ["no-box-drawing.mjs"] })
      ),
      writeFile(path.join(directory, "boxes.md"), boxes),
      writeFile(
        path.join(directory, "valid.md"),
        "# Valid\n\nASCII +-- | \u24FF \u2580\n"
      )
    ]);
    const valid = runMarkdownlint(directory, "valid.md");
    expect(valid.status, valid.output).toBe(0);
    const invalid = runMarkdownlint(directory, "--fix", "boxes.md");
    expect(invalid.status, invalid.output).toBe(1);
    const diagnostics = invalid.output
      .split("\n")
      .filter((line) => line.startsWith("boxes.md:"));
    expect(
      diagnostics.map((line) => line.split(" error docs/no-box-drawing ")[0])
    ).toEqual(
      [
        [3, 6],
        [5, 2],
        [7, 9],
        [9, 10],
        [12, 8],
        [20, 1],
        ...rangeLocations
      ].map(([line, column]) => `boxes.md:${line}:${column}`)
    );
    expect(diagnostics[0]).toBe(
      'boxes.md:3:6 error docs/no-box-drawing Unicode box drawing characters are not allowed in Markdown [Use ASCII tree markers such as +-- and | instead.] [Context: "\u{1F600} \u2500\u2501 and \u257F"]'
    );
    expect(await readFile(path.join(directory, "boxes.md"), "utf-8")).toBe(
      boxes
    );
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
});
