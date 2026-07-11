// -*- coding: utf-8 -*-

import { assertNever } from "./workflow-decision.js";
import {
  evaluateCurrentIdentityLedger,
  evaluateFailedLeafRecovery
} from "./workflow-failure.js";
import type { FailedLeafPhase, FailureBlocker } from "./workflow-failure.js";

interface WorkerBase {
  readonly files: readonly string[];
  readonly id: string;
  readonly mode: "executor" | "read-only" | "writer";
}

type FailedWorkerResult = WorkerBase &
  Readonly<{
    readonly disposition: "discarded" | "retained";
    readonly phase: FailedLeafPhase;
    readonly remediation: "changed" | "unchanged";
    readonly replacement?: Readonly<{
      readonly id: string;
      readonly status: "failed" | "passed";
    }>;
    readonly status: "failed";
  }>;

/** One worker result used for fan-in and ownership checks. */
export type WorkerResult =
  | FailedWorkerResult
  | (WorkerBase &
      Readonly<{
        readonly status: "missing" | "passed";
      }>);

/** Historical identities exclude every worker and replacement submitted here. */
export interface WorkerRecoveryInput {
  readonly historicalIdentityLedger: readonly string[];
  readonly workers: readonly WorkerResult[];
}

const failedRecoveryMessage = (kind: FailureBlocker["kind"]): string => {
  switch (kind) {
    case "changed-remediation-required": {
      return "failed leaf recovery requires changed remediation";
    }
    case "failed-leaf-discard-required": {
      return "failed leaf recovery requires discarded disposition";
    }
    case "failed-leaf-replacement-required": {
      return "failed leaf recovery requires a replacement";
    }
    case "fresh-leaf-identity-required": {
      return "failed leaf recovery requires a fresh identity";
    }
    case "leaf-identity-required":
    case "replacement-identity-required": {
      return "failed leaf recovery requires a nonblank identity";
    }
    case "replacement-failed": {
      return "failed leaf replacement failed";
    }
    default: {
      return assertNever(kind);
    }
  }
};

const isFailedWorker = (worker: WorkerResult): worker is FailedWorkerResult =>
  worker.status === "failed";

/** Translate canonical failed-leaf recovery blockers for fan-in evidence. */
export const evaluateWorkerRecovery = (
  input: WorkerRecoveryInput
): readonly string[] => {
  const { workers } = input;
  const failedWorkers = workers.filter(isFailedWorker);
  const replacements = failedWorkers.flatMap((worker) =>
    worker.replacement === undefined
      ? []
      : [{ id: worker.replacement.id, worker }]
  );
  const identityBlockers = evaluateCurrentIdentityLedger({
    currentIdentities: [
      ...workers.map((worker) => worker.id),
      ...replacements.map((replacement) => replacement.id)
    ],
    historicalIdentityLedger: input.historicalIdentityLedger
  });
  const recovery = evaluateFailedLeafRecovery({
    historicalIdentityLedger: input.historicalIdentityLedger,
    leaves: failedWorkers.map((worker) => ({
      disposition: worker.disposition,
      identity: worker.id,
      outcome: "failed",
      phase: worker.phase,
      remediation: worker.remediation,
      replacement:
        worker.replacement === undefined
          ? undefined
          : {
              identity: worker.replacement.id,
              outcome: worker.replacement.status
            }
    }))
  });
  const lifecycleBlockers = recovery.blockers.filter(
    (blocker) =>
      blocker.kind !== "fresh-leaf-identity-required" &&
      blocker.kind !== "leaf-identity-required" &&
      blocker.kind !== "replacement-identity-required"
  );
  return [
    ...identityBlockers.map((blocker) => {
      const worker =
        blocker.index < workers.length
          ? workers[blocker.index]
          : replacements[blocker.index - workers.length].worker;
      const identity = worker.id.trim();
      if (blocker.index < workers.length) {
        return blocker.kind === "identity-required"
          ? `${worker.id}: worker identity is required`
          : `${identity}: worker identity must be globally fresh`;
      }
      return `${identity}: ${failedRecoveryMessage(
        blocker.kind === "identity-required"
          ? "replacement-identity-required"
          : "fresh-leaf-identity-required"
      )}`;
    }),
    ...lifecycleBlockers.map((blocker) => {
      const worker = failedWorkers[blocker.index];
      return `${worker.id}: ${failedRecoveryMessage(blocker.kind)}`;
    })
  ];
};
