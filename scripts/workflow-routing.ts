// -*- coding: utf-8 -*-

import path from "node:path";

import type { WorkflowDecision } from "./workflow-decision.js";
import { evaluateWorkerRecovery } from "./workflow-fan-in-recovery.js";
import type { WorkerRecoveryInput } from "./workflow-fan-in-recovery.js";

export type { WorkerResult } from "./workflow-fan-in-recovery.js";
export type { WorkerRecoveryInput as FanInInput } from "./workflow-fan-in-recovery.js";

/** Bounded writer ownership evaluated before concurrent dispatch. */
export interface WriterScope {
  readonly discoveryCapable: boolean;
  readonly files: readonly string[];
  readonly id: string;
  readonly preflight: "missing" | "not-needed" | "recorded";
}

/** Inputs used to choose parallel, preflight, or serialized writer routing. */
export interface WriterCoordinationInput {
  readonly explorationAvailable: boolean;
  readonly writers: readonly WriterScope[];
}

/** Branch and worktree facts established by the root session. */
export interface WorktreeInput {
  readonly acceptanceCriteriaSets: number;
  readonly childCwdBindingGuaranteed: boolean;
  readonly concurrentWriter: boolean;
  readonly integrationUnits: number;
  readonly owners: number;
}

/** Caller-provided exploration must be a bounded, evidence-only read. */
export interface CallerExplorationInput {
  readonly evidenceOutput: boolean;
  readonly question: string;
  readonly routingAuthority: boolean;
  readonly scope: readonly string[];
  readonly tools: readonly string[];
}

/** Inputs that determine scoped versus general implementation routing. */
export interface ImplementationScope {
  readonly architectureDecision: boolean;
  readonly crossesBoundary: boolean;
  readonly exhaustiveFileSet: boolean;
  readonly fileCount: number;
  readonly needsAffectedSetDiscovery: boolean;
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

const READ_ONLY_EXPLORATION_TOOLS = new Set(["Glob", "Grep", "Read"]);

/** Select the correct implementation leaf from affected-set evidence. */
export const selectImplementationAgent = (
  scope: ImplementationScope
): WorkflowDecision<"implementer" | "scoped-implementer"> => {
  if (
    scope.needsAffectedSetDiscovery ||
    scope.architectureDecision ||
    scope.crossesBoundary
  ) {
    return { blockers: [], value: "implementer" };
  }
  if (!scope.exhaustiveFileSet) {
    return {
      blockers: [
        "scope exhaustiveness requires read-only exploration or planning"
      ]
    };
  }
  if (scope.fileCount > 3) {
    return { blockers: [], value: "implementer" };
  }
  return { blockers: [], value: "scoped-implementer" };
};

/** Validate writer ownership and complete worker fan-in. */
export const evaluateFanIn = (
  input: WorkerRecoveryInput
): WorkflowDecision<"complete"> => {
  const { workers } = input;
  const blockers: string[] = [];
  for (const worker of workers) {
    if (worker.status === "missing") {
      blockers.push(`${worker.id}: worker result is missing`);
    }
  }
  blockers.push(...evaluateWorkerRecovery(input));
  const writers = workers.filter((worker) => worker.mode === "writer");
  for (const executor of workers.filter(
    (worker) => worker.mode === "executor"
  )) {
    if (executor.files.length > 0) {
      blockers.push(
        `${executor.id}: executor workers may not declare source ownership`
      );
    }
  }
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

/** Route concurrent writers only after discovery ownership is bounded and disjoint. */
export const decideWriterCoordination = (
  input: WriterCoordinationInput
): WorkflowDecision<"parallel" | "preflight" | "serialize"> => {
  if (input.writers.length < 2) {
    return { blockers: [], value: "parallel" };
  }
  const hasUnknownScope = input.writers.some(
    (writer) => writer.files.length === 0
  );
  const needsDiscoveryPreflight = input.writers.some(
    (writer) => writer.discoveryCapable && writer.preflight !== "recorded"
  );
  if (hasUnknownScope || needsDiscoveryPreflight) {
    return {
      blockers: [],
      value: input.explorationAvailable ? "preflight" : "serialize"
    };
  }
  for (const [index, writer] of input.writers.entries()) {
    for (const other of input.writers.slice(index + 1)) {
      if (
        writer.files.some((file) =>
          other.files.some((candidate) => ownershipOverlaps(file, candidate))
        )
      ) {
        return { blockers: [], value: "serialize" };
      }
    }
  }
  return { blockers: [], value: "parallel" };
};

/** Allow a shared task branch only when no independent writer can conflict. */
export const decideWorktree = (
  input: WorktreeInput
): WorkflowDecision<"root-worktree" | "serialize" | "single-task-branch"> => {
  if (
    input.owners === 1 &&
    input.acceptanceCriteriaSets === 1 &&
    input.integrationUnits === 1 &&
    !input.concurrentWriter
  ) {
    return { blockers: [], value: "single-task-branch" };
  }
  return input.childCwdBindingGuaranteed
    ? { blockers: [], value: "root-worktree" }
    : { blockers: [], value: "serialize" };
};

/** Admit caller exploration only when it cannot mutate or route the workflow. */
export const decideCallerExploration = (
  input: CallerExplorationInput
): WorkflowDecision<"accepted"> => {
  const blockers: string[] = [];
  if (input.question.trim() === "") {
    blockers.push("caller exploration requires an explicit question");
  }
  if (
    input.scope.length === 0 ||
    input.scope.some((item) => item.trim() === "")
  ) {
    blockers.push("caller exploration requires a bounded scope");
  }
  if (!input.tools.every((tool) => READ_ONLY_EXPLORATION_TOOLS.has(tool))) {
    blockers.push("caller exploration requires read-only tools");
  }
  if (!input.evidenceOutput) {
    blockers.push("caller exploration requires evidence output");
  }
  if (input.routingAuthority) {
    blockers.push("caller exploration may not hold routing authority");
  }
  return blockers.length > 0
    ? { blockers }
    : { blockers: [], value: "accepted" };
};
