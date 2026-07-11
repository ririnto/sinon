import { lstat, readlink } from "node:fs/promises";

import { isSymlink, pathExists, readUtf8 } from "./files.js";
import {
  checkSafeFileDestination,
  checkSafeParentDir,
  requiredRealTarget
} from "./paths.js";
import { digestContent, managedBlock } from "./record-content.js";
import type { InstallCandidate } from "./types.js";

export type CandidateState = Readonly<{
  exists: boolean;
  linkTarget?: string;
  managedDigest?: string;
  targetDigest?: string;
}>;

/** Return the on-disk state for one install candidate. */
export const readCandidateState = async (
  candidate: InstallCandidate
): Promise<CandidateState> => {
  const target =
    candidate.kind === "root-contract"
      ? requiredRealTarget(candidate)
      : candidate.dst;
  await (candidate.kind === "symlink"
    ? checkSafeParentDir(target)
    : checkSafeFileDestination(target));
  if (await isSymlink(target)) {
    const linkTarget = await readlink(target);
    return {
      exists: true,
      linkTarget,
      targetDigest: digestContent(linkTarget)
    };
  }
  if (!(await pathExists(target))) {
    return { exists: false };
  }
  const stat = await lstat(target);
  if (!stat.isFile()) {
    return { exists: true };
  }
  const content = await readUtf8(target);
  const block = managedBlock(content);
  return {
    exists: true,
    ...(block === null ? {} : { managedDigest: digestContent(block) }),
    targetDigest: digestContent(content)
  };
};

/** Capture candidate state before an install operation. */
export const captureCandidateStates = async (
  candidates: readonly InstallCandidate[]
): Promise<ReadonlyMap<string, CandidateState>> =>
  new Map(
    await Promise.all(
      candidates.map(
        async (candidate) =>
          [candidate.dst, await readCandidateState(candidate)] as const
      )
    )
  );
