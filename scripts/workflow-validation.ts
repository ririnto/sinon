// -*- coding: utf-8 -*-

import type { WorkflowDecision } from "./workflow-decision.js";
import { assertNever } from "./workflow-decision.js";
import { evaluateFailedLeafRecovery } from "./workflow-failure.js";
import type { FailureBlocker, LeafResult } from "./workflow-failure.js";
import { WORKFLOW_POLICY } from "./workflow-policy-contract.js";

export type ValidationPhase =
  (typeof WORKFLOW_POLICY.validation.phases)[number];

type ValidationRetry = Readonly<{
  changedRemediation: boolean;
  executorId: string;
  outcome: "failed" | "passed";
}>;

export type ValidationResult =
  | Readonly<{
      executor: "validation-executor";
      executorId: string;
      outcome: "passed";
      phase: ValidationPhase;
      recordedBlocker?: boolean;
    }>
  | Readonly<{
      disposition: "discarded" | "retained";
      executor: "validation-executor";
      executorId: string;
      outcome: "failed";
      phase: ValidationPhase;
      retry?: ValidationRetry;
    }>;

export type ValidationBlocker = Readonly<{
  executorId: string;
  kind:
    | "changed-remediation-required"
    | "executor-identity-required"
    | "failed-executor-discard-required"
    | "fresh-executor-identity-required"
    | "recorded-validation-blocker"
    | "validation-failed";
  phase: ValidationPhase;
}>;

export interface ValidationInput {
  /** Historical identities exclude the validation results submitted here. */
  readonly historicalIdentityLedger: readonly string[];
  readonly results: readonly ValidationResult[];
}

export type ValidationClearance = Readonly<{
  readonly executorIdentityLedger: readonly string[];
  focused: "passed";
  integrated: "passed";
}>;

export type ValidationDecision = WorkflowDecision<
  ValidationClearance,
  ValidationBlocker
>;

const validationLeaf = (result: ValidationResult): LeafResult => {
  switch (result.outcome) {
    case "passed": {
      return {
        identity: result.executorId,
        outcome: "passed",
        phase: "validation"
      };
    }
    case "failed": {
      return {
        disposition: result.disposition,
        identity: result.executorId,
        outcome: "failed",
        phase: "validation",
        remediation:
          result.retry?.changedRemediation === true ? "changed" : "unchanged",
        replacement:
          result.retry === undefined
            ? undefined
            : {
                identity: result.retry.executorId,
                outcome: result.retry.outcome
              }
      };
    }
    default: {
      return assertNever(result);
    }
  }
};

const validationBlocker = (
  blocker: FailureBlocker,
  result: ValidationResult
): ValidationBlocker => {
  switch (blocker.kind) {
    case "changed-remediation-required": {
      return {
        executorId: result.executorId,
        kind: "changed-remediation-required",
        phase: result.phase
      };
    }
    case "failed-leaf-discard-required": {
      return {
        executorId: result.executorId,
        kind: "failed-executor-discard-required",
        phase: result.phase
      };
    }
    case "fresh-leaf-identity-required": {
      return {
        executorId: result.executorId,
        kind: "fresh-executor-identity-required",
        phase: result.phase
      };
    }
    case "leaf-identity-required":
    case "replacement-identity-required": {
      return {
        executorId: result.executorId,
        kind: "executor-identity-required",
        phase: result.phase
      };
    }
    case "failed-leaf-replacement-required":
    case "replacement-failed": {
      return {
        executorId: result.executorId,
        kind: "validation-failed",
        phase: result.phase
      };
    }
    default: {
      return assertNever(blocker.kind);
    }
  }
};

const validationIdentityLedger = (
  results: readonly ValidationResult[]
): readonly string[] =>
  results.flatMap((result) => {
    switch (result.outcome) {
      case "passed": {
        return [result.executorId];
      }
      case "failed": {
        return result.retry === undefined
          ? [result.executorId]
          : [result.executorId, result.retry.executorId];
      }
      default: {
        return assertNever(result);
      }
    }
  });

/** Require a successful focused and integrated validation outcome. */
export const evaluateValidation = (
  input: ValidationInput
): ValidationDecision => {
  const blockers: ValidationBlocker[] = [];
  const lifecycle = evaluateFailedLeafRecovery({
    historicalIdentityLedger: input.historicalIdentityLedger,
    leaves: input.results.map(validationLeaf)
  });
  for (const blocker of lifecycle.blockers) {
    const result = input.results[blocker.index];
    blockers.push(validationBlocker(blocker, result));
  }
  for (const result of input.results) {
    if (result.outcome === "passed" && result.recordedBlocker === true) {
      blockers.push({
        executorId: result.executorId,
        kind: "recorded-validation-blocker",
        phase: result.phase
      });
    }
  }
  for (const phase of WORKFLOW_POLICY.validation.phases) {
    if (!input.results.some((result) => result.phase === phase)) {
      blockers.push({
        executorId: "validation-executor",
        kind: "validation-failed",
        phase
      });
    }
  }
  return blockers.length === 0
    ? {
        blockers: [],
        value: {
          executorIdentityLedger: validationIdentityLedger(input.results),
          focused: "passed",
          integrated: "passed"
        }
      }
    : { blockers };
};
