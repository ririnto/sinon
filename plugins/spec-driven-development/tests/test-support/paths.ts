import { fileURLToPath } from "node:url";

/**
 * Exposes the valid SDD example tree for validation tests.
 */
export const repositoryPaths = Object.freeze({
  sddFixtureRoot: fileURLToPath(
    new URL(
      "../../skills/spec-driven-development/references/examples/valid-spec-tree/spec",
      import.meta.url
    )
  )
});
