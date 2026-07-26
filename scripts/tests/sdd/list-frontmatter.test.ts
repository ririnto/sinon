import { expect, test } from "bun:test";

import {
  matchesFilters,
  matchesKind
} from "../../../plugins/spec-driven-development/skills/spec-driven-development/scripts/sdd/frontmatter.js";

test("matchesKind rejects a mismatched document kind", () => {
  const matched = matchesKind("input/SPEC.md", "research");
  expect(matched).toBe(false);
});

test("matchesFilters accepts a matching scalar value", () => {
  const matched = matchesFilters({ status: "implemented" }, {}, [
    ["status", ["implemented"]]
  ]);
  expect(matched).toBe(true);
});

test("matchesFilters rejects a non-matching scalar value", () => {
  const matched = matchesFilters({ status: "draft" }, {}, [
    ["status", ["implemented"]]
  ]);
  expect(matched).toBe(false);
});

test("matchesFilters accepts one matching tag", () => {
  const matched = matchesFilters({ tag: ["ingest", "domain"] }, {}, [
    ["tag", ["ingest"]]
  ]);
  expect(matched).toBe(true);
});
