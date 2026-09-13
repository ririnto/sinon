import { expect, test } from "bun:test";

import {
  matchesFilters,
  matchesKind
} from "../../../plugins/spec-driven-development/skills/spec-driven-development/scripts/sdd/frontmatter.js";

test("matchesKind rejects a mismatched document kind", () => {
  expect(matchesKind("input/SPEC.md", "research")).toBe(false);
});

test("matchesFilters accepts a matching scalar value", () => {
  expect(
    matchesFilters({ status: "implemented" }, {}, [["status", ["implemented"]]])
  ).toBe(true);
});

test("matchesFilters rejects a non-matching scalar value", () => {
  expect(
    matchesFilters({ status: "draft" }, {}, [["status", ["implemented"]]])
  ).toBe(false);
});

test("matchesFilters accepts one matching tag", () => {
  expect(
    matchesFilters({ tag: ["ingest", "domain"] }, {}, [["tag", ["ingest"]]])
  ).toBe(true);
});
