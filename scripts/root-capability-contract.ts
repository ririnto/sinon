// -*- coding: utf-8 -*-

import type { WorkflowDecision } from "./workflow-decision.js";
import { WORKFLOW_POLICY } from "./workflow-policy-contract.js";

export type RootCapability = "opus-sol-medium" | "sonnet-terra-medium";

export type RootCapabilityEscalation =
  | "unresolved-ambiguity"
  | "security-sensitive-decision"
  | "contradictory-fan-in"
  | "high-risk-publication";

export type RootDelegatedPhase =
  | "exploration"
  | "implementation"
  | "validation"
  | "review";

export interface RootCapabilityBlocker {
  readonly handoffOwner: "eligible-root-or-human";
  readonly reason:
    | "bounded-work-required"
    | "executable-plan-required"
    | "high-risk-publication"
    | "no-available-capability"
    | "security-sensitive-decision"
    | "contradictory-fan-in"
    | "unresolved-ambiguity";
  readonly requiredCapability: RootCapability;
}

export interface RootCapabilityInput {
  readonly availableCapabilities: readonly RootCapability[];
  readonly boundedWork: boolean;
  readonly escalation?: RootCapabilityEscalation | undefined;
  readonly executablePlan: boolean;
}

export type RootCapabilityDecision = WorkflowDecision<
  Readonly<{
    capability: RootCapability;
    delegatedPhases: readonly RootDelegatedPhase[];
    directPhases: readonly RootDelegatedPhase[];
    requirement: "opus-sol-required" | "sonnet-terra-baseline";
  }>,
  RootCapabilityBlocker
>;

const ROOT_DELEGATED_PHASES = WORKFLOW_POLICY.root.terra.delegates;

const OPUS_SOL_DELEGATED_PHASES = WORKFLOW_POLICY.root.opusSol.delegates;

const block = (
  reason: RootCapabilityBlocker["reason"],
  requiredCapability: RootCapability
): RootCapabilityDecision => ({
  blockers: [
    { handoffOwner: "eligible-root-or-human", reason, requiredCapability }
  ]
});

const success = (
  capability: RootCapability,
  requirement: "opus-sol-required" | "sonnet-terra-baseline",
  directPhases: readonly RootDelegatedPhase[]
): RootCapabilityDecision => ({
  blockers: [],
  value: {
    capability,
    delegatedPhases:
      capability === "opus-sol-medium"
        ? OPUS_SOL_DELEGATED_PHASES
        : ROOT_DELEGATED_PHASES,
    directPhases,
    requirement
  }
});

export const decideRootCapability = (
  input: RootCapabilityInput
): RootCapabilityDecision => {
  if (!input.boundedWork) {
    return block("bounded-work-required", "sonnet-terra-medium");
  }
  if (!input.executablePlan) {
    return block("executable-plan-required", "sonnet-terra-medium");
  }
  if (input.escalation !== undefined) {
    return input.availableCapabilities.includes("opus-sol-medium")
      ? success("opus-sol-medium", "opus-sol-required", [])
      : block(input.escalation, "opus-sol-medium");
  }
  if (input.availableCapabilities.includes("opus-sol-medium")) {
    return success("opus-sol-medium", "sonnet-terra-baseline", ["exploration"]);
  }
  if (input.availableCapabilities.includes("sonnet-terra-medium")) {
    return success("sonnet-terra-medium", "sonnet-terra-baseline", []);
  }
  return block("no-available-capability", "sonnet-terra-medium");
};
