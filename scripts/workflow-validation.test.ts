// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";

import { evaluateValidation } from "./workflow-validation.js";

test("passes only focused and integrated validation from validation executors", () => {
  // Given
  const results = [
    {
      executor: "validation-executor",
      executorId: "focused-1",
      outcome: "passed",
      phase: "focused"
    },
    {
      executor: "validation-executor",
      executorId: "integrated-1",
      outcome: "passed",
      phase: "integrated"
    }
  ] as const;

  // When
  const decision = evaluateValidation({
    historicalIdentityLedger: [],
    results
  });

  // Then
  expect(decision.blockers).toEqual([]);
  expect(decision.value).toEqual({
    executorIdentityLedger: ["focused-1", "integrated-1"],
    focused: "passed",
    integrated: "passed"
  });
});

test("discards failed executor and requires changed remediation with a fresh retry", () => {
  // Given
  const failed = [
    {
      disposition: "discarded",
      executor: "validation-executor",
      executorId: "focused-1",
      outcome: "failed",
      phase: "focused",
      retry: {
        changedRemediation: false,
        executorId: "focused-1",
        outcome: "passed"
      }
    },
    {
      executor: "validation-executor",
      executorId: "integrated-1",
      outcome: "passed",
      phase: "integrated"
    }
  ] as const;

  // When
  const decision = evaluateValidation({
    historicalIdentityLedger: [],
    results: failed
  });

  // Then
  expect(decision.value).toBeUndefined();
  expect(decision.blockers).toContainEqual({
    executorId: "focused-1",
    kind: "fresh-executor-identity-required",
    phase: "focused"
  });
  expect(decision.blockers).toContainEqual({
    executorId: "focused-1",
    kind: "changed-remediation-required",
    phase: "focused"
  });
});

test("does not treat a recorded validation blocker as success", () => {
  // Given
  const results = [
    {
      executor: "validation-executor",
      executorId: "focused-1",
      outcome: "passed",
      phase: "focused",
      recordedBlocker: true
    },
    {
      executor: "validation-executor",
      executorId: "integrated-1",
      outcome: "passed",
      phase: "integrated"
    }
  ] as const;

  // When
  const decision = evaluateValidation({
    historicalIdentityLedger: [],
    results
  });

  // Then
  expect(decision.value).toBeUndefined();
  expect(decision.blockers).toContainEqual({
    executorId: "focused-1",
    kind: "recorded-validation-blocker",
    phase: "focused"
  });
});

test("requires a failed validation executor to be discarded before retry", () => {
  // Given
  const results = [
    {
      disposition: "retained",
      executor: "validation-executor",
      executorId: "focused-1",
      outcome: "failed",
      phase: "focused",
      retry: {
        changedRemediation: true,
        executorId: "focused-2",
        outcome: "passed"
      }
    },
    {
      executor: "validation-executor",
      executorId: "integrated-1",
      outcome: "passed",
      phase: "integrated"
    }
  ] as const;

  // When
  const decision = evaluateValidation({
    historicalIdentityLedger: [],
    results
  });

  // Then
  expect(decision.blockers).toContainEqual({
    executorId: "focused-1",
    kind: "failed-executor-discard-required",
    phase: "focused"
  });
});

test("rejects executor reuse between focused and integrated validation", () => {
  // Given
  const results = [
    {
      executor: "validation-executor",
      executorId: "executor-1",
      outcome: "passed",
      phase: "focused"
    },
    {
      executor: "validation-executor",
      executorId: "executor-1",
      outcome: "passed",
      phase: "integrated"
    }
  ] as const;

  // When
  const decision = evaluateValidation({
    historicalIdentityLedger: [],
    results
  });

  // Then
  expect(decision.blockers).toContainEqual({
    executorId: "executor-1",
    kind: "fresh-executor-identity-required",
    phase: "integrated"
  });
});

test("rejects whitespace-only executor identities", () => {
  // Given
  const results = [
    {
      executor: "validation-executor",
      executorId: "   ",
      outcome: "passed",
      phase: "focused"
    },
    {
      executor: "validation-executor",
      executorId: "integrated-1",
      outcome: "passed",
      phase: "integrated"
    }
  ] as const;

  // When
  const decision = evaluateValidation({
    historicalIdentityLedger: [],
    results
  });

  // Then
  expect(decision.blockers).toContainEqual({
    executorId: "   ",
    kind: "executor-identity-required",
    phase: "focused"
  });
});

test("rejects a retry identity used by an earlier validation phase", () => {
  // Given
  const results = [
    {
      executor: "validation-executor",
      executorId: "focused-1",
      outcome: "passed",
      phase: "focused"
    },
    {
      disposition: "discarded",
      executor: "validation-executor",
      executorId: "integrated-1",
      outcome: "failed",
      phase: "integrated",
      retry: {
        changedRemediation: true,
        executorId: "focused-1",
        outcome: "passed"
      }
    }
  ] as const;

  // When
  const decision = evaluateValidation({
    historicalIdentityLedger: [],
    results
  });

  // Then
  expect(decision.blockers).toContainEqual({
    executorId: "integrated-1",
    kind: "fresh-executor-identity-required",
    phase: "integrated"
  });
});

test("rejects a retry identity used by another work-item phase", () => {
  // Given
  const results = [
    {
      disposition: "discarded",
      executor: "validation-executor",
      executorId: "focused-1",
      outcome: "failed",
      phase: "focused",
      retry: {
        changedRemediation: true,
        executorId: "implementation-1",
        outcome: "passed"
      }
    },
    {
      executor: "validation-executor",
      executorId: "integrated-1",
      outcome: "passed",
      phase: "integrated"
    }
  ] as const;

  // When
  const decision = evaluateValidation({
    historicalIdentityLedger: ["implementation-1"],
    results
  });

  // Then
  expect(decision.blockers).toContainEqual({
    executorId: "focused-1",
    kind: "fresh-executor-identity-required",
    phase: "focused"
  });
});
