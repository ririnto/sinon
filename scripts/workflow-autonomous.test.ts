// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";

import { classifyAutonomousFailure } from "./workflow-autonomous.js";

const recoveredImplementationFailure = (remediation: "changed" | "unchanged") =>
  ({
    disposition: "discarded",
    identity: "implementation-1",
    outcome: "failed",
    phase: "implementation",
    remediation,
    replacement: { identity: "implementation-2", outcome: "passed" }
  }) as const;

test("terminal failures stop without consuming retry budget", () => {
  // Given
  const input = {
    failedCycles: 1,
    failure: { kind: "authority" }
  } as const;

  // When
  const decision = classifyAutonomousFailure(input);

  // Then
  expect(decision.value).toEqual({
    action: "stop",
    consumesRetryBudget: false,
    remainingRetries: 1
  });
});

test("invalid recovery stops before it consumes a retry", () => {
  // Given
  const input = {
    failedCycles: 0,
    failure: {
      historicalIdentityLedger: [],
      kind: "retryable",
      leaf: recoveredImplementationFailure("unchanged")
    }
  } as const;

  // When
  const decision = classifyAutonomousFailure(input);

  // Then
  expect(decision.value).toEqual({
    action: "stop",
    consumesRetryBudget: false,
    remainingRetries: 2
  });
});

test("zero prior retryable failures permit one recovered retry", () => {
  // Given
  const input = {
    failedCycles: 0,
    failure: {
      historicalIdentityLedger: [],
      kind: "retryable",
      leaf: recoveredImplementationFailure("changed")
    }
  } as const;

  // When
  const decision = classifyAutonomousFailure(input);

  // Then
  expect(decision.value).toEqual({
    action: "retry",
    consumesRetryBudget: true,
    remainingRetries: 1
  });
});

test("one prior retryable failure consumes the final retry budget", () => {
  // Given
  const input = {
    failedCycles: 1,
    failure: {
      historicalIdentityLedger: [],
      kind: "retryable",
      leaf: recoveredImplementationFailure("changed")
    }
  } as const;

  // When
  const decision = classifyAutonomousFailure(input);

  // Then
  expect(decision.value).toEqual({
    action: "stop",
    consumesRetryBudget: true,
    remainingRetries: 0
  });
});

test("failed cycle count must be a supported prior-failure state", () => {
  // Given
  const invalidCounts = [-1, 0.5, 2] as const;

  // When
  const decisions = invalidCounts.map((failedCycles) =>
    classifyAutonomousFailure({
      failedCycles,
      failure: {
        historicalIdentityLedger: [],
        kind: "retryable",
        leaf: recoveredImplementationFailure("changed")
      }
    })
  );

  // Then
  for (const decision of decisions) {
    expect(decision.blockers).toContain(
      "failedCycles must be an integer from zero through one"
    );
    expect(decision.value).toBeUndefined();
  }
});
