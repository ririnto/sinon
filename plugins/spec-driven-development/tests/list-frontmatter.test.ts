import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test, vi } from "vitest";

import { main } from "../skills/spec-driven-development/scripts/sdd/cli.js";

test.each([
  ["tag", "", []],
  ["tag", "tag: null", []],
  ["tag", 'tag: ""', []],
  ["tag", 'tag: "  "', []],
  ["tag", 'tag: " item "', ["item"]],
  ["tag", "tag: 0", ["0"]],
  ["tag", 'tag: [" item ", "", 2]', ["item", "2"]],
  ["subject", "", ""],
  ["subject", "subject: null", ""],
  ["subject", "subject: {}", ""],
  ["subject", "subject: {name: model}", "model@"],
  ["subject", 'subject: {version: "2"}', "@2"],
  ["subject", 'subject: {name: model, version: "2"}', "model@2"]
] as const)(
  "list-frontmatter renders %s from %j",
  (field, frontmatter, expected) => {
    const directory = mkdtempSync(path.join(tmpdir(), "sinon-sdd-record-"));
    onTestFinished(() => rmSync(directory, { force: true, recursive: true }));
    const file = path.join(directory, "SPEC.md");
    writeFileSync(file, `---\ntitle: Example\n${frontmatter}\n---\n`);
    const output = vi.spyOn(console, "log").mockImplementation(vi.fn());
    const errors = vi.spyOn(console, "error").mockImplementation(vi.fn());
    onTestFinished(() => {
      vi.restoreAllMocks();
    });
    expect(
      main(["list-frontmatter", directory, "--jsonl", "--fields", field])
    ).toBe(0);
    expect(output).toHaveBeenCalledTimes(1);
    expect(JSON.parse(String(output.mock.calls[0]?.[0]))).toEqual({
      [field]: expected,
      file
    });
    expect(errors).not.toHaveBeenCalled();
  }
);

test.each([
  [["--kind", "research"], [{ name: "RESEARCH.md", title: "Research" }]],
  [["--filter", "status=implemented"], [{ name: "SPEC.md", title: "Ingest" }]],
  [["--filter", "status=removed"], []],
  [
    ["--tag", "domain"],
    [
      { name: "RESEARCH.md", title: "Research" },
      { name: "SPEC.md", title: "Ingest" }
    ]
  ],
  [["--tag", "missing"], []],
  [["--filter", "category=api"], [{ name: "SPEC.md", title: "Ingest" }]],
  [
    [
      "--tag",
      "domain",
      "--filter",
      "category=api",
      "--filter",
      "status=implemented"
    ],
    [{ name: "SPEC.md", title: "Ingest" }]
  ],
  [
    ["--tag", "domain", "--filter", "category=api", "--filter", "status=draft"],
    []
  ]
] as const)("list-frontmatter applies selection %j", (options, expected) => {
  const directory = mkdtempSync(path.join(tmpdir(), "sinon-sdd-selection-"));
  onTestFinished(() => rmSync(directory, { force: true, recursive: true }));
  writeFileSync(
    path.join(directory, "SPEC.md"),
    "---\ntitle: Ingest\nstatus: implemented\ntag: [ingest, domain]\ncategory: [api, ui]\n---\n"
  );
  writeFileSync(
    path.join(directory, "RESEARCH.md"),
    '---\ntitle: Research\nstatus: draft\ntag: " domain "\ncategory: cli\n---\n'
  );
  const output = vi.spyOn(console, "log").mockImplementation(vi.fn());
  const errors = vi.spyOn(console, "error").mockImplementation(vi.fn());
  onTestFinished(() => {
    vi.restoreAllMocks();
  });
  expect(
    main([
      "list-frontmatter",
      directory,
      "--jsonl",
      "--fields",
      "title",
      ...options
    ])
  ).toBe(0);
  expect(output).toHaveBeenCalledTimes(1);
  expect(
    String(output.mock.calls[0]?.[0])
      .split("\n")
      .filter(Boolean)
      .map((row) => JSON.parse(row))
  ).toEqual(
    expected.map(({ name, title }) => ({
      file: path.join(directory, name),
      title
    }))
  );
  expect(errors).not.toHaveBeenCalled();
});
