import { writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { test } from "vitest";

import {
  createCommandFixture,
  waitForFixtureTasks
} from "../../../tests/fixture-command.ts";
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

test.for([false, true])(
  "Ultracite accepts public methods on a directly exported class expression (documented=%s)",
  async (documented, { expect }) => {
    await using fixture = await createCommandFixture(
      "sinon-tsdoc-exported-class-"
    );
    const { directory } = fixture;
    const doc = "/**\n * Provide the service.\n */\n";
    const memberDoc = documented
      ? "  /**\n   * Read the service.\n   */\n"
      : "";
    const filename = "service.ts";
    await writeConfig(directory);
    await writeFile(
      path.join(directory, filename),
      `${doc}export const Service = class {\n${memberDoc}  read() {\n    return this;\n  }\n};\n`
    );
    if (documented) {
      const formatted = await runUltracite(fixture, filename, "fix");
      expect(formatted.status).toBe(0);
    }
    const result = await runUltracite(fixture, filename);
    expect(result.status).toBe(0);
    expect(result.diagnostics).toEqual([]);
  }
);

test.concurrent("Ultracite checks TSDoc delimiter lines on exports and methods", async ({
  expect
}) => {
  await using fixture = await createCommandFixture("sinon-tsdoc-");
  const { directory } = fixture;
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
  await waitForFixtureTasks(
    (
      [
        ["valid.ts", 0, 0],
        ["invalid.ts", 1, 8],
        ["source.js", 0, 0]
      ] as const
    ).map(async ([filename, exitCode, diagnosticCount]) => {
      if (exitCode === 0) {
        const formatted = await runUltracite(fixture, filename, "fix");
        expect(formatted.status).toBe(0);
      }
      const { diagnostics, status } = await runUltracite(fixture, filename);
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
    })
  );
}, 30_000);

test.concurrent("Ultracite checks declarations and members without requiring export wrapper comments", async ({
  expect
}) => {
  await using fixture = await createCommandFixture("sinon-tsdoc-forms-");
  const { directory } = fixture;
  const doc = "/**\n * Document it.\n */\n";
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
    "export default function () {\n  return 1;\n}\n"
  );
  await writeFile(
    path.join(directory, "default-class.ts"),
    `${doc}export default class {\n  read() {\n    return 1;\n  }\n}\n`
  );
  const formatted = await runUltracite(fixture, "valid.ts", "fix");
  expect(formatted.status).toBe(0);
  const valid = await runUltracite(fixture, "valid.ts");
  expect(valid.diagnostics).toEqual([]);
  expect(valid.status).toBe(0);
  const invalid = await runUltracite(fixture, "invalid.ts");
  expect(invalid.status).toBe(1);
  expect(invalid.diagnostics.map(({ message }) => message)).toEqual([
    'Missing TSDoc for exported public API interface "Shape".',
    'Missing TSDoc for exported public API enum "Mode".',
    'Missing TSDoc for exported public API namespace "Space".'
  ]);
  const assignment = await runUltracite(fixture, "assignment.ts");
  expect(assignment.diagnostics.map(({ message }) => message)).toEqual([]);
  await waitForFixtureTasks(
    ["default-function.ts", "default-class.ts"].map(async (filename) => {
      const { diagnostics, status } = await runUltracite(fixture, filename);
      expect(diagnostics, filename).toEqual([]);
      expect(status, filename).toBe(0);
    })
  );
}, 30_000);

test.concurrent("Ultracite checks each indirectly exported variable in a shared declaration", async ({
  expect
}) => {
  await using fixture = await createCommandFixture("sinon-tsdoc-bindings-");
  const { directory } = fixture;
  await writeConfig(directory);
  await writeFile(
    path.join(directory, "source.ts"),
    "const label = 1, compute = () => 42, Service = class {};\n" +
      "/**\n * Expose the label.\n */\nexport { label };\n" +
      "export { compute };\nexport { compute as calculate, Service };\n"
  );
  const result = await runUltracite(fixture, "source.ts");
  expect(result.status).toBe(1);
  expect(result.diagnostics.map(({ message }) => message)).toEqual([
    'Missing TSDoc for exported public API variable "Service".'
  ]);
  await writeFile(
    path.join(directory, "documented.ts"),
    "/**\n * Expose the value and calculation.\n */\n" +
      "const label = 1, compute = () => 42;\nexport { label };\nexport { compute };\n"
  );
  const documented = await runUltracite(fixture, "documented.ts");
  expect(documented.diagnostics).toEqual([]);
}, 30_000);

test.concurrent.for(
  ["ts", "tsx", "mts", "cts"].flatMap((extension) =>
    [
      "class Service",
      "const Service = class",
      "const _Other = class { run() {} }, Service = class Implementation"
    ].map((declaration) => ({ declaration, extension }))
  )
)(
  "Ultracite checks indirectly exported $declaration members in .$extension",
  { timeout: 30_000 },
  async ({ declaration, extension }, { expect }) => {
    await using fixture = await createCommandFixture("sinon-tsdoc-indirect-");
    const { directory } = fixture;
    const doc = "/**\n * Document it.\n */\n";
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
    const undocumentedFixtures = fixtures.filter(
      ({ documented }) => !documented
    );
    const documentedFilenames = fixtures
      .filter(({ documented }) => documented)
      .map(({ filename }) => filename);
    await waitForFixtureTasks(
      fixtures.map(async ({ filename, source }) => {
        await writeFile(path.join(directory, filename), source);
      })
    );
    const directFilename = `direct.${extension}`;
    const indirectFilename = `indirect.${extension}`;
    const indirectValuesFilename = `indirect-values.${extension}`;
    await writeFile(
      path.join(directory, directFilename),
      "export class Service { run() {} }\n"
    );
    await writeFile(
      path.join(directory, indirectFilename),
      `class Service {}\nexport { Service };\nexport default Service;\n`
    );
    await writeFile(
      path.join(directory, indirectValuesFilename),
      "function lookup() {}\ntype Key = string;\nconst value = 1;\nconst load = () => 1;\nexport { lookup, Key, value, load };\nexport { lookup as find };\n"
    );
    const fix = await runUltracite(fixture, documentedFilenames, "fix");
    expect(fix.status).toBe(0);
    const documentedResult = await runUltracite(fixture, documentedFilenames);
    expect(documentedResult.status).toBe(0);
    expect(documentedResult.diagnostics).toEqual([]);
    const otherFilenames = [
      ...undocumentedFixtures.map(({ filename }) => filename),
      directFilename,
      indirectFilename,
      indirectValuesFilename
    ];
    const otherResult = await runUltracite(fixture, otherFilenames);
    expect(otherResult.status).toBe(1);
    expect(
      otherResult.diagnostics.filter(
        ({ filename }) => !otherFilenames.includes(filename ?? "")
      )
    ).toEqual([]);
    for (const { filename } of undocumentedFixtures) {
      expect(
        otherResult.diagnostics
          .filter((diagnostic) => diagnostic.filename === filename)
          .map(({ message }) => message)
      ).toEqual([]);
    }
    expect(
      otherResult.diagnostics.filter(
        (diagnostic) => diagnostic.filename === directFilename
      )
    ).toHaveLength(1);
    expect(
      otherResult.diagnostics
        .filter((diagnostic) => diagnostic.filename === indirectFilename)
        .map(({ message }) => message)
    ).toEqual(['Missing TSDoc for exported public API class "Service".']);
    expect(
      otherResult.diagnostics
        .filter((diagnostic) => diagnostic.filename === indirectValuesFilename)
        .map(({ message }) => message)
    ).toEqual([]);
  }
);
