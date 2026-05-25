#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type {
  HarnessManifest,
  Manifest,
} from "../core/manifest";
import type { RuleContext } from "../core/rule-context";
import type { Severity } from "../core/severity";

/**
 * Strategy interface implemented by each harness check rule.
 */
export interface HarnessCheckRule {
  /**
   * Manifest category key used by this rule.
   */
  readonly category: string;

  /**
   * Determines whether this rule applies to the current context.
   */
  applies(ctx: RuleContext): boolean;

  /**
   * Validates the project against this rule.
   * Returns a read-only array of findings.
   */
  validate(ctx: RuleContext): readonly Finding[];

  /**
   * Auto-formats the project against this rule, when supported.
   * Returns the absolute paths of files modified by this rule.
   * Rules without an automatic fix MUST return an empty array.
   */
  format?(ctx: RuleContext): readonly string[];
}

/**
 * Describes whether a fix is safe to apply automatically or requires review.
 *
 * - `safe`: The fix can be applied automatically without user review.
 * - `unsafe`: The fix has potential side effects and requires manual review before application.
 * - `manual`: The fix cannot be automated and requires manual implementation by the user.
 */
export type FixSafety = "safe" | "unsafe" | "manual";

/**
 * A single edit operation within a fix, specifying the exact text replacement.
 */
export interface FindingEdit {
  /**
   * Absolute file path or relative path to the file containing the fix location.
   */
  readonly file: string;

  /**
   * Starting line number of the text to replace (1-indexed).
   */
  readonly startLine: number;

  /**
   * Starting column number of the text to replace (1-indexed).
   */
  readonly startColumn: number;

  /**
   * Ending line number of the text to replace (1-indexed).
   */
  readonly endLine: number;

  /**
   * Ending column number of the text to replace (1-indexed).
   */
  readonly endColumn: number;

  /**
   * The replacement text to insert at the specified location.
   */
  readonly replacement: string;
}

/**
 * Describes an automated fix for a validation finding.
 */
export interface FindingFix {
  /**
   * Human-readable description of what the fix does.
   */
  readonly description: string;

  /**
   * Categorization of whether the fix is safe to apply automatically.
   */
  readonly safety: FixSafety;

  /**
   * Optional array of edit operations that implement this fix.
   * When present, provides exact text replacements; when absent, the fix description alone
   * should guide manual resolution. Recommended when the rule can construct precise edits.
   */
  readonly edits?: readonly FindingEdit[];
}

/**
 * A validation finding emitted by a rule.
 */
export interface Finding {
  severity: Severity;
  category: string;
  message: string;

  /**
   * Absolute file path or relative path where the finding occurs (1-indexed locations).
   */
  readonly file?: string;

  /**
   * Starting line number of the finding (1-indexed).
   */
  readonly startLine?: number;

  /**
   * Starting column number of the finding (1-indexed).
   */
  readonly startColumn?: number;

  /**
   * Ending line number of the finding (1-indexed).
   */
  readonly endLine?: number;

  /**
   * Ending column number of the finding (1-indexed).
   */
  readonly endColumn?: number;

  /**
   * Optional fix information associated with this finding.
   */
  readonly fix?: FindingFix;
}

export type { HarnessManifest, Manifest, RuleContext, Severity };
