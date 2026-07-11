import { createHash } from "node:crypto";

import { renderCandidateContent } from "./content.js";
import {
  managedBeginMarker,
  managedEndMarker,
  renderManagedBlock
} from "./managed.js";
import { fail } from "./types.js";
import type { InstallCandidate } from "./types.js";

/** Return a stable digest for installed content. */
export const digestContent = (content: string): string =>
  createHash("sha256").update(content).digest("hex");

/** Extract the normalized managed block from one root contract. */
export const managedBlock = (content: string): null | string => {
  const begin = content.indexOf(managedBeginMarker);
  const end = content.indexOf(managedEndMarker, begin);
  if (begin === -1 || end === -1) {
    return null;
  }
  return content.slice(begin, end + managedEndMarker.length);
};

/** Return the source digest expected for one candidate. */
export const sourceDigestForCandidate = async (
  candidate: InstallCandidate
): Promise<string> => {
  if (candidate.kind === "symlink") {
    return digestContent(candidate.symlinkTarget ?? "");
  }
  if (candidate.kind === "gitkeep") {
    return digestContent("");
  }
  const source = await renderCandidateContent(candidate);
  if (candidate.kind !== "root-contract") {
    return digestContent(source);
  }
  const block = managedBlock(renderManagedBlock(source));
  if (block === null) {
    return fail(`cannot render managed block for ${candidate.dst}`);
  }
  return digestContent(block);
};
