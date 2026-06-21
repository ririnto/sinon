import path from "node:path";

import { workflowAssetNameForCiHost, workflowNameForMode } from "./commands.js";
import { listTrackedTreeFiles } from "./files.js";
import {
  isCommonSkipPath,
  isDirectTemplateEntry,
  isHostTemplatePath,
  toPosixRelative
} from "./paths.js";
import { agentsMarker, claudeMarker, templateDir } from "./types.js";
import type { InstallCandidate, InstallerConfig } from "./types.js";

const commonInstallCandidates = async (
  config: InstallerConfig
): Promise<readonly InstallCandidate[]> => {
  const srcDir = path.join(templateDir, "common");
  const candidates: InstallCandidate[] = [];
  for (const src of await listTrackedTreeFiles(srcDir)) {
    const rel = toPosixRelative(srcDir, src);
    if (isCommonSkipPath(rel) || isHostTemplatePath(rel, config.ciHost)) {
      continue;
    }
    const selectedSrc =
      rel === "WORKFLOW.md"
        ? path.join(srcDir, workflowAssetNameForCiHost(config.ciHost))
        : src;
    const seed = isDirectTemplateEntry(rel);
    candidates.push({
      dst: rel,
      kind: seed ? "seed" : "file",
      seed,
      src: selectedSrc
    });
  }
  return candidates;
};

const stackInstallCandidates = async (
  config: InstallerConfig
): Promise<readonly InstallCandidate[]> => {
  const srcDir = path.join(templateDir, config.mode);
  const workflow = workflowNameForMode(config.mode);
  const candidates: InstallCandidate[] = [];
  for (const src of await listTrackedTreeFiles(srcDir)) {
    const rel = toPosixRelative(srcDir, src);
    if (rel === ".gitlab-ci.yml") {
      if (config.ciHost === "gitlab" || config.ciHost === "both") {
        candidates.push({ dst: ".gitlab-ci.yml", kind: "stack-file", src });
      }
      continue;
    }
    if (rel.startsWith(".github/workflows/")) {
      if (config.ciHost === "github" || config.ciHost === "both") {
        candidates.push({
          dst: `.github/workflows/${workflow}`,
          kind: "stack-file",
          src
        });
      }
      continue;
    }
    candidates.push({ dst: rel, kind: "stack-file", src });
  }
  return candidates;
};

const rootContractInstallCandidates = (): readonly InstallCandidate[] => [
  {
    dst: "AGENTS.md",
    kind: "root-contract",
    marker: agentsMarker,
    realTarget: "AGENTS.md",
    src: path.join(templateDir, "common", "AGENTS.md")
  },
  {
    dst: "CLAUDE.md",
    kind: "root-contract",
    marker: claudeMarker,
    realTarget: "CLAUDE.md",
    src: path.join(templateDir, "common", "CLAUDE.md")
  }
];

const gitkeepInstallCandidates = (): readonly InstallCandidate[] => [
  { dst: "docs/exec-plans/active/.gitkeep", kind: "gitkeep" },
  { dst: "docs/exec-plans/completed/.gitkeep", kind: "gitkeep" },
  { dst: "docs/generated/.gitkeep", kind: "gitkeep" }
];

export const buildPlan = async (
  config: InstallerConfig
): Promise<readonly InstallCandidate[]> => [
  ...(await commonInstallCandidates(config)),
  ...rootContractInstallCandidates(),
  ...(await stackInstallCandidates(config)),
  ...gitkeepInstallCandidates()
];
