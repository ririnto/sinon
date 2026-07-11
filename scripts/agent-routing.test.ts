import { expect, test } from "bun:test";
import path from "node:path";

import { validateAgentRouting } from "./agent-routing.js";

test("the repository routing inventory is valid", () => {
  const result = validateAgentRouting(path.resolve(import.meta.dirname, ".."));

  expect(result.errors).toEqual([]);
  expect(result.warnings).toHaveLength(3);
  expect(result.warnings[0]).toContain("runtime-inert compatibility metadata");
});
