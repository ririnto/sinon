import { pathExists, readInstallAsset, readUtf8 } from "./files.js";
import { checkSafeFileDestination, requiredSrc } from "./paths.js";
import { canRefreshOwnedAsset, digestContent } from "./record.js";
import type {
  InstallAssetRecord,
  InstallCandidate,
  InstallerConfig,
  InstallOperationResult
} from "./types.js";

export type FileInstallDecision = Readonly<{
  diagnostic: "stderr" | "stdout";
  message: string;
  operation: InstallOperationResult;
  write: boolean;
}>;

const canKeepAdoptedAsset = (
  previous: InstallAssetRecord | undefined
): boolean => previous?.ownership === "target" && previous.outcome === "kept";

const keptOwnership = (
  candidate: InstallCandidate,
  previous: InstallAssetRecord | undefined,
  currentDigest: string
): InstallOperationResult["ownership"] => {
  if (candidate.kind === "seed") {
    return "target";
  }
  if (
    previous?.ownership === "harness" &&
    previous.targetDigest === currentDigest
  ) {
    return "harness";
  }
  return "target";
};

/** Decide one file install from safe target state without writing anything. */
export const decideFileInstall = async (
  config: InstallerConfig,
  candidate: InstallCandidate,
  previousAssets: ReadonlyMap<string, InstallAssetRecord>
): Promise<FileInstallDecision> => {
  await checkSafeFileDestination(candidate.dst);
  const source = await readInstallAsset(requiredSrc(candidate));
  if (!(await pathExists(candidate.dst))) {
    return {
      diagnostic: "stdout",
      message:
        candidate.kind === "seed"
          ? `deliver seed: ${candidate.dst}`
          : `write: ${candidate.dst}`,
      operation: {
        outcome: "created",
        ownership: candidate.kind === "seed" ? "target" : "harness"
      },
      write: true
    };
  }
  const current = await readUtf8(candidate.dst);
  const currentDigest = digestContent(current);
  if (config.force) {
    return {
      diagnostic: "stdout",
      message:
        candidate.kind === "seed"
          ? `overwrite seed (--force): ${candidate.dst}`
          : `overwrite (--force): ${candidate.dst}`,
      operation: {
        outcome: "updated",
        ownership: candidate.kind === "seed" ? "target" : "harness"
      },
      write: true
    };
  }
  if (candidate.kind === "seed") {
    return {
      diagnostic: "stdout",
      message: `skip seed (target exists): ${candidate.dst}`,
      operation: { outcome: "kept", ownership: "target" },
      write: false
    };
  }
  const sourceDigest = digestContent(source);
  const previous = previousAssets.get(candidate.dst);
  if (currentDigest === sourceDigest) {
    return {
      diagnostic: "stdout",
      message: `keep existing (matches template): ${candidate.dst}`,
      operation: {
        outcome: "kept",
        ownership: keptOwnership(candidate, previous, currentDigest)
      },
      write: false
    };
  }
  if (canRefreshOwnedAsset(previous, currentDigest, sourceDigest)) {
    return {
      diagnostic: "stdout",
      message: `refresh owned: ${candidate.dst}`,
      operation: { outcome: "updated", ownership: "harness" },
      write: true
    };
  }
  if (canKeepAdoptedAsset(previous)) {
    return {
      diagnostic: "stdout",
      message: `keep adopted target: ${candidate.dst}`,
      operation: { outcome: "kept", ownership: "target" },
      write: false
    };
  }
  return {
    diagnostic: "stderr",
    message: `conflict: ${candidate.dst} differs from the plugin source; preserving target; run --adopt ${candidate.dst} to keep target ownership or --force to overwrite`,
    operation: { outcome: "conflict", ownership: "target" },
    write: false
  };
};
