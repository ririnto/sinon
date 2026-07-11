// -*- coding: utf-8 -*-

import type { WorkflowDecision } from "./workflow-decision.js";
import type { WorkflowPolicy } from "./workflow-policy-schema.js";
import { parseWorkflowPolicyObject } from "./workflow-policy-schema.js";

export type { WorkflowPolicy } from "./workflow-policy-schema.js";

/** Parse the sole fenced workflow-policy JSON object from a canonical document. */
export const parseWorkflowPolicyDocument = (
  document: string
): WorkflowDecision<WorkflowPolicy> => {
  const matches = [
    ...document.matchAll(/```workflow-policy\r?\n(?<policy>[\s\S]*?)\r?\n```/gu)
  ];
  if (matches.length !== 1) {
    return { blockers: ["workflow-policy-block-count-invalid"] };
  }
  const [match] = matches;
  if (match === undefined) {
    return { blockers: ["workflow-policy-block-count-invalid"] };
  }
  try {
    return parseWorkflowPolicyObject(JSON.parse(match.groups?.policy ?? ""));
  } catch {
    return { blockers: ["workflow-policy-json-invalid"] };
  }
};
