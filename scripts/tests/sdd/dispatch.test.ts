import { expect, test } from "bun:test";

import { parseArgs } from "../../../plugins/spec-driven-development/skills/spec-driven-development/scripts/sdd/args.js";
import { main } from "../../../plugins/spec-driven-development/skills/spec-driven-development/scripts/sdd/cli.js";

test("parseArgs returns undefined for an omitted command", () => {
  const parsed = parseArgs([]);
  expect(parsed).toBeUndefined();
});

test("parseArgs marks long help as a boolean option", () => {
  const parsed = parseArgs(["--help"]);
  expect(parsed?.options["help"]).toBe(true);
  expect(parsed?.positionals).toHaveLength(0);
});

test("parseArgs returns undefined for an unknown option", () => {
  const parsed = parseArgs(["validate", "--unknown"]);
  expect(parsed).toBeUndefined();
});

test("main returns zero for long help", () => {
  expect(main(["--help"])).toBe(0);
});

test("main returns one for an unknown command", () => {
  expect(main(["unknown-command"])).toBe(1);
});
