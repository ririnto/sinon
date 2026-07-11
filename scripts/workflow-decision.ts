// -*- coding: utf-8 -*-

/** Deterministic workflow decision with blockers. */
export interface WorkflowDecision<T, B = string> {
  readonly blockers: readonly B[];
  readonly value?: T;
}

export const assertNever = (value: never): never => {
  throw new TypeError(`Unhandled workflow variant: ${String(value)}`);
};
