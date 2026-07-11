// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";

import "./workflow-fan-in-cases.js";
import { WORKFLOW_POLICY } from "./workflow-policy-contract.js";
import {
  decideCallerExploration,
  decideWorktree,
  decideWriterCoordination,
  selectImplementationAgent
} from "./workflow-routing.js";

test("discovery-capable writers require recorded read-only preflight before parallel routing", () => {
  const unresolved = decideWriterCoordination({
    explorationAvailable: true,
    writers: [
      {
        discoveryCapable: true,
        files: ["src/a.ts"],
        id: "a",
        preflight: "missing"
      },
      {
        discoveryCapable: false,
        files: ["src/b.ts"],
        id: "b",
        preflight: "not-needed"
      }
    ]
  });
  expect(unresolved.value).toBe("preflight");
  const verified = decideWriterCoordination({
    explorationAvailable: true,
    writers: [
      {
        discoveryCapable: true,
        files: ["src/a.ts"],
        id: "a",
        preflight: "recorded"
      },
      {
        discoveryCapable: false,
        files: ["src/b.ts"],
        id: "b",
        preflight: "not-needed"
      }
    ]
  });
  expect(verified.value).toBe("parallel");
});

test("unknown writer scopes serialize when exploration is unavailable", () => {
  const result = decideWriterCoordination({
    explorationAvailable: false,
    writers: [
      {
        discoveryCapable: true,
        files: [],
        id: "a",
        preflight: "missing"
      },
      {
        discoveryCapable: false,
        files: ["src/b.ts"],
        id: "b",
        preflight: "not-needed"
      }
    ]
  });
  expect(result.value).toBe("serialize");
});

test("single-task branches require one owner, criteria set, integration unit, and no concurrent writer", () => {
  expect(
    decideWorktree({
      acceptanceCriteriaSets: 1,
      childCwdBindingGuaranteed: false,
      concurrentWriter: false,
      integrationUnits: 1,
      owners: 1
    }).value
  ).toBe("single-task-branch");
  expect(
    decideWorktree({
      acceptanceCriteriaSets: 2,
      childCwdBindingGuaranteed: true,
      concurrentWriter: true,
      integrationUnits: 1,
      owners: 1
    }).value
  ).toBe("root-worktree");
  expect(
    decideWorktree({
      acceptanceCriteriaSets: 2,
      childCwdBindingGuaranteed: false,
      concurrentWriter: true,
      integrationUnits: 1,
      owners: 1
    }).value
  ).toBe("serialize");
});

test("caller exploration must remain bounded, evidence-producing, read-only, and non-authoritative", () => {
  const accepted = decideCallerExploration({
    evidenceOutput: true,
    question: "Which files own the parser boundary?",
    routingAuthority: false,
    scope: ["src/parser"],
    tools: ["Read", "Glob", "Grep"]
  });
  expect(accepted).toEqual({ blockers: [], value: "accepted" });
  const blocked = decideCallerExploration({
    evidenceOutput: false,
    question: "",
    routingAuthority: true,
    scope: [],
    tools: ["Read", "Edit"]
  });
  expect(blocked.blockers).toHaveLength(5);
});

test("scoped implementation requires an exhaustive small file set", () => {
  expect(
    selectImplementationAgent({
      architectureDecision: false,
      crossesBoundary: false,
      exhaustiveFileSet: true,
      fileCount: 2,
      needsAffectedSetDiscovery: false
    }).value
  ).toBe("scoped-implementer");
  expect(
    selectImplementationAgent({
      architectureDecision: false,
      crossesBoundary: false,
      exhaustiveFileSet: false,
      fileCount: 1,
      needsAffectedSetDiscovery: false
    }).blockers[0]
  ).toContain("exploration or planning");
});

test("general implementation owns discovery and cross-boundary work", () => {
  expect(
    selectImplementationAgent({
      architectureDecision: true,
      crossesBoundary: true,
      exhaustiveFileSet: true,
      fileCount: 2,
      needsAffectedSetDiscovery: true
    }).value
  ).toBe("implementer");
  expect(
    selectImplementationAgent({
      architectureDecision: false,
      crossesBoundary: false,
      exhaustiveFileSet: false,
      fileCount: 0,
      needsAffectedSetDiscovery: true
    }).value
  ).toBe("implementer");
});

test("workflow policy keeps implementation and review as phase keys", () => {
  expect(WORKFLOW_POLICY.root.opusSol.delegates).toEqual([
    "implementation",
    "validation",
    "review"
  ]);
  expect(WORKFLOW_POLICY.root.terra.delegates).toContain("implementation");
  expect(WORKFLOW_POLICY.root.terra.delegates).toContain("review");
});
