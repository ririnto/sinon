import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test, vi } from "vitest";

import { main } from "../skills/spec-driven-development/scripts/sdd/cli.js";

test("mixed generated document trees report every failure in the aggregate", () => {
  const root = mkdtempSync(path.join(tmpdir(), "sinon-validation-property-"));
  onTestFinished(() => {
    vi.restoreAllMocks();
    rmSync(root, { force: true, recursive: true });
  });
  const output = vi.spyOn(console, "log").mockImplementation(vi.fn());
  const errors = vi.spyOn(console, "error").mockImplementation(vi.fn());
  const documents = [
    ["SPEC.md", "status: draft\ncall: []\n", "status: invalid\ncall: []\n"],
    [
      "RESEARCH.md",
      "subject: {name: library, version: '1'}\ntag: []\n",
      "subject: {}\ntag: []\n"
    ],
    ["CONTRACT.md", "", "description: ''\n"]
  ] as const;
  for (let mask = 0; mask < 8; mask += 1) {
    const tree = path.join(root, `tree-${mask}`, "spec");
    for (const [index, [filename, valid, invalid]] of documents.entries()) {
      const directory = path.join(tree, `item-${index}`);
      mkdirSync(directory, { recursive: true });
      writeFileSync(
        path.join(directory, filename),
        `---\ntitle: Example\ndescription: Example\nlast_updated: '2026-01-01'\n${Math.floor(mask / 2 ** index) % 2 === 1 ? invalid : valid}---\n`
      );
    }
    output.mockClear();
    errors.mockClear();
    const failed = documents.filter(
      (_, index) => Math.floor(mask / 2 ** index) % 2 === 1
    ).length;
    expect(main(["validate", tree])).toBe(failed === 0 ? 0 : 1);
    expect(output.mock.calls).toContainEqual([
      `- Total checks: ${documents.length}`
    ]);
    expect(output.mock.calls).toContainEqual([
      `- Passed: ${documents.length - failed}`
    ]);
    expect(output.mock.calls).toContainEqual([`- Failed: ${failed}`]);
    expect(errors.mock.calls).toHaveLength(failed);
  }
});
