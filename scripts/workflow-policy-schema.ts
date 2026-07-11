// -*- coding: utf-8 -*-

import type { WorkflowDecision } from "./workflow-decision.js";
import type {
  PolicyRecords,
  StringList,
  WorkflowPolicy
} from "./workflow-policy-types.js";
import { buildWorkflowPolicy } from "./workflow-policy-values.js";

export type {
  PolicyRecords,
  StringList,
  WorkflowPolicy
} from "./workflow-policy-types.js";

const topKeys = [
  "schemaVersion",
  "root",
  "failure",
  "validation",
  "review",
  "completion",
  "documentEdits",
  "records"
];

const isObject = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

const readObject = (
  value: unknown,
  blockers: string[]
): Record<string, unknown> | undefined => {
  if (isObject(value)) {
    return value;
  }
  blockers.push("workflow-policy-keys-invalid");
  return undefined;
};

const hasExactKeys = (
  value: Record<string, unknown>,
  keys: StringList
): boolean =>
  Object.keys(value).length === keys.length &&
  keys.every((key) => Object.hasOwn(value, key));

const readFields = (
  value: Record<string, unknown>,
  keys: StringList,
  blockers: string[]
): boolean => {
  if (hasExactKeys(value, keys)) {
    return true;
  }
  blockers.push("workflow-policy-keys-invalid");
  return false;
};

const readPolicyRecords = (
  policy: Record<string, unknown>,
  blockers: string[]
): PolicyRecords | undefined => {
  const root = readObject(policy.root, blockers);
  const failure = readObject(policy.failure, blockers);
  const validation = readObject(policy.validation, blockers);
  const review = readObject(policy.review, blockers);
  const completion = readObject(policy.completion, blockers);
  const documentEdits = readObject(policy.documentEdits, blockers);
  const records = readObject(policy.records, blockers);
  if (
    root === undefined ||
    failure === undefined ||
    validation === undefined ||
    review === undefined ||
    completion === undefined ||
    documentEdits === undefined ||
    records === undefined
  ) {
    return undefined;
  }
  const backgroundCliFallback = readObject(
    root.backgroundCliFallback,
    blockers
  );
  const terra = readObject(root.terra, blockers);
  const opusSol = readObject(root.opusSol, blockers);
  const unavailable = readObject(root.unavailable, blockers);
  if (
    backgroundCliFallback === undefined ||
    terra === undefined ||
    opusSol === undefined ||
    unavailable === undefined
  ) {
    return undefined;
  }
  return {
    backgroundCliFallback,
    completion,
    documentEdits,
    failure,
    opusSol,
    records,
    review,
    root,
    terra,
    unavailable,
    validation
  };
};

const hasPolicyFields = (records: PolicyRecords, blockers: string[]): boolean =>
  [
    readFields(
      records.root,
      ["backgroundCliFallback", "opusSol", "terra", "unavailable"],
      blockers
    ),
    readFields(
      records.backgroundCliFallback,
      [
        "admission",
        "authority",
        "leafLimit",
        "leafProhibitions",
        "record",
        "unavailableHandoff"
      ],
      blockers
    ),
    readFields(records.terra, ["requires", "delegates"], blockers),
    readFields(
      records.opusSol,
      ["directExploration", "requiredFor", "delegates"],
      blockers
    ),
    readFields(
      records.unavailable,
      ["requiredCapability", "handoffOwner"],
      blockers
    ),
    readFields(records.validation, ["executor", "phases"], blockers),
    readFields(
      records.failure,
      ["discardRetryRequires", "failedCycles", "phases", "retryBudget"],
      blockers
    ),
    readFields(
      records.review,
      ["lowDispositions", "sourceFixRequires"],
      blockers
    ),
    readFields(records.completion, ["requires", "blockedReport"], blockers),
    readFields(records.documentEdits, ["requires", "manualFallback"], blockers),
    readFields(records.records, ["addenda", "selection"], blockers)
  ].every(Boolean);

export const parseWorkflowPolicyObject = (
  parsed: unknown
): WorkflowDecision<WorkflowPolicy> => {
  const blockers: string[] = [];
  const policy = readObject(parsed, blockers);
  if (policy === undefined || !readFields(policy, topKeys, blockers)) {
    return { blockers };
  }
  if (typeof policy.schemaVersion !== "number") {
    return { blockers: [...blockers, "workflow-policy-value-invalid"] };
  }
  const records = readPolicyRecords(policy, blockers);
  if (records === undefined || !hasPolicyFields(records, blockers)) {
    return { blockers };
  }
  const value = buildWorkflowPolicy(records, policy.schemaVersion, blockers);
  return value === undefined ? { blockers } : { blockers: [], value };
};
