#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Node } from "typescript@6.0.3";
import { forEachChild } from "typescript@6.0.3";

/**
 * Collect direct children of a TypeScript AST node as an immutable array.
 * Enables node traversal via flatMap composition rather than mutable visitor callbacks.
 */
export const astChildrenOf = (node: Node): readonly Node[] => {
  const collected: Node[] = [];
  forEachChild(node, (child) => {
    collected.push(child);
  });
  return collected;
};
