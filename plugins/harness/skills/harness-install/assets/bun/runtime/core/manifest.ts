#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Severity } from "./severity";

export type HarnessManifest = Record<string, unknown>;

/**
 * Raw manifest payload with accessor methods.
 */
export interface Manifest {
    /**
     * Raw manifest payload.
     */
    readonly raw: Record<string, unknown>;

    isEnabled(category: string): boolean;
    severityOf(category: string): Severity;
    stringArray(category: string): readonly string[];
    stringValue(category: string): string;
    categoryObject(category: string): Record<string, unknown>;
}

function asRecord(value: unknown): Record<string, unknown> {
    return value !== null && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function categoryObjectFromManifest(rawManifest: Record<string, unknown>, category: string): Record<string, unknown> {
    const categoryValue = rawManifest[category];
    return typeof categoryValue === "object" && categoryValue !== null
        ? (categoryValue as Record<string, unknown>)
        : {};
}

function isEnabledFromManifest(rawManifest: Record<string, unknown>, category: string): boolean {
    const section = categoryObjectFromManifest(rawManifest, category);
    return section.enabled !== false;
}

function severityFromManifest(rawManifest: Record<string, unknown>, category: string): Severity {
    const severity = categoryObjectFromManifest(rawManifest, category).severity;
    return severity === "ERROR" || severity === "WARN" || severity === "INFO" ? severity : "ERROR";
}

function stringValueFromManifest(rawManifest: Record<string, unknown>, category: string): string {
    return typeof rawManifest[category] === "string" ? (rawManifest[category] as string) : "";
}

function stringArrayFromManifest(rawManifest: Record<string, unknown>, category: string): readonly string[] {
    const value = rawManifest[category];
    return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

/**
 * Factory function to create a Manifest from a raw manifest object.
 */
export function createManifest(rawManifest: unknown): Manifest {
    const manifestJson = asRecord(rawManifest);
    return {
        raw: manifestJson,
        isEnabled(category: string): boolean {
            return isEnabledFromManifest(manifestJson, category);
        },
        severityOf(category: string): Severity {
            return severityFromManifest(manifestJson, category);
        },
        stringArray(category: string): readonly string[] {
            return stringArrayFromManifest(manifestJson, category);
        },
        stringValue(category: string): string {
            return stringValueFromManifest(manifestJson, category);
        },
        categoryObject(category: string): Record<string, unknown> {
            return categoryObjectFromManifest(manifestJson, category);
        },
    };
}

export {
    asRecord,
    categoryObjectFromManifest,
    isEnabledFromManifest,
    severityFromManifest,
    stringArrayFromManifest,
    stringValueFromManifest,
};
