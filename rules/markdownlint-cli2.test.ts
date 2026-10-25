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
      "# Included\n\nOne sentence.\n"
    );
    const ignored = runMarkdownlint(directory, "**/*.md");
    expect(ignored.status).toBe(0);
    expect(ignored.output).toContain("Linting: 1 file");
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
    for (const rule of [
      "search-replace",
      "docs/sentence-per-line",
      "docs/table-separators"
    ]) {
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
    const otherScope = runMarkdownlint(
      directory,
      "plugins/spec-driven-development/scoped.md"
    );
    expect(otherScope.status, otherScope.output).toBe(0);
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
}, 30_000);

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
      )
    ]);
    const boxes = [
      "# Box coverage",
      "",
      "```text",
      "Plain box: ─━ and ╿",
      "Emoji \u{1F600} ─",
      ...Array.from({ length: 128 }, (_, index) =>
        String.fromCodePoint(0x25_00 + index)
      ),
      "ASCII +-- | and ⓿▀",
      "```",
      ""
    ].join("\n");
    await writeFile(path.join(directory, "boxes.md"), boxes);
    const boxResult = runMarkdownlint(directory, "boxes.md");
    expect(boxResult.status).toBe(1);
    expect(
      boxResult.output
        .split("\n")
        .filter((line) => line.includes("search-replace"))
    ).toHaveLength(130);
    expect(boxResult.output).toContain("boxes.md:4:12");
    expect(boxResult.output).toContain("boxes.md:5:10");
    const fixed = runMarkdownlint(directory, "--fix", "boxes.md");
    expect(fixed.status).toBe(1);
    expect(await readFile(path.join(directory, "boxes.md"), "utf-8")).toBe(
      boxes
    );
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
}, 30_000);
