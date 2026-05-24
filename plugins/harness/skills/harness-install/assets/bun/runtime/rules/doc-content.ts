#!/usr/bin/env bun
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

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
export const docContentRule = (ctx: RuleContext): HarnessCheckRule => ({
	category: "docContent",
	applies(manifest: HarnessManifest): boolean {
		const section = manifest.docContent;
		if (typeof section !== "object" || section === null) {
			return false;
		}
		const enabled = (section as { enabled?: unknown }).enabled;
		if (enabled === false) {
			return false;
		}
		const checks = ctx.readJsonObject(
			(section as Record<string, unknown>).parameters,
		).checks;
		return Array.isArray(checks) && checks.length > 0;
	},

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		const checks = ctx.readJsonObject(
			ctx.readJsonObject(manifest.docContent).parameters,
		).checks;
		if (!Array.isArray(checks)) {
			return [];
		}
		return checks
			.filter((check) => typeof check === "object" && check !== null)
			.flatMap((check) => {
				const checkObj = check as Record<string, unknown>;
				const files = ctx.readStringArray(checkObj.files);
				const failureMessage =
					typeof checkObj.failureMessage === "string"
						? checkObj.failureMessage
						: "";
				const combinedText = files.map((f) => ctx.read(f)).join("\n");
				return !conditionMatches(checkObj, combinedText) && failureMessage
					? [
							{
								severity: ctx.severityOf(manifest, "docContent"),
								category: "docContent",
								message: failureMessage,
							},
						]
					: [];
			});
	},
});

const conditionMatches = (
	checkObj: Record<string, unknown>,
	combinedText: string,
): boolean => {
	const condition = checkObj.condition ?? checkObj.when;
	if (condition !== undefined) {
		return evaluateCondition(condition as ContentCondition, combinedText);
	}
	return false;
};

const evaluateCondition = (
	condition: ContentCondition,
	combinedText: string,
): boolean => {
	if (typeof condition === "string") {
		return combinedText.includes(condition);
	}
	if (Array.isArray(condition)) {
		return condition.every((item) =>
			evaluateCondition(item as ContentCondition, combinedText),
		);
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
	const andMatches =
		!hasAll || allOf.every((item) => evaluateCondition(item, combinedText));
	const orMatches =
		!hasAny || anyOf.some((item) => evaluateCondition(item, combinedText));
	const containsMatches = contains.every((substring) =>
		combinedText.includes(substring),
	);
	const notMatches =
		!hasNot || !evaluateCondition(notCondition as ContentCondition, combinedText);
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
