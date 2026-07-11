// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";

import { evaluateFanIn } from "./workflow-routing.js";

test("non-overlapping writers can complete fan-in", () => {
  const result = evaluateFanIn({
    historicalIdentityLedger: [],
    workers: [
      { files: ["src/a.ts"], id: "a", mode: "writer", status: "passed" },
      { files: ["src/b.ts"], id: "b", mode: "writer", status: "passed" }
    ]
  });
  expect(result).toEqual({ blockers: [], value: "complete" });
});

test("current worker identities must be nonblank and globally fresh", () => {
  // Given
  const duplicateWorkers = [
    { files: ["src/a.ts"], id: "worker-1", mode: "writer", status: "passed" },
    {
      files: ["src/b.ts"],
      id: " worker-1 ",
      mode: "writer",
      status: "passed"
    }
  ] as const;

  // When
  const duplicate = evaluateFanIn({
    historicalIdentityLedger: [],
    workers: duplicateWorkers
  });
  const blank = evaluateFanIn({
    historicalIdentityLedger: [],
    workers: [
      { files: ["src/a.ts"], id: "   ", mode: "writer", status: "passed" }
    ]
  });

  // Then
  expect(duplicate.blockers).toContain(
    "worker-1: worker identity must be globally fresh"
  );
  expect(blank.blockers).toContain("   : worker identity is required");
});

test("overlapping writers must be serialized", () => {
  const result = evaluateFanIn({
    historicalIdentityLedger: [],
    workers: [
      { files: ["src/shared.ts"], id: "a", mode: "writer", status: "passed" },
      { files: ["src/shared.ts"], id: "b", mode: "writer", status: "passed" }
    ]
  });
  expect(result.blockers[0]).toContain("overlapping writer ownership");
  const ancestor = evaluateFanIn({
    historicalIdentityLedger: [],
    workers: [
      { files: ["src/"], id: "directory", mode: "writer", status: "passed" },
      { files: ["./src/a.ts"], id: "file", mode: "writer", status: "passed" }
    ]
  });
  expect(ancestor.blockers[0]).toContain("overlapping writer ownership");
});

test("writers require an explicit ownership scope", () => {
  const result = evaluateFanIn({
    historicalIdentityLedger: [],
    workers: [{ files: [], id: "writer", mode: "writer", status: "passed" }]
  });
  expect(result.blockers[0]).toContain("ownership scope is missing");
});

test("failed and missing workers block full fan-in", () => {
  const result = evaluateFanIn({
    historicalIdentityLedger: [],
    workers: [
      {
        disposition: "retained",
        files: [],
        id: "failed",
        mode: "read-only",
        phase: "exploration",
        remediation: "unchanged",
        status: "failed"
      },
      { files: [], id: "missing", mode: "read-only", status: "missing" }
    ]
  });
  expect(result.blockers).toContain("missing: worker result is missing");
  expect(result.value).toBeUndefined();
});

test("invalid failed leaf recovery blocks fan-in", () => {
  // Given
  const workers = [
    {
      disposition: "retained",
      files: [],
      id: "exploration-1",
      mode: "read-only",
      phase: "exploration",
      remediation: "changed",
      replacement: { id: "exploration-2", status: "passed" },
      status: "failed"
    }
  ] as const;

  // When
  const result = evaluateFanIn({ historicalIdentityLedger: [], workers });

  // Then
  expect(result.blockers).toContain(
    "exploration-1: failed leaf recovery requires discarded disposition"
  );
});

test("fan-in separates historical and current identities", () => {
  // Given
  const recoveredWorker = {
    disposition: "discarded",
    files: [],
    id: "review-1",
    mode: "read-only",
    phase: "review",
    remediation: "changed",
    replacement: { id: "review-2", status: "passed" },
    status: "failed"
  } as const;

  // When
  const reused = evaluateFanIn({
    historicalIdentityLedger: ["review-2"],
    workers: [recoveredWorker]
  });
  const fresh = evaluateFanIn({
    historicalIdentityLedger: ["implementation-1"],
    workers: [recoveredWorker]
  });

  // Then
  expect(reused.blockers).toContain(
    "review-1: failed leaf recovery requires a fresh identity"
  );
  expect(fresh).toEqual({ blockers: [], value: "complete" });
});

test("executors participate in fan-in without source ownership", () => {
  const result = evaluateFanIn({
    historicalIdentityLedger: [],
    workers: [
      { files: ["src/a.ts"], id: "writer", mode: "writer", status: "passed" },
      { files: [], id: "checks", mode: "executor", status: "passed" }
    ]
  });
  expect(result).toEqual({ blockers: [], value: "complete" });
  const ownedExecutor = evaluateFanIn({
    historicalIdentityLedger: [],
    workers: [
      { files: ["src/a.ts"], id: "checks", mode: "executor", status: "passed" }
    ]
  });
  expect(ownedExecutor.blockers).toContain(
    "checks: executor workers may not declare source ownership"
  );
});
