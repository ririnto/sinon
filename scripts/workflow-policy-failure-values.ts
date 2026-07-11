// -*- coding: utf-8 -*-

import { WORKFLOW_POLICY } from "./workflow-policy-contract.js";
import type { StringList } from "./workflow-policy-types.js";

export type FailurePolicyValues = Readonly<{
  readonly discardRetryRequires: StringList;
  readonly failedCycles: string;
  readonly phases: StringList;
  readonly retryBudget: string;
}>;

const stringValue = (value: unknown): string | undefined =>
  typeof value === "string" ? value : undefined;

const stringList = (value: unknown): StringList | undefined =>
  Array.isArray(value) && value.every((item) => typeof item === "string")
    ? value
    : undefined;

/** Read the canonical failed-leaf lifecycle values from a parsed policy record. */
export const readFailurePolicyValues = (
  failure: Record<string, unknown>
): FailurePolicyValues | undefined => {
  const discardRetryRequires = stringList(failure.discardRetryRequires);
  const failedCycles = stringValue(failure.failedCycles);
  const phases = stringList(failure.phases);
  const retryBudget = stringValue(failure.retryBudget);
  if (
    discardRetryRequires === undefined ||
    failedCycles === undefined ||
    phases === undefined ||
    retryBudget === undefined
  ) {
    return undefined;
  }
  return { discardRetryRequires, failedCycles, phases, retryBudget };
};

/** Compare a parsed list with the canonical ordered policy list. */
export const listMatches = (
  actual: StringList,
  expected: readonly string[]
): boolean =>
  actual.length === expected.length &&
  actual.every((value, index) => value === expected[index]);

/** Confirm the parsed failure lifecycle uses the supported workflow vocabulary. */
export const matchesFailurePolicyVocabulary = (
  values: FailurePolicyValues
): boolean =>
  listMatches(
    values.discardRetryRequires,
    WORKFLOW_POLICY.failure.discardRetryRequires
  ) &&
  values.failedCycles === WORKFLOW_POLICY.failure.failedCycles &&
  listMatches(values.phases, WORKFLOW_POLICY.failure.phases) &&
  values.retryBudget === WORKFLOW_POLICY.failure.retryBudget;
