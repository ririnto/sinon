// -*- coding: utf-8 -*-

import type { WorkflowDecision } from "./workflow-decision.js";
import { evaluateFanIn } from "./workflow-routing.js";
import type { WorkerResult } from "./workflow-routing.js";
import type { ValidationDecision } from "./workflow-validation.js";

/** Review and validation state required before publication. */
export interface CompletionState {
  readonly evidenceComplete: boolean;
  /** Historical identities exclude the workers in this completion state. */
  readonly historicalIdentityLedger: readonly string[];
  readonly ownerFix: "missing" | "not-needed" | "passed";
  readonly publicationAuthorized: boolean;
  readonly reviewClearance: WorkflowDecision<"cleared">;
  readonly selectedRecordUpdated: boolean;
  readonly validationClearance: ValidationDecision;
  readonly workers: readonly WorkerResult[];
}

/** Evaluate full fan-in, owner-fix, re-review, validation, and publication gates. */
export const evaluateCompletion = (
  state: CompletionState
): WorkflowDecision<"publish"> => {
  const validationIdentityLedger =
    state.validationClearance.value?.executorIdentityLedger ?? [];
  const blockers = [
    ...evaluateFanIn({
      historicalIdentityLedger: [
        ...state.historicalIdentityLedger,
        ...validationIdentityLedger
      ],
      workers: state.workers
    }).blockers
  ];
  if (state.reviewClearance.value !== "cleared") {
    blockers.push("review clearance is blocked");
  }
  if (state.ownerFix === "missing") {
    blockers.push("owning-writer fix is missing");
  }
  if (state.validationClearance.value === undefined) {
    blockers.push("validation clearance is blocked");
  }
  if (!state.evidenceComplete) {
    blockers.push("required evidence is incomplete");
  }
  if (!state.selectedRecordUpdated) {
    blockers.push("selected record is not updated");
  }
  if (!state.publicationAuthorized) {
    blockers.push("root-session publication authority is missing");
  }
  return blockers.length > 0
    ? { blockers }
    : { blockers: [], value: "publish" };
};
