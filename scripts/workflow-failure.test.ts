// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";

import {
  FAILED_LEAF_PHASES,
  evaluateFailedLeafRecovery
} from "./workflow-failure.js";

test("retained failed leaves block recovery", () => {
  // Given
  const leaves = [
    {
      disposition: "retained",
      identity: "implementation-1",
      outcome: "failed",
      phase: "implementation",
      remediation: "changed",
      replacement: { identity: "implementation-2", outcome: "passed" }
    }
  ] as const;

  // When
  const decision = evaluateFailedLeafRecovery({
    historicalIdentityLedger: [],
    leaves
  });

  // Then
  expect(decision.blockers).toContainEqual({
    index: 0,
    kind: "failed-leaf-discard-required",
    phase: "implementation"
  });
});

test("unchanged remediation blocks failed leaf recovery", () => {
  // Given
  const leaves = [
    {
      disposition: "discarded",
      identity: "review-1",
      outcome: "failed",
      phase: "review",
      remediation: "unchanged",
      replacement: { identity: "review-2", outcome: "passed" }
    }
  ] as const;

  // When
  const decision = evaluateFailedLeafRecovery({
    historicalIdentityLedger: [],
    leaves
  });

  // Then
  expect(decision.blockers).toContainEqual({
    index: 0,
    kind: "changed-remediation-required",
    phase: "review"
  });
});

test("blank failed and replacement identities block recovery", () => {
  // Given
  const leaves = [
    {
      disposition: "discarded",
      identity: "   ",
      outcome: "failed",
      phase: "exploration",
      remediation: "changed",
      replacement: { identity: "   ", outcome: "passed" }
    }
  ] as const;

  // When
  const decision = evaluateFailedLeafRecovery({
    historicalIdentityLedger: [],
    leaves
  });

  // Then
  expect(decision.blockers).toContainEqual({
    index: 0,
    kind: "leaf-identity-required",
    phase: "exploration"
  });
  expect(decision.blockers).toContainEqual({
    index: 0,
    kind: "replacement-identity-required",
    phase: "exploration"
  });
});

test("a replacement cannot reuse its failed leaf identity", () => {
  // Given
  const leaves = [
    {
      disposition: "discarded",
      identity: "validation-1",
      outcome: "failed",
      phase: "validation",
      remediation: "changed",
      replacement: { identity: " validation-1 ", outcome: "passed" }
    }
  ] as const;

  // When
  const decision = evaluateFailedLeafRecovery({
    historicalIdentityLedger: [],
    leaves
  });

  // Then
  expect(decision.blockers).toContainEqual({
    index: 0,
    kind: "fresh-leaf-identity-required",
    phase: "validation"
  });
});

test("a replacement identity must be globally fresh in every phase", () => {
  // Given
  const phases = FAILED_LEAF_PHASES;

  // When
  const decisions = phases.map((phase) =>
    evaluateFailedLeafRecovery({
      historicalIdentityLedger: ["replacement-1"],
      leaves: [
        {
          disposition: "discarded",
          identity: `${phase}-1`,
          outcome: "failed",
          phase,
          remediation: "changed",
          replacement: { identity: "replacement-1", outcome: "passed" }
        }
      ]
    })
  );

  // Then
  for (const [index, decision] of decisions.entries()) {
    expect(decision.blockers).toContainEqual({
      index: 0,
      kind: "fresh-leaf-identity-required",
      phase: phases[index]
    });
  }
});

test("discarded leaves clear after changed remediation and a fresh passed replacement", () => {
  // Given
  const leaves = [
    {
      disposition: "discarded",
      identity: "implementation-1",
      outcome: "failed",
      phase: "implementation",
      remediation: "changed",
      replacement: { identity: "implementation-2", outcome: "passed" }
    }
  ] as const;

  // When
  const decision = evaluateFailedLeafRecovery({
    historicalIdentityLedger: ["exploration-1", "review-1"],
    leaves
  });

  // Then
  expect(decision).toEqual({ blockers: [], value: "cleared" });
});

test("current failed and replacement identities clear when omitted from history", () => {
  // Given
  const leaves = [
    {
      disposition: "discarded",
      identity: "review-1",
      outcome: "failed",
      phase: "review",
      remediation: "changed",
      replacement: { identity: "review-2", outcome: "passed" }
    }
  ] as const;

  // When
  const decision = evaluateFailedLeafRecovery({
    historicalIdentityLedger: ["implementation-1"],
    leaves
  });

  // Then
  expect(decision).toEqual({ blockers: [], value: "cleared" });
});
