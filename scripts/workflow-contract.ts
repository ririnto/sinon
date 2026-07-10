// -*- coding: utf-8 -*-

import path from "node:path";

/** Supported review-publication hosts. */
export type ReviewHost = "github" | "gitlab" | "local";

/** One worker result used for fan-in and ownership checks. */
export interface WorkerResult {
  files: readonly string[];
  id: string;
  mode: "read-only" | "writer";
  status: "failed" | "missing" | "passed";
}

/** Inputs that determine scoped versus general implementation routing. */
export interface ImplementationScope {
  architectureDecision: boolean;
  crossesBoundary: boolean;
  exhaustiveFileSet: boolean;
  fileCount: number;
  needsAffectedSetDiscovery: boolean;
}

/** Review and validation state required before publication. */
export interface CompletionState {
  integratedValidation: "failed" | "missing" | "passed";
  openFindings: number;
  ownerFix: "missing" | "not-needed" | "passed";
  publicationAuthorized: boolean;
  rereview: "missing" | "not-needed" | "passed";
  workers: readonly WorkerResult[];
}

/** Evidence used to select one review host. */
export interface HostEvidence {
  existingRecord?: ReviewHost;
  explicit?: ReviewHost;
  policy?: ReviewHost;
  remoteCandidates: readonly ReviewHost[];
  upstream?: ReviewHost;
}

/** Deterministic workflow decision with blockers. */
export interface WorkflowDecision<T> {
  blockers: readonly string[];
  value?: T;
}

const normalizeOwnershipPath = (value: string): string => {
  const normalized = path.posix.normalize(value.replaceAll("\\", "/"));
  return normalized.startsWith("./") ? normalized.slice(2) : normalized;
};

const ownershipOverlaps = (left: string, right: string): boolean => {
  const first = normalizeOwnershipPath(left).replace(/\/$/u, "");
  const second = normalizeOwnershipPath(right).replace(/\/$/u, "");
  return (
    first === "." ||
    second === "." ||
    first === second ||
    first.startsWith(`${second}/`) ||
    second.startsWith(`${first}/`)
  );
};

/** Select the correct implementation leaf from affected-set evidence. */
export const selectImplementationAgent = (
  scope: ImplementationScope
): WorkflowDecision<"implementation" | "scoped-implementer"> => {
  if (
    scope.needsAffectedSetDiscovery ||
    scope.architectureDecision ||
    scope.crossesBoundary
  ) {
    return { blockers: [], value: "implementation" };
  }
  if (!scope.exhaustiveFileSet) {
    return {
      blockers: [
        "scope exhaustiveness requires read-only exploration or planning"
      ]
    };
  }
  if (scope.fileCount > 3) {
    return { blockers: [], value: "implementation" };
  }
  return { blockers: [], value: "scoped-implementer" };
};

/** Validate writer ownership and complete worker fan-in. */
export const evaluateFanIn = (
  workers: readonly WorkerResult[]
): WorkflowDecision<"complete"> => {
  const blockers: string[] = [];
  for (const worker of workers) {
    if (worker.status !== "passed") {
      blockers.push(`${worker.id}: worker result is ${worker.status}`);
    }
  }
  const writers = workers.filter((worker) => worker.mode === "writer");
  for (const writer of writers) {
    if (writer.files.length === 0) {
      blockers.push(`${writer.id}: writer ownership scope is missing`);
    }
  }
  for (const [index, writer] of writers.entries()) {
    for (const other of writers.slice(index + 1)) {
      const overlap = writer.files.filter((file) =>
        other.files.some((candidate) => ownershipOverlaps(file, candidate))
      );
      if (overlap.length > 0) {
        blockers.push(
          `${writer.id} and ${other.id}: overlapping writer ownership ${overlap.join(", ")}`
        );
      }
    }
  }
  return blockers.length > 0
    ? { blockers }
    : { blockers: [], value: "complete" };
};

/** Select one publication host without preloading multiple host branches. */
export const selectReviewHost = (
  evidence: HostEvidence
): WorkflowDecision<ReviewHost> => {
  for (const candidate of [
    evidence.explicit,
    evidence.existingRecord,
    evidence.policy,
    evidence.upstream
  ]) {
    if (candidate !== undefined) {
      return { blockers: [], value: candidate };
    }
  }
  const candidates = [...new Set(evidence.remoteCandidates)];
  if (candidates.length === 1) {
    return { blockers: [], value: candidates[0] };
  }
  if (candidates.length > 1) {
    return {
      blockers: [
        "multiple publication hosts remain plausible; request a focused host choice"
      ]
    };
  }
  return {
    blockers: ["no supported publication host or local review policy was found"]
  };
};

/** Return the one optional reference allowed for a selected remote host. */
export const referenceForHost = (host: ReviewHost): string | undefined => {
  if (host === "github") {
    return "references/github.md";
  }
  if (host === "gitlab") {
    return "references/gitlab.md";
  }
  return undefined;
};

/** Evaluate full fan-in, owner-fix, re-review, validation, and publication gates. */
export const evaluateCompletion = (
  state: CompletionState
): WorkflowDecision<"publish"> => {
  const blockers = [...evaluateFanIn(state.workers).blockers];
  if (state.openFindings > 0) {
    blockers.push(`${state.openFindings} review finding(s) remain open`);
  }
  if (state.ownerFix === "missing") {
    blockers.push("owning-writer fix is missing");
  }
  if (state.rereview === "missing") {
    blockers.push("re-review is missing");
  }
  if (state.ownerFix === "passed" && state.rereview !== "passed") {
    blockers.push("owning-writer fixes require a passed re-review");
  }
  if (state.integratedValidation !== "passed") {
    blockers.push(`integrated validation is ${state.integratedValidation}`);
  }
  if (!state.publicationAuthorized) {
    blockers.push("root-session publication authority is missing");
  }
  return blockers.length > 0
    ? { blockers }
    : { blockers: [], value: "publish" };
};
