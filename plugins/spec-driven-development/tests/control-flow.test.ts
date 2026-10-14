import path from "node:path";

import { expect, test } from "vitest";

import {
  matchesFilters,
  normalizeTag,
  parseFields,
  subjectString
} from "../skills/spec-driven-development/scripts/sdd/frontmatter.js";
import {
  extractCallPath,
  extractLinkTargets,
  resolveTargetPaths
} from "../skills/spec-driven-development/scripts/sdd/links.js";

test.each([
  [undefined, []],
  [null, []],
  ["", []],
  ["  ", []],
  [" item ", ["item"]],
  [0, ["0"]],
  [
    [" item ", "", 2],
    ["item", "2"]
  ]
])("normalizes tag input %j", (input, expected) => {
  expect(normalizeTag(input)).toEqual(expected);
});

test.each([
  [undefined, undefined],
  ["", undefined],
  [", ,", undefined],
  ["title, tags, ,status", ["title", "tag", "status"]]
])("parses field input %j", (input, expected) => {
  expect(parseFields(input)).toEqual(expected);
});

test.each([
  [{}, ""],
  [{ subject: null }, ""],
  [{ subject: {} }, ""],
  [{ subject: { name: "model" } }, "model@"],
  [{ subject: { version: "2" } }, "@2"],
  [{ subject: { name: "model", version: "2" } }, "model@2"]
])("renders subject input %j", (input, expected) => {
  expect(subjectString(input)).toBe(expected);
});

test.each([
  ["child/SPEC.md", "child/SPEC.md"],
  [{ path: "child/SPEC.md" }, "child/SPEC.md"],
  [{ path: 0 }, "0"],
  [{ path: null }, ""],
  [null, ""],
  [[], ""]
])("extracts call path input %j", (input, expected) => {
  expect(extractCallPath(input)).toBe(expected);
});

test("filter branches preserve tag handling, record precedence and short-circuiting", () => {
  expect(
    matchesFilters(
      { category: ["api", "ui"], status: "done", tag: ["domain"] },
      { status: "draft" },
      [
        ["tag", ["domain"]],
        ["category", ["api"]],
        ["status", ["done"]]
      ]
    )
  ).toBe(true);
  expect(matchesFilters({ tag: "domain" }, {}, [["tag", ["domain"]]])).toBe(
    false
  );
  expect(matchesFilters({}, { status: "draft" }, [["status", ["draft"]]])).toBe(
    true
  );
  const record = Object.defineProperty({ tag: [] }, "status", {
    get() {
      throw new Error("Later filters must not run after a mismatch");
    }
  });
  expect(
    matchesFilters(record, {}, [
      ["tag", ["domain"]],
      ["status", ["done"]]
    ])
  ).toBe(false);
});

test("target paths retain ordered candidates and exclude schemes and fragments", () => {
  const base = path.resolve("fixture/spec/module");
  const root = path.dirname(base);
  expect([...resolveTargetPaths("", base)]).toEqual([]);
  expect([...resolveTargetPaths("#fragment", base)]).toEqual([]);
  expect([
    ...resolveTargetPaths("https://example.invalid/SPEC.md", base)
  ]).toEqual([]);
  const absolute = path.resolve("fixture/absolute/SPEC.md");
  expect([...resolveTargetPaths(`${absolute}#fragment`, base)]).toEqual([
    absolute
  ]);
  expect([...resolveTargetPaths("spec/child/SPEC.md", base)]).toEqual([
    path.join(root, "child/SPEC.md")
  ]);
  expect([...resolveTargetPaths("child/SPEC.md", base)]).toEqual([
    path.join(base, "child/SPEC.md"),
    path.join(root, "child/SPEC.md")
  ]);
  expect([...resolveTargetPaths("../child/SPEC.md", base)]).toEqual([
    path.resolve(base, "../child/SPEC.md")
  ]);
});

test("link extraction keeps the first relative SPEC call and ignores unsupported entries", () => {
  const source = path.resolve("fixture/spec/module/RESEARCH.md");
  const absolute = path.resolve("fixture/spec/absolute/SPEC.md");
  expect(
    extractLinkTargets(
      {
        call: [
          " child/SPEC.md#first ",
          { path: "child/SPEC.md#duplicate" },
          "https://example.invalid/SPEC.md",
          absolute,
          "child/RESEARCH.md",
          null,
          "#fragment"
        ]
      },
      source
    )
  ).toEqual([
    {
      raw: "child/SPEC.md#first",
      resolved: path.resolve(path.dirname(source), "child/SPEC.md")
    }
  ]);
  expect(extractLinkTargets({ call: "child/SPEC.md" }, source)).toEqual([]);
});
