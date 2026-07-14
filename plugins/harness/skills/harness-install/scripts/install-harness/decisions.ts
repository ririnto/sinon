import { renderCandidateContent } from "./content.js";
import { pathExists, readUtf8 } from "./files.js";
import { checkSafeFileDestination } from "./paths.js";
import type { InstallCandidate, InstallerConfig } from "./types.js";

export type FileInstallDecision = Readonly<{
  diagnostic: "stderr" | "stdout";
  message: string;
  write: boolean;
}>;

/** Decide one file install from the target bytes without consulting history. */
export const decideFileInstall = async (
  config: InstallerConfig,
  candidate: InstallCandidate
): Promise<FileInstallDecision> => {
  await checkSafeFileDestination(candidate.dst);
  const source = await renderCandidateContent(candidate);
  if (!(await pathExists(candidate.dst))) {
    return {
      diagnostic: "stdout",
      message:
        candidate.kind === "seed"
          ? `deliver seed: ${candidate.dst}`
          : `write: ${candidate.dst}`,
      write: true
    };
  }

  const current = await readUtf8(candidate.dst);
  if (config.force) {
    return {
      diagnostic: "stdout",
      message:
        candidate.kind === "seed"
          ? `overwrite seed (--force): ${candidate.dst}`
          : `overwrite (--force): ${candidate.dst}`,
      write: true
    };
  }

  if (current === source) {
    return {
      diagnostic: "stdout",
      message: `keep existing (matches template): ${candidate.dst}`,
      write: false
    };
  }

  if (candidate.kind === "seed") {
    return {
      diagnostic: "stdout",
      message: `preserve seed (target differs): ${candidate.dst}`,
      write: false
    };
  }

  return {
    diagnostic: "stderr",
    message: `conflict: ${candidate.dst} differs from the plugin source; preserving target; rerun with --force to overwrite`,
    write: false
  };
};
