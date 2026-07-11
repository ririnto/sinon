// -*- coding: utf-8 -*-

import { assertNever } from "./workflow-decision.js";
import type { WorkflowDecision } from "./workflow-decision.js";

export const FAILED_LEAF_PHASES = [
  "exploration",
  "implementation",
  "review",
  "validation"
] as const;

/** Workflow stages that can return a failed leaf result. */
export type FailedLeafPhase = (typeof FAILED_LEAF_PHASES)[number];

export type FailedLeaf = Readonly<{
  readonly disposition: "discarded" | "retained";
  readonly identity: string;
  readonly outcome: "failed";
  readonly phase: FailedLeafPhase;
  readonly remediation: "changed" | "unchanged";
  readonly replacement?: Readonly<{
    readonly identity: string;
    readonly outcome: "failed" | "passed";
  }>;
}>;

export type LeafResult =
  | FailedLeaf
  | Readonly<{
      readonly identity: string;
      readonly outcome: "passed";
      readonly phase: FailedLeafPhase;
    }>;

export type FailureBlocker = Readonly<{
  readonly index: number;
  readonly kind:
    | "changed-remediation-required"
    | "failed-leaf-discard-required"
    | "failed-leaf-replacement-required"
    | "fresh-leaf-identity-required"
    | "leaf-identity-required"
    | "replacement-identity-required"
    | "replacement-failed";
  readonly phase: FailedLeafPhase;
}>;

export type IdentityLedgerBlocker = Readonly<{
  readonly index: number;
  readonly kind: "identity-required" | "identity-reused";
}>;

/** Historical identities exclude the leaf records submitted with this call. */
export interface FailureLifecycleInput {
  readonly historicalIdentityLedger: readonly string[];
  readonly leaves: readonly LeafResult[];
}

/** Reject blank or reused current identities against the historical ledger. */
export const evaluateCurrentIdentityLedger = (input: {
  readonly currentIdentities: readonly string[];
  readonly historicalIdentityLedger: readonly string[];
}): readonly IdentityLedgerBlocker[] => {
  const identities = new Set(
    input.historicalIdentityLedger
      .map((identity) => identity.trim())
      .filter((identity) => identity !== "")
  );
  const blockers: IdentityLedgerBlocker[] = [];
  for (const [index, currentIdentity] of input.currentIdentities.entries()) {
    const identity = currentIdentity.trim();
    if (identity === "") {
      blockers.push({ index, kind: "identity-required" });
    } else if (identities.has(identity)) {
      blockers.push({ index, kind: "identity-reused" });
    } else {
      identities.add(identity);
    }
  }
  return blockers;
};

/** Evaluate whether failed leaves have a valid globally fresh recovery. */
export const evaluateFailedLeafRecovery = (
  input: FailureLifecycleInput
): WorkflowDecision<"cleared", FailureBlocker> => {
  const blockers: FailureBlocker[] = [];
  const replacements = input.leaves.flatMap((leaf, index) => {
    switch (leaf.outcome) {
      case "passed": {
        return [];
      }
      case "failed": {
        return leaf.replacement === undefined
          ? []
          : [
              {
                identity: leaf.replacement.identity,
                index,
                phase: leaf.phase
              }
            ];
      }
      default: {
        return assertNever(leaf);
      }
    }
  });
  const identityBlockers = evaluateCurrentIdentityLedger({
    currentIdentities: [
      ...input.leaves.map((leaf) => leaf.identity),
      ...replacements.map((replacement) => replacement.identity)
    ],
    historicalIdentityLedger: input.historicalIdentityLedger
  });
  for (const blocker of identityBlockers) {
    const isReplacement = blocker.index >= input.leaves.length;
    const replacement = replacements[blocker.index - input.leaves.length];
    const leaf = input.leaves[blocker.index];
    const kinds = {
      "identity-required": isReplacement
        ? "replacement-identity-required"
        : "leaf-identity-required",
      "identity-reused": "fresh-leaf-identity-required"
    } satisfies Readonly<
      Record<IdentityLedgerBlocker["kind"], FailureBlocker["kind"]>
    >;
    blockers.push({
      index: isReplacement ? replacement.index : blocker.index,
      kind: kinds[blocker.kind],
      phase: isReplacement ? replacement.phase : leaf.phase
    });
  }
  for (const [index, leaf] of input.leaves.entries()) {
    switch (leaf.outcome) {
      case "passed": {
        break;
      }
      case "failed": {
        if (leaf.disposition !== "discarded") {
          blockers.push({
            index,
            kind: "failed-leaf-discard-required",
            phase: leaf.phase
          });
        }
        if (leaf.remediation !== "changed") {
          blockers.push({
            index,
            kind: "changed-remediation-required",
            phase: leaf.phase
          });
        }
        if (leaf.replacement === undefined) {
          blockers.push({
            index,
            kind: "failed-leaf-replacement-required",
            phase: leaf.phase
          });
          break;
        }
        if (leaf.replacement.outcome === "failed") {
          blockers.push({
            index,
            kind: "replacement-failed",
            phase: leaf.phase
          });
        }
        break;
      }
      default: {
        return assertNever(leaf);
      }
    }
  }
  return blockers.length > 0
    ? { blockers }
    : { blockers: [], value: "cleared" };
};
