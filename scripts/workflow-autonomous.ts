// -*- coding: utf-8 -*-

import { assertNever } from "./workflow-decision.js";
import type { WorkflowDecision } from "./workflow-decision.js";
import { evaluateFailedLeafRecovery } from "./workflow-failure.js";
import type { FailedLeaf } from "./workflow-failure.js";

type AutonomousTerminalFailureKind =
  | "authentication"
  | "authority"
  | "missing-intent"
  | "policy"
  | "unavailable-dependency"
  | "unavailable-environment";

/** Inputs used to classify an autonomous failed cycle. */
export type AutonomousFailureInput =
  | Readonly<{
      readonly failedCycles: number;
      readonly failure: Readonly<{
        readonly kind: AutonomousTerminalFailureKind;
      }>;
    }>
  | Readonly<{
      readonly failedCycles: number;
      readonly failure: Readonly<{
        /** Historical identities exclude the failed leaf submitted here. */
        readonly historicalIdentityLedger: readonly string[];
        readonly kind: "retryable";
        readonly leaf: FailedLeaf;
      }>;
    }>;

/** Retry decision records whether the current failure consumes the retry budget. */
export interface AutonomousFailureDecision {
  readonly action: "retry" | "stop";
  readonly consumesRetryBudget: boolean;
  readonly remainingRetries: number;
}

const isPriorRetryableFailureCount = (
  failedCycles: number
): failedCycles is 0 | 1 => failedCycles === 0 || failedCycles === 1;

const remainingRetries = (failedCycles: 0 | 1): number =>
  Math.max(0, 2 - failedCycles);

const stopFailure = (
  retries: number,
  reason: string
): WorkflowDecision<AutonomousFailureDecision> => ({
  blockers: [reason],
  value: {
    action: "stop",
    consumesRetryBudget: false,
    remainingRetries: retries
  }
});

/** Stop terminal failures; retry a valid recovered leaf only before a second failure. */
export const classifyAutonomousFailure = (
  input: AutonomousFailureInput
): WorkflowDecision<AutonomousFailureDecision> => {
  if (!isPriorRetryableFailureCount(input.failedCycles)) {
    return {
      blockers: ["failedCycles must be an integer from zero through one"]
    };
  }
  switch (input.failure.kind) {
    case "authentication":
    case "authority":
    case "missing-intent":
    case "policy":
    case "unavailable-dependency":
    case "unavailable-environment": {
      return stopFailure(
        remainingRetries(input.failedCycles),
        `${input.failure.kind} failure stops autonomous work immediately`
      );
    }
    case "retryable": {
      const recovery = evaluateFailedLeafRecovery({
        historicalIdentityLedger: input.failure.historicalIdentityLedger,
        leaves: [input.failure.leaf]
      });
      if (recovery.value === undefined) {
        return stopFailure(
          remainingRetries(input.failedCycles),
          `${input.failure.leaf.phase} failure recovery is invalid`
        );
      }
      if (input.failedCycles >= 1) {
        return {
          blockers: [
            "autonomous retry budget is exhausted after two failed cycles"
          ],
          value: {
            action: "stop",
            consumesRetryBudget: true,
            remainingRetries: 0
          }
        };
      }
      return {
        blockers: [],
        value: {
          action: "retry",
          consumesRetryBudget: true,
          remainingRetries: 1 - input.failedCycles
        }
      };
    }
    default: {
      return assertNever(input.failure);
    }
  }
};
