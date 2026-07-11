import { readInstallAsset } from "./files.js";
import { requiredSrc } from "./paths.js";
import type { InstallCandidate } from "./types.js";

/** Resolve the exact bytes the installer compares, displays, digests, and writes. */
export const renderCandidateContent = (
  candidate: InstallCandidate
): Promise<string> => readInstallAsset(requiredSrc(candidate));
