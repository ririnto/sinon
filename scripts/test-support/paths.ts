import { fileURLToPath } from "node:url";

/**
 * Provides repository test fixture paths.
 */
export const repositoryPaths = Object.freeze({
  sddFixtureRoot: fileURLToPath(
    new URL(
      "../../plugins/spec-driven-development/skills/spec-driven-development/references/examples/valid-spec-tree/spec",
      import.meta.url
    )
  )
});
