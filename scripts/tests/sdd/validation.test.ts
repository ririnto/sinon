import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test } from "vitest";

import {
  validateChangelogFile,
  validateDocument
} from "../../../plugins/spec-driven-development/skills/spec-driven-development/scripts/sdd/validation.js";
import { repositoryPaths } from "../../test-support/paths.js";

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
