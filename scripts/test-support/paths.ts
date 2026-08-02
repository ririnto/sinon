import path from "node:path";

const repositoryRoot = path.resolve(import.meta.dirname, "../..");
const sddSkillRoot = path.join(
  repositoryRoot,
  "plugins/spec-driven-development/skills/spec-driven-development"
);
const astrolabeRoot = path.join(repositoryRoot, "plugins/astrolabe");

export const repositoryPaths = Object.freeze({
  astrolabeRoot,
  sddFixtureRoot: path.join(
    sddSkillRoot,
    "references/examples/valid-spec-tree/spec"
  )
});
