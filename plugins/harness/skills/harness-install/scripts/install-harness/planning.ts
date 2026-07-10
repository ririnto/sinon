import path from "node:path";

import { workflowAssetNameForCiHost, workflowNameForMode } from "./commands.js";
import { listTrackedTreeFiles } from "./files.js";
import {
  isCommonSkipPath,
  isDirectTemplateEntry,
  isHostTemplatePath,
  toPosixRelative
} from "./paths.js";
import { templateDir } from "./types.js";
import type { InstallCandidate, InstallerConfig } from "./types.js";

const gitkeepPaths = [
  "docs/exec-plans/active/.gitkeep",
  "docs/exec-plans/completed/.gitkeep",
  "docs/generated/.gitkeep"
] as const;

const commonInstallCandidates = async (
  config: InstallerConfig
): Promise<readonly InstallCandidate[]> => {
  const srcDir = path.join(templateDir, "common");
  const candidates: InstallCandidate[] = [];
  for (const src of await listTrackedTreeFiles(srcDir)) {
    const rel = toPosixRelative(srcDir, src);
    if (
      isCommonSkipPath(rel) ||
      isHostTemplatePath(rel, config.ciHost) ||
      gitkeepPaths.includes(rel as (typeof gitkeepPaths)[number])
    ) {
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
    realTarget: "AGENTS.md",
    src: path.join(templateDir, "common", "AGENTS.md")
  },
  {
    dst: "CLAUDE.md",
    kind: "root-contract",
    realTarget: "CLAUDE.md",
    src: path.join(templateDir, "common", "CLAUDE.md")
  }
];

const gitkeepInstallCandidates = (): readonly InstallCandidate[] =>
  gitkeepPaths.map((dst) => ({ dst, kind: "gitkeep" as const }));

const runtimeSymlinkCandidates = (): readonly InstallCandidate[] => [
  {
    dst: ".agents/skills",
    kind: "symlink",
    symlinkTarget: "../.claude/skills"
  }
];

/** Build one destination-unique plan where stack assets override common assets. */
export const buildPlan = async (
  config: InstallerConfig
): Promise<readonly InstallCandidate[]> => {
  const candidates = [
    ...rootContractInstallCandidates(),
    ...(await commonInstallCandidates(config)),
    ...runtimeSymlinkCandidates(),
    ...(await stackInstallCandidates(config)),
    ...gitkeepInstallCandidates()
  ];
  return [
    ...new Map(
      candidates.map((candidate) => [candidate.dst, candidate])
    ).values()
  ];
};
