import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { expect, test } from "vitest";

import { runUltracite } from "../../../tests/ultracite-fixture.js";

const pluginDirectory = new URL("../plugins/", import.meta.url);

const writeRuleConfiguration = async (
  directory: string,
  pluginFile: string,
  ruleId: string
): Promise<void> => {
  await writeFile(
    path.join(directory, ".oxlintrc.json"),
    JSON.stringify({
      jsPlugins: [fileURLToPath(new URL(pluginFile, pluginDirectory))],
      rules: { [ruleId]: "error" }
    })
  );
};

test("blank-line checks preserve directive, comment, and literal gaps", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-blank-lines-"));
  try {
    await writeRuleConfiguration(
      directory,
      "no-blank-lines-in-functions.ts",
      "style/no-blank-lines-in-functions"
    );
    await writeFile(
      path.join(directory, "valid.ts"),
      "function valid() {\n'use strict';\n\n/**\n * Document the nested value.\n\n */\nconst text = `first\n\nlast`;\nreturn text;\n}\nvalid();"
    );
    await writeFile(
      path.join(directory, "invalid.ts"),
      [
        "function blank() {\n\nreturn 1;\n}",
        "function windows() {\r\n\r\nreturn 1;\r\n}",
        "function unicode() {\u2028\u2028return 1;\u2028}",
        "function nested() {\nconst inner = () => {\n// Explain the return value\nreturn 1;\n};\nreturn inner();\n}",
        "blank(); windows(); unicode(); nested();"
      ].join("\n")
    );
    expect(runUltracite(directory, "valid.ts", "fix").status).toBe(0);
    const valid = runUltracite(directory, "valid.ts");
    expect(valid.status).toBe(0);
    expect(
      valid.diagnostics.filter(
        ({ code }) => code === "style(no-blank-lines-in-functions)"
      )
    ).toEqual([]);
    const invalid = runUltracite(directory, "invalid.ts");
    expect(invalid.status).toBe(1);
    expect(
      invalid.diagnostics
        .filter(({ code }) => code?.startsWith("style("))
        .map(({ code }) => code)
    ).toEqual([
      "style(no-blank-lines-in-functions)",
      "style(no-blank-lines-in-functions)",
      "style(no-blank-lines-in-functions)"
    ]);
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
}, 30_000);

const commentFixtures = [
  {
    expected: 1,
    filename: "nested.ts",
    source:
      "function nested() {\nconst inner = () => {\n// Explain the return value\nreturn 1;\n};\nreturn inner();\n}\nnested();"
  },
  {
    expected: 0,
    filename: "directives.tsx",
    source: [
      "export function view(value: string) {",
      "// @ts-expect-error intentional mismatch",
      "const mismatch: number = value;",
      "/* oxlint-disable-next-line no-console */",
      "console.log(mismatch);",
      "return <div>{/* eslint-disable-next-line react/no-danger */}{value}</div>;",
      "}"
    ].join("\n")
  },
  {
    expected: 0,
    filename: "tool-directives.ts",
    source: [
      "function valid() {",
      "  // @ts-expect-error reason",
      "  // @ts-ignore",
      "  // eslint-disable-next-line no-console",
      "  // oxlint-disable-next-line no-console -- reason",
      "  // oxfmt-ignore",
      "  // v8 ignore next",
      "  return 1;",
      "}",
      "valid();"
    ].join("\n")
  },
  {
    expected: 5,
    filename: "comment-boundaries.ts",
    source: [
      "function explanatory() {",
      "  // explain the return value",
      "  return 1;",
      "}",
      "function fakeDirective() {",
      "  // @ts-expect-errors are discouraged",
      "  return 1;",
      "}",
      "function legalProse() {",
      "  // This comment discusses copyright.",
      "  /* The @license marker appears here as prose. */",
      "  return 1;",
      "}",
      "function legalNotices() {",
      "  /*",
      "   * Copyright 2026 Sinon contributors.",
      "   * SPDX-License-Identifier: MIT",
      "   */",
      "  /*! @license MIT */",
      "  /*! Copyright 2026 Sinon contributors. */",
      "  return 1;",
      "}",
      "function unattachedDocumentation() {",
      "  /**",
      "   * Document the return contract.",
      "   */",
      "  return 1;",
      "}",
      "explanatory();",
      "fakeDirective();",
      "legalProse();",
      "legalNotices();",
      "unattachedDocumentation();"
    ].join("\n")
  },
  {
    expected: 0,
    filename: "shebang.ts",
    source:
      '#!/usr/bin/env node\n/// <reference types="node" />\n/* #__PURE__ */\nconst result = work();\nconsume(result);\n'
  },
  {
    expected: 0,
    filename: "docs.ts",
    source: [
      "/** First declaration contract. */",
      "/** Declaration contract. */",
      "const value = 1;",
      "/* @license MIT */",
      "function valid() {",
      "// @ts-expect-error intentional fixture",
      "consume(value);",
      "}"
    ].join("\n")
  },
  {
    expected: 0,
    filename: "documented-object-method.ts",
    source: [
      "/**",
      " * Provide a service status query.",
      " */",
      "export const service = {",
      "  /**",
      "   * Return the current service status.",
      "   */",
      "  status() {",
      '    return "ready";',
      "  }",
      "};"
    ].join("\n")
  },
  {
    expected: 0,
    filename: "documented-object-function-property.ts",
    source: [
      "/**",
      " * Provide a callback status query.",
      " */",
      "export const callbackService = {",
      "  /**",
      "   * Return the current callback status.",
      "   */",
      '  status: () => "ready"',
      "};"
    ].join("\n")
  },
  {
    expected: 2,
    filename: "comments.tsx",
    source: [
      "export function view(value: string) {",
      "/* plain */",
      "return <div>{/* note */}{value}</div>;",
      "}"
    ].join("\n")
  },
  {
    expected: 2,
    filename: "plain.ts",
    source: [
      "// explanatory top-level comment",
      "function invalid() {",
      "/** Not declaration documentation. */",
      "return 1;",
      "}"
    ].join("\n")
  }
] as const;

test("inline-comment checks preserve directives and license notices", async () => {
  const directory = await mkdtemp(
    path.join(tmpdir(), "sinon-inline-comments-")
  );
  try {
    await writeRuleConfiguration(
      directory,
      "no-inline-comments-in-functions.ts",
      "style/no-inline-comments-in-functions"
    );
    await Promise.all(
      commentFixtures.map(({ filename, source }) =>
        writeFile(path.join(directory, filename), source)
      )
    );
    for (const { filename, expected } of commentFixtures) {
      if (expected === 0) {
        const result = runUltracite(directory, filename, "fix");
        expect(result.status, JSON.stringify(result.diagnostics)).toBe(0);
      }
      const { diagnostics, status } = runUltracite(directory, filename);
      const comments = diagnostics.filter(
        ({ code }) => code === "style(no-inline-comments-in-functions)"
      );
      expect(comments, filename).toHaveLength(expected);
      expect(status, filename).toBe(expected === 0 ? 0 : 1);
    }
    runUltracite(directory, "plain.ts", "fix");
    const fixed = await readFile(path.join(directory, "plain.ts"), "utf-8");
    expect(fixed).toContain("// explanatory top-level comment");
    expect(fixed).toContain("/** Not declaration documentation. */");
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
}, 60_000);

const registrationCases = [
  [
    "valid.ts",
    'import { describe as suite, test as example, beforeEach } from "vitest";\nsuite("group", () => {\nbeforeEach(() => {});\n\nexample("first", () => {});\n\nexample.skip("second", () => {});\n});',
    0,
    0
  ],
  [
    "missing.ts",
    'import { describe, test, afterEach } from "@jest/globals";\ndescribe("group", () => {\ntest("first", () => {});\nafterEach(() => {});\n});',
    1,
    0
  ],
  [
    "extra.ts",
    'import { test, afterAll } from "bun:test";\ntest("first", () => {});\n\n\nafterAll(() => {});',
    1,
    0
  ],
  [
    "shadow.ts",
    'import { describe, test } from "vitest";\nfunction ordinary(describe: Function) {\ndescribe("group", () => {\ntest("first", () => {});\n\ntest("second", () => {});\n});\n}',
    0,
    1
  ],
  [
    "callback.ts",
    'import { describe, test } from "vitest";\ndescribe("group", () => {\ntest("first", () => {\nfirst();\n\nsecond();\n});\n\ntest("second", () => {});\n});',
    0,
    1
  ],
  [
    "global.ts",
    'describe("group", () => {\ntest("first", () => {});\n\ntest("second", () => {});\n});',
    0,
    1
  ],
  [
    "dynamic.ts",
    'import { describe, test } from "vitest";\ndescribe.factory([[1]])("group", () => {\ntest("first", () => {});\n\ntest("second", () => {});\n});',
    0,
    1
  ],
  [
    "directive-gap.ts",
    'import { describe, test } from "vitest";\ndescribe("group", () => {\ntest("first", () => {});\n\n// @ts-expect-error intentional directive\ntest("second", () => {});\n});',
    0,
    0
  ],
  [
    "each.ts",
    'import { describe, test, beforeEach } from "vitest";\ndescribe.each([[1]])("group", () => {\nbeforeEach(() => {});\n\ntest.each([[1]])("first", () => {});\n\ntest("second", () => {});\n});',
    0,
    0
  ]
] as const;

type RegistrationCase = (typeof registrationCases)[number];

test.each([
  {
    expectedCount: (testCase: RegistrationCase) => testCase[2],
    pluginFile: "sibling-test-spacing.ts",
    ruleCode: "style(sibling-test-spacing)",
    ruleId: "style/sibling-test-spacing"
  },
  {
    expectedCount: (testCase: RegistrationCase) => testCase[3],
    pluginFile: "no-blank-lines-in-functions.ts",
    ruleCode: "style(no-blank-lines-in-functions)",
    ruleId: "style/no-blank-lines-in-functions"
  }
])(
  "$ruleCode preserves registration-boundary behavior",
  async ({ pluginFile, ruleId, ruleCode, expectedCount }) => {
    const directory = await mkdtemp(
      path.join(tmpdir(), "sinon-registration-style-")
    );
    try {
      await writeRuleConfiguration(directory, pluginFile, ruleId);
      await Promise.all(
        registrationCases.map(([filename, source]) =>
          writeFile(path.join(directory, filename), source)
        )
      );
      for (const testCase of registrationCases) {
        const { diagnostics } = runUltracite(directory, testCase[0]);
        expect(
          diagnostics
            .filter(({ code }) => code?.startsWith("style("))
            .map(({ code }) => code),
          testCase[0]
        ).toEqual(
          Array.from({ length: expectedCount(testCase) }, () => ruleCode)
        );
      }
    } finally {
      await rm(directory, { force: true, recursive: true });
    }
  },
  60_000
);
