import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test } from "vitest";

import {
  validateChangelogFile,
  validateDocument
} from "../skills/spec-driven-development/scripts/sdd/validation.js";
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
    `---\ntitle: Status Example\ndescription: Example.\nlast_updated: "2026-10-23"\nstatus: ${status}\ncall: []\n---\n`
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
    `---\ntitle: Status Example\ndescription: Example.\nlast_updated: "2026-10-23"\n${statusField}\ncall: []\n---\n`
  );

  const result = validateDocument(filePath, "spec");

  expect(result.passed).toBe(false);
  expect(result.errors.some((error) => error.includes("status"))).toBe(true);
});

test("validateChangelogFile rejects oldest-first dates without changing input", () => {
  const directory = mkdtempSync(path.join(tmpdir(), "sinon-sdd-changelog-"));
  onTestFinished(() => rmSync(directory, { force: true, recursive: true }));
  const filePath = path.join(directory, "CHANGELOG.md");
  writeFileSync(
    filePath,
    "# Changelog\n\n## 2026-01-01 - First\n\n## 2026-10-01 - Second\n"
  );
  expect(validateChangelogFile(filePath)).toEqual({
    errors: [`FAIL [${filePath}]: CHANGELOG.md entries must be newest first`],
    passed: false
  });
});

test("validateChangelogFile accepts newest-first dates and repeated dates", () => {
  const directory = mkdtempSync(path.join(tmpdir(), "sinon-sdd-changelog-"));
  onTestFinished(() => rmSync(directory, { force: true, recursive: true }));
  const filePath = path.join(directory, "CHANGELOG.md");
  writeFileSync(
    filePath,
    "# Changelog\n\n## 2026-10-01 - First\n\n## 2026-10-01 - Second\n\n## 2026-01-01 - Third\n"
  );
  expect(validateChangelogFile(filePath)).toEqual({ errors: [], passed: true });
});
