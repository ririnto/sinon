import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test, vi } from "vitest";

import { main } from "../skills/spec-driven-development/scripts/sdd/cli.js";

test.each([
  [
    [],
    { description: "Hidden", status: "draft", tag: ["one"], title: "Value" }
  ],
  [
    ["--fields", ""],
    { description: "Hidden", status: "draft", tag: ["one"], title: "Value" }
  ],
  [
    ["--fields", ", ,"],
    { description: "Hidden", status: "draft", tag: ["one"], title: "Value" }
  ],
  [
    ["--fields", "title, tags, ,status"],
    { status: "draft", tag: ["one"], title: "Value" }
  ]
] as const)(
  "get-frontmatter selects fields with options %j and reports the header boundary",
  (options, expected) => {
    const directory = mkdtempSync(path.join(tmpdir(), "sinon-sdd-fields-"));
    onTestFinished(() => rmSync(directory, { force: true, recursive: true }));
    const file = path.join(directory, "SPEC.md");
    writeFileSync(
      file,
      "---\ntitle: Value\ntag: [one]\nstatus: draft\ndescription: Hidden\n---\nbody: ignored\n"
    );
    const output = vi.spyOn(console, "log").mockImplementation(vi.fn());
    const errors = vi.spyOn(console, "error").mockImplementation(vi.fn());
    onTestFinished(() => {
      vi.restoreAllMocks();
    });
    expect(main(["get-frontmatter", "spec", file, ...options])).toBe(0);
    expect(output).toHaveBeenCalledTimes(1);
    expect(JSON.parse(String(output.mock.calls[0]?.[0]))).toEqual({
      ...expected,
      file,
      frontmatter_end_line: 6,
      kind: "spec"
    });
    expect(errors).not.toHaveBeenCalled();
  }
);

test.each([
  ["missing", "title: value\n---"],
  ["unterminated", "---\ntitle: value"]
])("get-frontmatter rejects a %s header without output", (_kind, content) => {
  const directory = mkdtempSync(path.join(tmpdir(), "sinon-sdd-header-"));
  onTestFinished(() => rmSync(directory, { force: true, recursive: true }));
  const file = path.join(directory, "SPEC.md");
  writeFileSync(file, content);
  const output = vi.spyOn(console, "log").mockImplementation(vi.fn());
  const errors = vi.spyOn(console, "error").mockImplementation(vi.fn());
  onTestFinished(() => {
    vi.restoreAllMocks();
  });
  expect(main(["get-frontmatter", "spec", file])).toBe(1);
  expect(output).not.toHaveBeenCalled();
  expect(errors).toHaveBeenCalledTimes(1);
  expect(errors.mock.calls.flat().join("\n")).toContain(file);
  expect(errors.mock.calls.flat().join("\n")).toContain("frontmatter");
});
