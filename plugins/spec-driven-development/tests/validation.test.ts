import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test } from "vitest";

import { validateDocument } from "../skills/spec-driven-development/scripts/sdd/validation.js";
import { repositoryPaths } from "./test-support/paths.js";

test("validateDocument marks a valid spec as passed", () => {
  expect(
    validateDocument(`${repositoryPaths.sddFixtureRoot}/domain/SPEC.md`, "spec")
      .passed
  ).toBe(true);
});

test("validateDocument marks a valid research document as passed", () => {
  expect(
    validateDocument(
      `${repositoryPaths.sddFixtureRoot}/research/library/demo-lib/RESEARCH.md`,
      "research"
    ).passed
  ).toBe(true);
});

test.each(["", "# ", "## Not a title", "```\n# Not a title\n```"])(
  "validateDocument rejects a missing or invalid body title",
  (heading) => {
    const directory = mkdtempSync(path.join(tmpdir(), "sinon-sdd-title-"));
    onTestFinished(() => rmSync(directory, { force: true, recursive: true }));
    const filePath = path.join(directory, "CONTRACT.md");
    writeFileSync(
      filePath,
      `---\ndescription: Example\nlast_updated: '2026-01-01'\n---\n\n${heading}\n`
    );
    expect(validateDocument(filePath, "contract").passed).toBe(false);
  }
);

test("validateDocument rejects a frontmatter title even with a body title", () => {
  const directory = mkdtempSync(path.join(tmpdir(), "sinon-sdd-title-"));
  onTestFinished(() => rmSync(directory, { force: true, recursive: true }));
  const filePath = path.join(directory, "CONTRACT.md");
  writeFileSync(
    filePath,
    "---\ntitle: Old\ndescription: Example\nlast_updated: '2026-01-01'\n---\n\n# New\n"
  );
  expect(validateDocument(filePath, "contract").passed).toBe(false);
});

test.each([
  "draft",
  "review",
  "approved",
  "wip",
  "implemented",
  "deprecated",
  "superseded",
  "removed"
])("validateDocument accepts the %s SPEC status", (status) => {
  const directory = mkdtempSync(path.join(tmpdir(), "sinon-sdd-status-"));
  onTestFinished(() => rmSync(directory, { force: true, recursive: true }));
  const filePath = path.join(directory, "SPEC.md");
  writeFileSync(
    filePath,
    `---\ndescription: Example.\nlast_updated: "2026-10-23"\nstatus: ${status}\ncall: []\n---\n\n# Status Example\n`
  );

  expect(validateDocument(filePath, "spec")).toEqual({
    errors: [],
    passed: true
  });
});

test.each([
  ["number", "status: 1"],
  ["null", "status: null"],
  ["list", "status: [draft]"],
  ["mapping", "status: {value: draft}"],
  ["missing", ""],
  ["unknown string", "status: unknown"],
  ["quoted numeric string", 'status: "1"']
])("validateDocument rejects a %s SPEC status", (_kind, statusField) => {
  const directory = mkdtempSync(path.join(tmpdir(), "sinon-sdd-status-"));
  onTestFinished(() => rmSync(directory, { force: true, recursive: true }));
  const filePath = path.join(directory, "SPEC.md");
  writeFileSync(
    filePath,
    `---\ndescription: Example.\nlast_updated: "2026-10-23"\n${statusField}\ncall: []\n---\n\n# Status Example\n`
  );

  const result = validateDocument(filePath, "spec");

  expect(result.passed).toBe(false);
  expect(result.errors.some((error) => error.includes("status"))).toBe(true);
});
