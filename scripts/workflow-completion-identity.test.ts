// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";

import { evaluateCompletion } from "./workflow-completion.js";
import { evaluateValidation } from "./workflow-validation.js";

test("blocks publication when a current worker reuses a validation executor identity", () => {
  // Given
  const validationClearance = evaluateValidation({
    historicalIdentityLedger: [],
    results: [
      {
        executor: "validation-executor",
        executorId: "writer-1",
        outcome: "passed",
        phase: "focused"
      },
      {
        executor: "validation-executor",
        executorId: "integrated-1",
        outcome: "passed",
        phase: "integrated"
      }
    ]
  });

  // When
  const completion = evaluateCompletion({
    evidenceComplete: true,
    historicalIdentityLedger: [],
    ownerFix: "not-needed",
    publicationAuthorized: true,
    reviewClearance: { blockers: [], value: "cleared" },
    selectedRecordUpdated: true,
    validationClearance,
    workers: [
      { files: ["src/a.ts"], id: "writer-1", mode: "writer", status: "passed" }
    ]
  });

  // Then
  expect(completion.value).toBeUndefined();
  expect(completion.blockers).toContain(
    "writer-1: worker identity must be globally fresh"
  );
});

test("blocks publication when a worker reuses a validation retry identity", () => {
  // Given
  const validationClearance = evaluateValidation({
    historicalIdentityLedger: [],
    results: [
      {
        disposition: "discarded",
        executor: "validation-executor",
        executorId: "focused-1",
        outcome: "failed",
        phase: "focused",
        retry: {
          changedRemediation: true,
          executorId: "writer-1",
          outcome: "passed"
        }
      },
      {
        executor: "validation-executor",
        executorId: "integrated-1",
        outcome: "passed",
        phase: "integrated"
      }
    ]
  });

  // When
  const completion = evaluateCompletion({
    evidenceComplete: true,
    historicalIdentityLedger: [],
    ownerFix: "not-needed",
    publicationAuthorized: true,
    reviewClearance: { blockers: [], value: "cleared" },
    selectedRecordUpdated: true,
    validationClearance,
    workers: [
      { files: ["src/a.ts"], id: "writer-1", mode: "writer", status: "passed" }
    ]
  });

  // Then
  expect(completion.value).toBeUndefined();
  expect(completion.blockers).toContain(
    "writer-1: worker identity must be globally fresh"
  );
});
