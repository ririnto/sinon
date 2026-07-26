import { expect, test } from "bun:test";

import { validateDocument } from "../../../plugins/spec-driven-development/skills/spec-driven-development/scripts/sdd/validation.js";
import { repositoryPaths } from "../../test-support/paths.js";

test("validateDocument marks a valid spec as passed", () => {
  const result = validateDocument(
    `${repositoryPaths.sddFixtureRoot}/domain/SPEC.md`,
    "spec"
  );
  expect(result.passed).toBe(true);
});

test("validateDocument marks a valid research document as passed", () => {
  const result = validateDocument(
    `${repositoryPaths.sddFixtureRoot}/research/library/demo-lib/RESEARCH.md`,
    "research"
  );
  expect(result.passed).toBe(true);
});
