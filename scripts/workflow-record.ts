// -*- coding: utf-8 -*-

import type { WorkflowDecision } from "./workflow-decision.js";

/** Supported review-publication hosts. */
export type ReviewHost = "github" | "gitlab" | "local";

/** Evidence used to select one review host. */
export interface HostEvidence {
  readonly existingRecord?: ReviewHost;
  readonly explicit?: ReviewHost;
  readonly policy?: ReviewHost;
  readonly remoteCandidates: readonly ReviewHost[];
  readonly upstream?: ReviewHost;
}

/** Host-record routing never creates a second remote record implicitly. */
export interface ReviewRecordRequest {
  readonly dualAuthority: "none" | "repository-policy" | "user-direction";
  readonly evidence: HostEvidence;
  readonly mode: "dual" | "primary";
}

/** Select one publication host without preloading multiple host branches. */
export const selectReviewHost = (
  evidence: HostEvidence
): WorkflowDecision<ReviewHost> => {
  for (const candidate of [
    evidence.explicit,
    evidence.existingRecord,
    evidence.policy,
    evidence.upstream
  ]) {
    if (candidate !== undefined) {
      return { blockers: [], value: candidate };
    }
  }
  const candidates = [...new Set(evidence.remoteCandidates)];
  if (candidates.length === 1) {
    return { blockers: [], value: candidates[0] };
  }
  if (candidates.length > 1) {
    return {
      blockers: [
        "multiple publication hosts remain plausible; request a focused host choice"
      ]
    };
  }
  return {
    blockers: ["no supported publication host or local review policy was found"]
  };
};

/** Select one review record by default, or an authorized explicit dual record. */
export const selectReviewRecord = (
  request: ReviewRecordRequest
): WorkflowDecision<
  | Readonly<{ host: ReviewHost; mode: "primary" }>
  | Readonly<{ hosts: readonly ["github", "gitlab"]; mode: "dual" }>
> => {
  if (request.mode === "dual") {
    return request.dualAuthority === "none"
      ? {
          blockers: [
            "dual GitHub and GitLab records require user direction or repository policy"
          ]
        }
      : {
          blockers: [],
          value: { hosts: ["github", "gitlab"], mode: "dual" }
        };
  }
  const primary = selectReviewHost(request.evidence);
  return primary.value === undefined
    ? { blockers: primary.blockers }
    : { blockers: [], value: { host: primary.value, mode: "primary" } };
};

/** Return the one optional reference allowed for a selected remote host. */
export const referenceForHost = (host: ReviewHost): string | undefined => {
  if (host === "github") {
    return "references/github.md";
  }
  if (host === "gitlab") {
    return "references/gitlab.md";
  }
  return undefined;
};
