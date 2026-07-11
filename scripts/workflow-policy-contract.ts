// -*- coding: utf-8 -*-

export const WORKFLOW_POLICY = {
  completion: {
    blockedReport: "blocked-report",
    requires: [
      "fan-in",
      "review-clearance",
      "focused-validation",
      "integrated-validation",
      "required-evidence",
      "selected-record-updated",
      "root-publication-authority"
    ]
  },
  documentEdits: {
    manualFallback: "recorded-manual-fallback",
    requires: ["stop-slop", "semantic-line-breaks"]
  },
  failure: {
    discardRetryRequires: [
      "discarded",
      "changed-remediation",
      "fresh-replacement-identity"
    ],
    failedCycles: "prior-retryable-failures",
    phases: ["exploration", "implementation", "review", "validation"],
    retryBudget: "two-failed-cycles"
  },
  records: { addenda: "install-all", selection: "per-work-item" },
  review: {
    lowDispositions: ["accepted", "deferred"],
    sourceFixRequires: "fresh-rereview"
  },
  root: {
    backgroundCliFallback: {
      admission: "native-delegation-unavailable",
      authority: "root-only",
      leafLimit: "one-bounded-leaf",
      leafProhibitions: [
        "no-delegation",
        "no-integration",
        "no-commit",
        "no-publication"
      ],
      record: "recorded",
      unavailableHandoff: "eligible-root-or-human"
    },
    opusSol: {
      delegates: ["implementation", "validation", "review"],
      directExploration: "bounded-read-only",
      requiredFor: [
        "unresolved-ambiguity",
        "security-sensitive-decision",
        "contradictory-fan-in",
        "high-risk-publication"
      ]
    },
    terra: {
      delegates: ["exploration", "implementation", "validation", "review"],
      requires: ["bounded-work", "executable-plan"]
    },
    unavailable: {
      handoffOwner: "eligible-root-or-human",
      requiredCapability: "opus-sol-medium"
    }
  },
  schemaVersion: 1,
  validation: {
    executor: "validation-executor",
    phases: ["focused", "integrated"]
  }
} as const;

export type WorkflowPolicy = typeof WORKFLOW_POLICY;
