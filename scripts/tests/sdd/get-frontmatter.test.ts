import { expect, test } from "bun:test";

import {
  extractFrontmatterFromText,
  parseFields
} from "../../../plugins/spec-driven-development/skills/spec-driven-development/scripts/sdd/frontmatter.js";

test("extractFrontmatterFromText returns the closing line number", () => {
  const block = extractFrontmatterFromText("---\ntitle: value\n---\nbody");
  expect(block?.endLine).toBe(3);
});

test("extractFrontmatterFromText returns undefined without a header", () => {
  const block = extractFrontmatterFromText("title: value\n---");
  expect(block).toBeUndefined();
});

test("extractFrontmatterFromText rejects unterminated frontmatter", () => {
  expect(() => extractFrontmatterFromText("---\ntitle: value")).toThrow();
});

test("parseFields returns undefined for empty input", () => {
  const fields = parseFields("");
  expect(fields).toBeUndefined();
});
