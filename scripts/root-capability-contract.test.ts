// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";

import { decideRootCapability } from "./root-capability-contract.js";
import type { RootCapabilityEscalation } from "./root-capability-contract.js";

const delegatedPhases = ["implementation", "validation", "review"] as const;

const hardEscalations = [
  "unresolved-ambiguity",
  "security-sensitive-decision",
  "contradictory-fan-in",
  "high-risk-publication"
] as const satisfies readonly RootCapabilityEscalation[];

test("admits Terra for a bounded root plan", () => {
  // Given
  const availableCapabilities = ["sonnet-terra-medium"] as const;

  // When
  const result = decideRootCapability({
    availableCapabilities,
    boundedWork: true,
    escalation: undefined,
    executablePlan: true
  });

  // Then
  expect(result).toEqual({
    blockers: [],
    value: {
      capability: "sonnet-terra-medium",
      delegatedPhases: ["exploration", ...delegatedPhases],
      directPhases: [],
      requirement: "sonnet-terra-baseline"
    }
  });
});

test("prefers Opus/Sol for ordinary bounded planned work", () => {
  // Given
  const availableCapabilities = [
    "sonnet-terra-medium",
    "opus-sol-medium"
  ] as const;

  // When
  const result = decideRootCapability({
    availableCapabilities,
    boundedWork: true,
    escalation: undefined,
    executablePlan: true
  });

  // Then
  expect(result).toEqual({
    blockers: [],
    value: {
      capability: "opus-sol-medium",
      delegatedPhases,
      directPhases: ["exploration"],
      requirement: "sonnet-terra-baseline"
    }
  });
});

for (const escalation of hardEscalations) {
  test(`delegates only Opus/Sol phases for ${escalation}`, () => {
    // Given
    const availableCapabilities = [
      "sonnet-terra-medium",
      "opus-sol-medium"
    ] as const;

    // When
    const result = decideRootCapability({
      availableCapabilities,
      boundedWork: true,
      escalation,
      executablePlan: true
    });

    // Then
    expect(result).toEqual({
      blockers: [],
      value: {
        capability: "opus-sol-medium",
        delegatedPhases,
        directPhases: [],
        requirement: "opus-sol-required"
      }
    });
  });
}

for (const escalation of hardEscalations) {
  test(`does not fall back to Terra for ${escalation}`, () => {
    // Given
    const availableCapabilities = ["sonnet-terra-medium"] as const;

    // When
    const result = decideRootCapability({
      availableCapabilities,
      boundedWork: true,
      escalation,
      executablePlan: true
    });

    // Then
    expect(result.blockers).toEqual([
      {
        handoffOwner: "eligible-root-or-human",
        reason: escalation,
        requiredCapability: "opus-sol-medium"
      }
    ]);
    expect(result.value).toBeUndefined();
  });
}

test("blocks Terra when work is unbounded", () => {
  // Given
  const availableCapabilities = ["sonnet-terra-medium"] as const;

  // When
  const result = decideRootCapability({
    availableCapabilities,
    boundedWork: false,
    escalation: undefined,
    executablePlan: true
  });

  // Then
  expect(result.blockers).toHaveLength(1);
  expect(result.value).toBeUndefined();
});

test("blocks Terra when the plan is not executable", () => {
  // Given
  const availableCapabilities = ["sonnet-terra-medium"] as const;

  // When
  const result = decideRootCapability({
    availableCapabilities,
    boundedWork: true,
    escalation: undefined,
    executablePlan: false
  });

  // Then
  expect(result.blockers).toHaveLength(1);
  expect(result.value).toBeUndefined();
});

test("blocks when no root capability is available", () => {
  // Given
  const availableCapabilities = [] as const;

  // When
  const result = decideRootCapability({
    availableCapabilities,
    boundedWork: true,
    escalation: undefined,
    executablePlan: true
  });

  // Then
  expect(result.blockers).toHaveLength(1);
  expect(result.value).toBeUndefined();
});
