import { expect, test } from "bun:test";

import { parseArgs } from "../../../plugins/spec-driven-development/skills/spec-driven-development/scripts/sdd/args.js";
import { main } from "../../../plugins/spec-driven-development/skills/spec-driven-development/scripts/sdd/cli.js";
import { generateMermaid } from "../../../plugins/spec-driven-development/skills/spec-driven-development/scripts/sdd/graph.js";
import { repositoryPaths } from "../../test-support/paths.js";

test("generateMermaid preserves linked fixture output", () => {
  expect(generateMermaid(repositoryPaths.sddFixtureRoot)).toBe(
    [
      "flowchart TD",
      '  domain_ingest_SPEC_md["Domain Ingest"]',
      '  domain_SPEC_md["Domain Capability"]',
      "  domain_SPEC_md --> domain_ingest_SPEC_md"
    ].join("\n")
  );
});

test("parseArgs returns undefined for an omitted command", () => {
  expect(parseArgs([])).toBeUndefined();
});

test("parseArgs marks long help as a boolean option", () => {
  const parsed = parseArgs(["--help"]);
  expect(parsed?.options["help"]).toBe(true);
  expect(parsed?.positionals).toHaveLength(0);
});

test("parseArgs returns undefined for an unknown option", () => {
  expect(parseArgs(["validate", "--unknown"])).toBeUndefined();
});

test("main returns zero for long help", () => {
  expect(main(["--help"])).toBe(0);
});

test("main returns one for an unknown command", () => {
  expect(main(["unknown-command"])).toBe(1);
});
