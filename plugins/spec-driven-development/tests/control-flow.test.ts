import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test, vi } from "vitest";

import { main } from "../skills/spec-driven-development/scripts/sdd/cli.js";

test.each([
  ["child/SPEC.md", ["child/SPEC.md#first", "../child/SPEC.md"]],
  ["spec/child/SPEC.md", ["../child/SPEC.md"]],
  ["../child/SPEC.md", ["../child/SPEC.md"]],
  ["absolute#fragment", ["../child/SPEC.md"]],
  ["#fragment", []],
  ["https://example.invalid/SPEC.md", []]
] as const)(
  "list-frontmatter resolves inbound query %s and excludes unsupported calls",
  (query, expectedLinks) => {
    const directory = mkdtempSync(path.join(tmpdir(), "sinon-sdd-links-"));
    onTestFinished(() => rmSync(directory, { force: true, recursive: true }));
    const specRoot = path.join(directory, "spec");
    const moduleDirectory = path.join(specRoot, "module");
    mkdirSync(moduleDirectory, { recursive: true });
    const source = path.join(moduleDirectory, "RESEARCH.md");
    const absolute = path.join(specRoot, "child/SPEC.md");
    const target =
      query === "absolute#fragment" ? `${absolute}#fragment` : query;
    writeFileSync(
      path.join(moduleDirectory, "SPEC.md"),
      '---\ncall: "child/SPEC.md"\n---\n'
    );
    writeFileSync(
      source,
      `---\n${JSON.stringify({
        call: [
          " child/SPEC.md#first ",
          { path: "child/SPEC.md#duplicate" },
          { path: "../child/SPEC.md" },
          "https://example.invalid/SPEC.md",
          absolute,
          "child/RESEARCH.md",
          { path: 0 },
          { path: null },
          null,
          [],
          "#fragment"
        ],
        title: "Caller"
      })}\n---\n`
    );
    const output = vi.spyOn(console, "log").mockImplementation(vi.fn());
    const errors = vi.spyOn(console, "error").mockImplementation(vi.fn());
    onTestFinished(() => {
      vi.restoreAllMocks();
    });
    expect(
      main(["list-frontmatter", moduleDirectory, "--inbound-of", target])
    ).toBe(0);
    expect(output.mock.calls).toEqual([
      [
        [
          "target\tsource\traw_link",
          ...expectedLinks.map((link) => `${target}\t${source}\t${link}`)
        ].join("\n")
      ]
    ]);
    expect(errors).not.toHaveBeenCalled();
  }
);
