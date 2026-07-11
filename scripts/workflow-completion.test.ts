// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";

import { evaluateCompletion } from "./workflow-completion.js";
import { evaluateValidation } from "./workflow-validation.js";

test("aggregate validation clearance gates publication", () => {
  const failedValidation = evaluateValidation({
    historicalIdentityLedger: [],
    results: [
      {
        executor: "validation-executor",
        executorId: "focused",
        outcome: "passed",
        phase: "focused"
      },
      {
        disposition: "discarded",
        executor: "validation-executor",
        executorId: "integrated",
        outcome: "failed",
        phase: "integrated"
      }
    ]
  });
  const passedValidation = evaluateValidation({
    historicalIdentityLedger: [],
    results: [
      {
        executor: "validation-executor",
        executorId: "focused",
        outcome: "passed",
        phase: "focused"
      },
      {
        executor: "validation-executor",
        executorId: "integrated",
        outcome: "passed",
        phase: "integrated"
      }
    ]
  });
  const blocked = evaluateCompletion({
    evidenceComplete: false,
    historicalIdentityLedger: [],
    ownerFix: "missing",
    publicationAuthorized: false,
    reviewClearance: { blockers: ["security finding requires a source fix"] },
    selectedRecordUpdated: false,
    validationClearance: failedValidation,
    workers: [
      { files: ["src/a.ts"], id: "writer", mode: "writer", status: "passed" }
    ]
  });
  expect(blocked.value).toBeUndefined();
  const ready = evaluateCompletion({
    evidenceComplete: true,
    historicalIdentityLedger: [],
    ownerFix: "passed",
    publicationAuthorized: true,
    reviewClearance: { blockers: [], value: "cleared" },
    selectedRecordUpdated: true,
    validationClearance: passedValidation,
    workers: [
      { files: ["src/a.ts"], id: "writer", mode: "writer", status: "passed" }
    ]
  });
  expect(ready).toEqual({ blockers: [], value: "publish" });
  const missingRereview = evaluateCompletion({
    evidenceComplete: true,
    historicalIdentityLedger: [],
    ownerFix: "passed",
    publicationAuthorized: true,
    reviewClearance: {
      blockers: ["source fixes require a fresh passed re-review"]
    },
    selectedRecordUpdated: true,
    validationClearance: passedValidation,
    workers: [
      { files: ["src/a.ts"], id: "writer", mode: "writer", status: "passed" }
    ]
  });
  expect(missingRereview.blockers).toContain("review clearance is blocked");
});

test("missing focused validation blocks completion", () => {
  // Given
  const validationClearance = evaluateValidation({
    historicalIdentityLedger: [],
    results: [
      {
        executor: "validation-executor",
        executorId: "integrated",
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
      { files: ["src/a.ts"], id: "writer", mode: "writer", status: "passed" }
    ]
  });

  // Then
  expect(completion.blockers).toContain("validation clearance is blocked");
});

test("failed integrated validation blocks completion", () => {
  // Given
  const validationClearance = evaluateValidation({
    historicalIdentityLedger: [],
    results: [
      {
        executor: "validation-executor",
        executorId: "focused",
        outcome: "passed",
        phase: "focused"
      },
      {
        disposition: "discarded",
        executor: "validation-executor",
        executorId: "integrated",
        outcome: "failed",
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
      { files: ["src/a.ts"], id: "writer", mode: "writer", status: "passed" }
    ]
  });

  // Then
  expect(completion.blockers).toContain("validation clearance is blocked");
});

test("invalid failed leaf recovery blocks completion", () => {
  // Given
  const validationClearance = evaluateValidation({
    historicalIdentityLedger: [],
    results: [
      {
        executor: "validation-executor",
        executorId: "focused",
        outcome: "passed",
        phase: "focused"
      },
      {
        executor: "validation-executor",
        executorId: "integrated",
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
      {
        disposition: "retained",
        files: [],
        id: "review-1",
        mode: "read-only",
        phase: "review",
        remediation: "changed",
        replacement: { id: "review-2", status: "passed" },
        status: "failed"
      }
    ]
  });

  // Then
  expect(completion.blockers).toContain(
    "review-1: failed leaf recovery requires discarded disposition"
  );
});

test("historical replacement identity reuse blocks completion", () => {
  // Given
  const validationClearance = evaluateValidation({
    historicalIdentityLedger: [],
    results: [
      {
        executor: "validation-executor",
        executorId: "focused",
        outcome: "passed",
        phase: "focused"
      },
      {
        executor: "validation-executor",
        executorId: "integrated",
        outcome: "passed",
        phase: "integrated"
      }
    ]
  });

  // When
  const completion = evaluateCompletion({
    evidenceComplete: true,
    historicalIdentityLedger: ["review-2"],
    ownerFix: "not-needed",
    publicationAuthorized: true,
    reviewClearance: { blockers: [], value: "cleared" },
    selectedRecordUpdated: true,
    validationClearance,
    workers: [
      {
        disposition: "discarded",
        files: [],
        id: "review-1",
        mode: "read-only",
        phase: "review",
        remediation: "changed",
        replacement: { id: "review-2", status: "passed" },
        status: "failed"
      }
    ]
  });

  // Then
  expect(completion.blockers).toContain(
    "review-1: failed leaf recovery requires a fresh identity"
  );
});
