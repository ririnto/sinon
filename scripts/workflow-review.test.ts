// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";

import { decideReviewFindings } from "./workflow-review.js";

test("critical review findings require source fixes and fresh re-review", () => {
  const open = decideReviewFindings({
    findings: [{ category: "security", state: "open" }],
    rereview: "missing"
  });
  expect(open.blockers).toContain("security finding requires a source fix");
  const fixedWithoutRereview = decideReviewFindings({
    findings: [{ category: "security", state: "source-fixed" }],
    rereview: "missing"
  });
  expect(fixedWithoutRereview.blockers).toContain(
    "source fixes require a fresh passed re-review"
  );
});

test("non-blocking low findings need a durable root disposition", () => {
  const missingRecord = decideReviewFindings({
    findings: [
      {
        category: "low",
        nonBlocking: true,
        rootDisposition: {
          durableRecord: false,
          owner: "root",
          rationale: "cosmetic",
          status: "accepted"
        },
        state: "root-disposed"
      }
    ],
    rereview: "not-needed"
  });
  expect(missingRecord.blockers).toContain(
    "non-blocking low finding requires a durable root disposition record"
  );
  const accepted = decideReviewFindings({
    findings: [
      {
        category: "low",
        nonBlocking: true,
        rootDisposition: {
          durableRecord: true,
          owner: "root",
          rationale: "cosmetic and behaviorally neutral",
          status: "accepted"
        },
        state: "root-disposed"
      }
    ],
    rereview: "not-needed"
  });
  expect(accepted).toEqual({ blockers: [], value: "cleared" });
});

test("deferred non-blocking low findings clear only with a durable disposition", () => {
  // Given
  const input = {
    findings: [
      {
        category: "low" as const,
        nonBlocking: true,
        rootDisposition: {
          durableRecord: true,
          owner: "root",
          rationale: "follow-up has no release impact",
          status: "deferred" as const
        },
        state: "root-disposed" as const
      }
    ],
    rereview: "not-needed" as const
  };

  // When
  const decision = decideReviewFindings(input);

  // Then
  expect(decision).toEqual({ blockers: [], value: "cleared" });
});
