// -*- coding: utf-8 -*-

export type { WorkflowPolicy } from "./workflow-policy-contract.js";

export type StringList = readonly string[];

export type PolicyRecords = Readonly<{
  backgroundCliFallback: Record<string, unknown>;
  completion: Record<string, unknown>;
  documentEdits: Record<string, unknown>;
  failure: Record<string, unknown>;
  opusSol: Record<string, unknown>;
  records: Record<string, unknown>;
  review: Record<string, unknown>;
  root: Record<string, unknown>;
  terra: Record<string, unknown>;
  unavailable: Record<string, unknown>;
  validation: Record<string, unknown>;
}>;
