import { expect, test } from "bun:test";

import {
  extractFrontmatterFromText,
  parseFields
} from "../../../plugins/spec-driven-development/skills/spec-driven-development/scripts/sdd/frontmatter.js";

test("extractFrontmatterFromText returns the closing line number", () => {
  expect(
    extractFrontmatterFromText("---\ntitle: value\n---\nbody")?.endLine
  ).toBe(3);
});

test("extractFrontmatterFromText returns undefined without a header", () => {
  expect(extractFrontmatterFromText("title: value\n---")).toBeUndefined();
});

test("extractFrontmatterFromText rejects unterminated frontmatter", () => {
  expect(() => extractFrontmatterFromText("---\ntitle: value")).toThrow(
    "Unterminated YAML frontmatter"
  );
});

test("parseFields returns undefined for empty input", () => {
  expect(parseFields("")).toBeUndefined();
});
