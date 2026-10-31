import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { expect, test } from "vitest";

import { runUltracite } from "../../../tests/ultracite-fixture.js";

const writeConfig = async (directory: string): Promise<void> => {
  await writeFile(
    path.join(directory, ".oxlintrc.json"),
    JSON.stringify({
      jsPlugins: [
        fileURLToPath(new URL("../plugins/tsdoc-plugin.ts", import.meta.url))
      ],
      rules: { "tsdoc/require-export-tsdoc": "error" }
    })
  );
};

test.each([false, true])(
  "Ultracite checks public methods on a directly exported class expression (documented=%s)",
  async (documented) => {
    const directory = await mkdtemp(
      path.join(tmpdir(), "sinon-tsdoc-exported-class-")
    );
    const doc = "/**\n * Provide the service.\n */\n";
    const memberDoc = documented
      ? "  /**\n   * Read the service.\n   */\n"
      : "";
    const filename = "service.ts";
    try {
      await writeConfig(directory);
      await writeFile(
        path.join(directory, filename),
        `${doc}export const Service = class {\n${memberDoc}  read() {\n    return this;\n  }\n};\n`
      );
      if (documented) {
        expect(runUltracite(directory, filename, "fix").status).toBe(0);
      }
      const result = runUltracite(directory, filename);
      expect(result.status).toBe(documented ? 0 : 1);
      expect(result.diagnostics.map(({ message }) => message)).toEqual(
        documented
          ? []
          : ['Missing TSDoc for exported public API method "read".']
      );
    } finally {
      await rm(directory, { force: true, recursive: true });
    }
  }
);

test("Ultracite checks TSDoc delimiter lines on exports and methods", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-tsdoc-"));
  try {
    await writeConfig(directory);
    await writeFile(
      path.join(directory, "valid.ts"),
      'const marker = "\u{1F600}";\n' +
        "/**\n * Parse input.\n */\nexport function parse() { return marker; }\n" +
        "/**\r\n * Store values.\r\n */\r\nexport class Store {\r\n" +
        "  /**\r\n   * Read the stored value.\r\n   */\r\n  read() { return this.#internal(); }\r\n  private hidden() {}\r\n  protected inherited() {}\r\n  #internal() {}\r\n}\r\n" +
        "/**\n * Store a value.\n */\n/**\n * Keep it immutable.\n */\nexport const adjacentDocumented = 1;\n"
    );
    await writeFile(
      path.join(directory, "invalid.ts"),
      "/** Parse input. */\nexport function parse() { return 1; }\n" +
        "/** Store values.\n */\nexport class Store {\n" +
        "  /**\n   * Read the stored value. */\n  read() { return 1; }\n}\n" +
        "export const undocumented = 1;\n" +
        "/**\n * Return a value.\n */ export function closingCode() { return 1; }\n" +
        "const value = 1; /**\n * Return the stored value.\n */\nexport function openingCode() { return value; }\n" +
        "/**\n * First.\n */\n/** Second. */\nexport const adjacentInline = 1;\n" +
        "/** First. */\n/**\n * Second.\n */\nexport const adjacentMultiline = 1;\n" +
        "/** An identifier. */\nexport type Identifier = string;\n"
    );
    await writeFile(
      path.join(directory, "source.js"),
      "export function parse() { return 1; }\n"
    );
    for (const [filename, exitCode, diagnosticCount] of [
      ["valid.ts", 0, 0],
      ["invalid.ts", 1, 8],
      ["source.js", 0, 0]
    ] as const) {
      if (exitCode === 0) {
        expect(runUltracite(directory, filename, "fix").status).toBe(0);
      }
      const { diagnostics, status } = runUltracite(directory, filename);
      expect(status).toBe(exitCode);
      expect(diagnostics).toHaveLength(diagnosticCount);
      if (filename === "invalid.ts") {
        expect(
          diagnostics.filter((diagnostic: { message: string }) =>
            diagnostic.message.includes("Use multiline TSDoc")
          )
        ).toHaveLength(8);
        expect(
          diagnostics.filter((diagnostic: { message: string }) =>
            diagnostic.message.includes("Missing TSDoc")
          )
        ).toHaveLength(0);
      }
    }
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
}, 30_000);

test("Ultracite checks declarations and members without requiring export wrapper comments", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-tsdoc-forms-"));
  const doc = "/**\n * Document it.\n */\n";
  try {
    await writeConfig(directory);
    const validStatements = [
      "export interface Shape { size: number; }",
      "export type Alias = string;",
      "export enum Mode { On }",
      `export namespace Space {\n${doc}export const inner = 1;\n}`,
      "export declare function overload(): void;",
      "export default 1;",
      "export * from './other.js';",
      "export * as ns from './other.js';",
      "export { a } from './other.js';",
      "export type { B } from './other.js';",
      "export { local };",
      `export abstract class Base {\n  ${doc}  abstract run(): void;\n  ${doc}  handler = () => 1;\n  ${doc}  static build = function build() { return 1; };\n  label = 'plain';\n  private secret = () => 1;\n}`
    ].map((line) => `${doc}${line}`);
    await writeFile(
      path.join(directory, "valid.ts"),
      `${validStatements.join("\n")}\nconst local = 1;\n`
    );
    await writeFile(
      path.join(directory, "invalid.ts"),
      [
        "export function parse() { return 1; }",
        "export interface Shape { size: number; }",
        "export type Alias = string;",
        "export enum Mode { On }",
        "export namespace Space { export const inner = 1; }",
        "export declare function overload(): void;",
        "export default { value: 1 };",
        "export * from './other.js';",
        "export { a } from './other.js';",
        "const local = 1;\nexport { local };",
        `${doc}export abstract class Base {\n  abstract run(): void;\n  handler = () => 1;\n  static build = function build() { return 1; };\n}`
      ].join("\n")
    );
    await writeFile(
      path.join(directory, "assignment.ts"),
      "const Base = 1;\nexport = Base;\n"
    );
    await writeFile(
      path.join(directory, "default-function.ts"),
      "export default function () { return 1; }\n"
    );
    await writeFile(
      path.join(directory, "default-class.ts"),
      `${doc}export default class { read() { return 1; } }\n`
    );
    expect(runUltracite(directory, "valid.ts", "fix").status).toBe(0);
    const valid = runUltracite(directory, "valid.ts");
    expect(valid.diagnostics).toEqual([]);
    expect(valid.status).toBe(0);
    const invalid = runUltracite(directory, "invalid.ts");
    expect(invalid.status).toBe(1);
    expect(invalid.diagnostics.map(({ message }) => message)).toEqual([
      'Missing TSDoc for exported public API function "parse".',
      'Missing TSDoc for exported public API interface "Shape".',
      'Missing TSDoc for exported public API enum "Mode".',
      'Missing TSDoc for exported public API namespace "Space".',
      'Missing TSDoc for exported public API function "overload".',
      'Missing TSDoc for exported public API method "run".',
      'Missing TSDoc for exported public API property "handler".',
      'Missing TSDoc for exported public API property "build".'
    ]);
    expect(
      runUltracite(directory, "assignment.ts").diagnostics.map(
        ({ message }) => message
      )
    ).toEqual([]);
    expect(
      runUltracite(directory, "default-function.ts").diagnostics.map(
        ({ message }) => message
      )
    ).toEqual([
      'Missing TSDoc for exported public API function "[anonymous]".'
    ]);
    expect(
      runUltracite(directory, "default-class.ts").diagnostics.map(
        ({ message }) => message
      )
    ).toEqual(['Missing TSDoc for exported public API method "read".']);
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
}, 30_000);

test("Ultracite checks each indirectly exported variable in a shared declaration", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-tsdoc-bindings-"));
  try {
    await writeConfig(directory);
    await writeFile(
      path.join(directory, "source.ts"),
      "const label = 1, compute = () => 42;\n" +
        "/**\n * Expose the label.\n */\nexport { label };\n" +
        "export { compute };\nexport { compute as calculate };\n"
    );
    const result = runUltracite(directory, "source.ts");
    expect(result.status).toBe(1);
    expect(result.diagnostics.map(({ message }) => message)).toEqual([
      'Missing TSDoc for exported public API variable "compute".'
    ]);
    await writeFile(
      path.join(directory, "documented.ts"),
      "/**\n * Expose the value and calculation.\n */\n" +
        "const label = 1, compute = () => 42;\nexport { label };\nexport { compute };\n"
    );
    expect(runUltracite(directory, "documented.ts").diagnostics).toEqual([]);
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
}, 30_000);

test.each(
  ["ts", "tsx", "mts", "cts"].flatMap((extension) =>
    [
      "class Service",
      "const Service = class",
      "const _Other = class { run() {} }, Service = class Implementation"
    ].map((declaration) => ({ declaration, extension }))
  )
)(
  "Ultracite checks indirectly exported $declaration members in .$extension",
  async ({ declaration, extension }) => {
    const directory = await mkdtemp(
      path.join(tmpdir(), "sinon-tsdoc-indirect-")
    );
    const doc = "/**\n * Document it.\n */\n";
    try {
      await writeConfig(directory);
      const fixtures = [
        "export { Service };",
        "export { Service as PublicService };",
        "export default Service;",
        `export { Service };\n${doc}export default Service;`
      ].flatMap((statement, index) =>
        [false, true].map((documented) => ({
          documented,
          filename: `source-${index}-${documented}.${extension}`,
          source: `${doc}${declaration} {\n${documented ? doc : ""}run() { return this.#internal(); }\nprivate hidden() {}\nprotected inherited() {}\n#internal() {}\n};\nconst _Helper = class { run() {} };\n${statement}\n`
        }))
      );
      await Promise.all(
        fixtures.map(async ({ filename, source }) => {
          await writeFile(path.join(directory, filename), source);
        })
      );
      for (const { documented, filename } of fixtures) {
        if (documented) {
          expect(runUltracite(directory, filename, "fix").status).toBe(0);
        }
        const result = runUltracite(directory, filename);
        expect(result.status).toBe(documented ? 0 : 1);
        expect(result.diagnostics.map(({ message }) => message)).toEqual(
          documented
            ? []
            : ['Missing TSDoc for exported public API method "run".']
        );
      }
      await writeFile(
        path.join(directory, `direct.${extension}`),
        "export class Service { run() {} }\n"
      );
      expect(
        runUltracite(directory, `direct.${extension}`).diagnostics
      ).toHaveLength(2);
      await writeFile(
        path.join(directory, `indirect.${extension}`),
        `class Service {}\nexport { Service };\nexport default Service;\n`
      );
      expect(
        runUltracite(directory, `indirect.${extension}`).diagnostics.map(
          ({ message }) => message
        )
      ).toEqual(['Missing TSDoc for exported public API class "Service".']);
      await writeFile(
        path.join(directory, `indirect-values.${extension}`),
        "function lookup() {}\ntype Key = string;\nconst value = 1;\nconst load = () => 1;\nexport { lookup, Key, value, load };\nexport { lookup as find };\n"
      );
      expect(
        runUltracite(directory, `indirect-values.${extension}`).diagnostics.map(
          ({ message }) => message
        )
      ).toEqual([
        'Missing TSDoc for exported public API function "lookup".',
        'Missing TSDoc for exported public API variable "load".'
      ]);
    } finally {
      await rm(directory, { force: true, recursive: true });
    }
  },
  30_000
);
