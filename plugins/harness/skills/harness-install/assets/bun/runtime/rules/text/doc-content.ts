#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Finding, HarnessCheckRule, RuleContext } from "../harness-check-rule";

type ContentCondition =
    | string
    | readonly unknown[]
    | {
          readonly contains?: unknown;
          readonly allOf?: unknown;
          readonly anyOf?: unknown;
          readonly not?: unknown;
      };

/**
 * Require specified content in documentation files.
 */
export const docContentRule: HarnessCheckRule = {
    category: "docContent",
    applies(ctx: RuleContext): boolean {
        const section = ctx.manifest.raw.docContent;
        if (typeof section !== "object" || section === null) {
            return false;
        }
        const enabled = (section as { enabled?: unknown }).enabled;
        if (enabled === false) {
            return false;
        }
        const checks = ctx.readJsonObject((section as Record<string, unknown>).parameters).checks;
        return Array.isArray(checks) && checks.length > 0;
    },

    validate(ctx: RuleContext): readonly Finding[] {
        const checks = ctx.readJsonObject(ctx.readJsonObject(ctx.manifest.raw.docContent).parameters).checks;
        if (!Array.isArray(checks)) {
            return [];
        }
        return checks
            .filter((check) => typeof check === "object" && check !== null)
            .flatMap((check, _) => {
                const checkObj = check as Record<string, unknown>;
                const files = ctx.readStringArray(checkObj.files);
                const failureMessage = typeof checkObj.failureMessage === "string" ? checkObj.failureMessage : "";
                return !conditionMatches(checkObj, files.map((f) => ctx.read(f)).join("\n")) && failureMessage
                    ? [
                          {
                              severity: ctx.severityOf("docContent"),
                              category: "docContent",
                              message: failureMessage,
                              file: files[0] ?? "",
                              startLine: 1,
                              startColumn: 1,
                              endLine: 1,
                              endColumn: 1,
                              fix: {
                                  description: "add required content to documentation",
                                  safety: "manual",
                              },
                          },
                      ]
                    : [];
            });
    },
};

const conditionMatches = (checkObj: Record<string, unknown>, combinedText: string): boolean => {
    const condition = checkObj.condition ?? checkObj.when;
    if (condition !== undefined) {
        return evaluateCondition(condition as ContentCondition, combinedText);
    }
    return false;
};

const evaluateCondition = (condition: ContentCondition, combinedText: string): boolean => {
    if (typeof condition === "string") {
        return combinedText.includes(condition);
    }
    if (Array.isArray(condition)) {
        return condition.every((item) => evaluateCondition(item as ContentCondition, combinedText));
    }
    if (typeof condition !== "object" || condition === null) {
        return false;
    }
    const hasAll = "allOf" in condition;
    const hasAny = "anyOf" in condition;
    const hasContains = "contains" in condition;
    const hasNot = "not" in condition;
    if (!(hasAll || hasAny || hasContains || hasNot)) {
        return false;
    }
    const allOf = readConditionArray(condition.allOf);
    const anyOf = readConditionArray(condition.anyOf);
    const contains = readStringArray(condition.contains);
    const notCondition = condition.not;
    const andMatches = !hasAll || allOf.every((item) => evaluateCondition(item, combinedText));
    const orMatches = !hasAny || anyOf.some((item) => evaluateCondition(item, combinedText));
    const containsMatches = contains.every((substring) => combinedText.includes(substring));
    const notMatches = !hasNot || !evaluateCondition(notCondition as ContentCondition, combinedText);
    return andMatches && orMatches && containsMatches && notMatches;
};

const readConditionArray = (value: unknown): readonly ContentCondition[] =>
    Array.isArray(value)
        ? value.map((item) => item as ContentCondition)
        : typeof value === "string" || (typeof value === "object" && value !== null)
          ? [value as ContentCondition]
          : [];

const readStringArray = (value: unknown): readonly string[] =>
    Array.isArray(value)
        ? value.filter((item): item is string => typeof item === "string")
        : typeof value === "string"
          ? [value]
          : [];
