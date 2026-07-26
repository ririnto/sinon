import path from "node:path";

const repositoryRoot = path.resolve(import.meta.dirname, "../..");
const harnessPluginRoot = path.join(repositoryRoot, "plugins/harness");
const sddSkillRoot = path.join(
  repositoryRoot,
  "plugins/spec-driven-development/skills/spec-driven-development"
);

export const repositoryPaths = Object.freeze({
  authoringAssetRoot: path.join(
    repositoryRoot,
    "plugins/agent-capability-kit/skills/plugin-authoring/assets"
  ),
  harnessPluginRoot,
  sddFixtureRoot: path.join(
    sddSkillRoot,
    "references/examples/valid-spec-tree/spec"
  )
});
