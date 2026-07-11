// -*- coding: utf-8 -*-

import { assertNever } from "./workflow-decision.js";
import type { WorkflowDecision } from "./workflow-decision.js";

type CriticalFindingCategory =
  | "behavior"
  | "contract"
  | "correctness"
  | "security";

/** Durable root disposition for a genuinely non-blocking low finding. */
export interface RootFindingDisposition {
  readonly durableRecord: boolean;
  readonly owner: string;
  readonly rationale: string;
  readonly status: "accepted" | "deferred";
}

/** Review findings remain blocking until fixed or explicitly recorded when low. */
export type ReviewFinding =
  | Readonly<{
      category: CriticalFindingCategory;
      state: "open" | "source-fixed";
    }>
  | Readonly<{
      category: "low";
      nonBlocking: boolean;
      rootDisposition?: RootFindingDisposition;
      state: "open" | "root-disposed" | "source-fixed";
    }>;

/** Inputs used to determine whether review findings have cleared. */
export interface ReviewFindingInput {
  readonly findings: readonly ReviewFinding[];
  readonly rereview: "fresh-passed" | "missing" | "not-needed";
}

const isRecordedDisposition = (
  disposition: RootFindingDisposition | undefined
): disposition is RootFindingDisposition =>
  disposition !== undefined &&
  (disposition.status === "accepted" || disposition.status === "deferred") &&
  disposition.owner.trim() !== "" &&
  disposition.rationale.trim() !== "";

/** Apply the review disposition gate before a finding can leave fan-in. */
export const decideReviewFindings = (
  input: ReviewFindingInput
): WorkflowDecision<"cleared"> => {
  const blockers: string[] = [];
  let sourceFixApplied = false;
  for (const finding of input.findings) {
    switch (finding.category) {
      case "behavior":
      case "contract":
      case "correctness":
      case "security": {
        if (finding.state === "open") {
          blockers.push(`${finding.category} finding requires a source fix`);
        } else {
          sourceFixApplied = true;
        }
        break;
      }
      case "low": {
        switch (finding.state) {
          case "open": {
            blockers.push("low finding remains open");
            break;
          }
          case "source-fixed": {
            sourceFixApplied = true;
            break;
          }
          case "root-disposed": {
            const disposition = finding.rootDisposition;
            if (!finding.nonBlocking) {
              blockers.push("low finding requires a source fix");
            } else if (!isRecordedDisposition(disposition)) {
              blockers.push(
                "non-blocking low finding requires root disposition, owner, and rationale"
              );
            } else if (!disposition.durableRecord) {
              blockers.push(
                "non-blocking low finding requires a durable root disposition record"
              );
            }
            break;
          }
          default: {
            return assertNever(finding);
          }
        }
        break;
      }
      default: {
        return assertNever(finding);
      }
    }
  }
  if (sourceFixApplied && input.rereview !== "fresh-passed") {
    blockers.push("source fixes require a fresh passed re-review");
  }
  return blockers.length > 0
    ? { blockers }
    : { blockers: [], value: "cleared" };
};
