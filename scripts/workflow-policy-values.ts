// -*- coding: utf-8 -*-

import type { WorkflowPolicy } from "./workflow-policy-contract.js";
import { WORKFLOW_POLICY } from "./workflow-policy-contract.js";
import {
  listMatches,
  matchesFailurePolicyVocabulary,
  readFailurePolicyValues
} from "./workflow-policy-failure-values.js";
import type { FailurePolicyValues } from "./workflow-policy-failure-values.js";
import type { PolicyRecords, StringList } from "./workflow-policy-types.js";

const identifier = /^[a-z][a-z0-9-]*$/u;

type FallbackValues = Readonly<{
  admission: string;
  authority: string;
  leafLimit: string;
  leafProhibitions: StringList;
  record: string;
  unavailableHandoff: string;
}>;

type PolicyValues = Readonly<{
  addenda: string;
  backgroundCliFallback: FallbackValues;
  blockedReport: string;
  completionRequires: StringList;
  directExploration: string;
  documentEditRequires: StringList;
  executor: string;
  failure: FailurePolicyValues;
  handoffOwner: string;
  lowDispositions: StringList;
  manualFallback: string;
  opusSolDelegates: StringList;
  opusSolRequiredFor: StringList;
  requiredCapability: string;
  selection: string;
  sourceFixRequires: string;
  terraDelegates: StringList;
  terraRequires: StringList;
  validationPhases: StringList;
}>;

const stringValue = (value: unknown): string | undefined =>
  typeof value === "string" ? value : undefined;

const stringList = (value: unknown): StringList | undefined =>
  Array.isArray(value) && value.every((item) => typeof item === "string")
    ? value
    : undefined;

const readFallback = (
  fallback: Record<string, unknown>
): FallbackValues | undefined => {
  const admission = stringValue(fallback.admission);
  const authority = stringValue(fallback.authority);
  const leafLimit = stringValue(fallback.leafLimit);
  const leafProhibitions = stringList(fallback.leafProhibitions);
  const record = stringValue(fallback.record);
  const unavailableHandoff = stringValue(fallback.unavailableHandoff);
  if (
    admission === undefined ||
    authority === undefined ||
    leafLimit === undefined ||
    leafProhibitions === undefined ||
    record === undefined ||
    unavailableHandoff === undefined
  ) {
    return undefined;
  }
  return {
    admission,
    authority,
    leafLimit,
    leafProhibitions,
    record,
    unavailableHandoff
  };
};

const readPolicyValues = (records: PolicyRecords): PolicyValues | undefined => {
  const backgroundCliFallback = readFallback(records.backgroundCliFallback);
  const terraRequires = stringList(records.terra.requires);
  const terraDelegates = stringList(records.terra.delegates);
  const opusSolRequiredFor = stringList(records.opusSol.requiredFor);
  const opusSolDelegates = stringList(records.opusSol.delegates);
  const validationPhases = stringList(records.validation.phases);
  const failure = readFailurePolicyValues(records.failure);
  const lowDispositions = stringList(records.review.lowDispositions);
  const completionRequires = stringList(records.completion.requires);
  const documentEditRequires = stringList(records.documentEdits.requires);
  const directExploration = stringValue(records.opusSol.directExploration);
  const requiredCapability = stringValue(
    records.unavailable.requiredCapability
  );
  const handoffOwner = stringValue(records.unavailable.handoffOwner);
  const executor = stringValue(records.validation.executor);
  const sourceFixRequires = stringValue(records.review.sourceFixRequires);
  const blockedReport = stringValue(records.completion.blockedReport);
  const manualFallback = stringValue(records.documentEdits.manualFallback);
  const addenda = stringValue(records.records.addenda);
  const selection = stringValue(records.records.selection);
  if (
    backgroundCliFallback === undefined ||
    terraRequires === undefined ||
    terraDelegates === undefined ||
    opusSolRequiredFor === undefined ||
    opusSolDelegates === undefined ||
    validationPhases === undefined ||
    failure === undefined ||
    lowDispositions === undefined ||
    completionRequires === undefined ||
    documentEditRequires === undefined ||
    directExploration === undefined ||
    requiredCapability === undefined ||
    handoffOwner === undefined ||
    executor === undefined ||
    sourceFixRequires === undefined ||
    blockedReport === undefined ||
    manualFallback === undefined ||
    addenda === undefined ||
    selection === undefined
  ) {
    return undefined;
  }
  return {
    addenda,
    backgroundCliFallback,
    blockedReport,
    completionRequires,
    directExploration,
    documentEditRequires,
    executor,
    failure,
    handoffOwner,
    lowDispositions,
    manualFallback,
    opusSolDelegates,
    opusSolRequiredFor,
    requiredCapability,
    selection,
    sourceFixRequires,
    terraDelegates,
    terraRequires,
    validationPhases
  };
};

const validateIdentifiers = (values: StringList, blockers: string[]): void => {
  if (!values.every((value) => identifier.test(value))) {
    blockers.push("workflow-policy-identifier-invalid");
  }
};

const matchesBackgroundCliFallback = (values: FallbackValues): boolean =>
  values.admission === WORKFLOW_POLICY.root.backgroundCliFallback.admission &&
  values.authority === WORKFLOW_POLICY.root.backgroundCliFallback.authority &&
  values.leafLimit === WORKFLOW_POLICY.root.backgroundCliFallback.leafLimit &&
  listMatches(
    values.leafProhibitions,
    WORKFLOW_POLICY.root.backgroundCliFallback.leafProhibitions
  ) &&
  values.record === WORKFLOW_POLICY.root.backgroundCliFallback.record &&
  values.unavailableHandoff ===
    WORKFLOW_POLICY.root.backgroundCliFallback.unavailableHandoff;

const matchesVocabulary = (values: PolicyValues): boolean =>
  values.addenda === WORKFLOW_POLICY.records.addenda &&
  matchesBackgroundCliFallback(values.backgroundCliFallback) &&
  values.blockedReport === WORKFLOW_POLICY.completion.blockedReport &&
  listMatches(values.completionRequires, WORKFLOW_POLICY.completion.requires) &&
  values.directExploration === WORKFLOW_POLICY.root.opusSol.directExploration &&
  matchesFailurePolicyVocabulary(values.failure) &&
  listMatches(
    values.documentEditRequires,
    WORKFLOW_POLICY.documentEdits.requires
  ) &&
  values.executor === WORKFLOW_POLICY.validation.executor &&
  values.handoffOwner === WORKFLOW_POLICY.root.unavailable.handoffOwner &&
  listMatches(values.lowDispositions, WORKFLOW_POLICY.review.lowDispositions) &&
  values.manualFallback === WORKFLOW_POLICY.documentEdits.manualFallback &&
  listMatches(
    values.opusSolDelegates,
    WORKFLOW_POLICY.root.opusSol.delegates
  ) &&
  listMatches(
    values.opusSolRequiredFor,
    WORKFLOW_POLICY.root.opusSol.requiredFor
  ) &&
  values.requiredCapability ===
    WORKFLOW_POLICY.root.unavailable.requiredCapability &&
  values.selection === WORKFLOW_POLICY.records.selection &&
  values.sourceFixRequires === WORKFLOW_POLICY.review.sourceFixRequires &&
  listMatches(values.terraDelegates, WORKFLOW_POLICY.root.terra.delegates) &&
  listMatches(values.terraRequires, WORKFLOW_POLICY.root.terra.requires) &&
  listMatches(values.validationPhases, WORKFLOW_POLICY.validation.phases);

export const buildWorkflowPolicy = (
  records: PolicyRecords,
  schemaVersion: number,
  blockers: string[]
): WorkflowPolicy | undefined => {
  const values = readPolicyValues(records);
  if (values === undefined) {
    blockers.push("workflow-policy-value-invalid");
    return undefined;
  }
  if (schemaVersion !== WORKFLOW_POLICY.schemaVersion) {
    blockers.push("workflow-policy-version-unsupported");
  }
  for (const identifierValues of [
    values.backgroundCliFallback.leafProhibitions,
    values.terraRequires,
    values.terraDelegates,
    values.opusSolRequiredFor,
    values.opusSolDelegates,
    values.validationPhases,
    values.failure.discardRetryRequires,
    values.failure.phases,
    values.lowDispositions,
    values.completionRequires,
    values.documentEditRequires
  ]) {
    validateIdentifiers(identifierValues, blockers);
  }
  validateIdentifiers(
    [
      values.backgroundCliFallback.admission,
      values.backgroundCliFallback.authority,
      values.backgroundCliFallback.leafLimit,
      values.backgroundCliFallback.record,
      values.backgroundCliFallback.unavailableHandoff,
      values.directExploration,
      values.requiredCapability,
      values.handoffOwner,
      values.executor,
      values.sourceFixRequires,
      values.blockedReport,
      values.manualFallback,
      values.addenda,
      values.selection,
      values.failure.failedCycles,
      values.failure.retryBudget
    ],
    blockers
  );
  if (
    new Set(values.validationPhases).size !== values.validationPhases.length
  ) {
    blockers.push("workflow-policy-validation-phases-invalid");
  }
  if (!matchesVocabulary(values)) {
    blockers.push("workflow-policy-vocabulary-unsupported");
  }
  if (blockers.length > 0) {
    return undefined;
  }
  return WORKFLOW_POLICY;
};
