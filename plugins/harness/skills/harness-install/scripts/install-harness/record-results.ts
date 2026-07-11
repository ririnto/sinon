import { sourceDigestForCandidate } from "./record-content.js";
import { readCandidateState } from "./record-state.js";
import type { CandidateState } from "./record-state.js";
import { fail } from "./types.js";
import type {
  InstallAssetRecord,
  InstallCandidate,
  InstallOperationResult
} from "./types.js";

const stateMatchesSource = (
  candidate: InstallCandidate,
  state: CandidateState,
  sourceDigest: string
): boolean =>
  candidate.kind === "root-contract"
    ? state.managedDigest === sourceDigest
    : state.targetDigest === sourceDigest;

/** Build records from actual before-and-after installer states. */
export const buildInstallResults = (
  candidates: readonly InstallCandidate[],
  beforeStates: ReadonlyMap<string, CandidateState>,
  operations: ReadonlyMap<string, InstallOperationResult>
): Promise<readonly InstallAssetRecord[]> =>
  Promise.all(
    candidates.map(async (candidate): Promise<InstallAssetRecord> => {
      const before = beforeStates.get(candidate.dst) ?? { exists: false };
      const after = await readCandidateState(candidate);
      const sourceDigest = await sourceDigestForCandidate(candidate);
      const matchesSource = stateMatchesSource(candidate, after, sourceDigest);
      const operation = operations.get(candidate.dst);
      if (operation === undefined) {
        return fail(`missing actual install operation for ${candidate.dst}`);
      }
      if (operation.outcome === "created" && before.exists) {
        return fail(
          `created operation had a pre-existing target: ${candidate.dst}`
        );
      }
      if (operation.outcome !== "created" && !before.exists) {
        return fail(
          `${operation.outcome} operation had no pre-existing target: ${candidate.dst}`
        );
      }
      if (!after.exists) {
        return fail(`install operation left no target: ${candidate.dst}`);
      }
      if (
        operation.outcome !== "conflict" &&
        (operation.ownership === "harness" ||
          operation.ownership === "shared") &&
        !matchesSource
      ) {
        return fail(
          `installed ${operation.ownership} target does not match source: ${candidate.dst}`
        );
      }
      return {
        kind: candidate.kind,
        ...(after.linkTarget === undefined
          ? {}
          : { linkTarget: after.linkTarget }),
        ...(after.managedDigest === undefined
          ? {}
          : { managedDigest: after.managedDigest }),
        outcome: operation.outcome,
        ownership: operation.ownership,
        path: candidate.dst,
        sourceDigest,
        ...(after.targetDigest === undefined
          ? {}
          : { targetDigest: after.targetDigest })
      };
    })
  );
