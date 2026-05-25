#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

const STACK = "bun";

export const hookCommandRule: HarnessCheckRule = {
  category: "hookCommand",
  applies(ctx: RuleContext): boolean {
    const section = ctx.manifest.raw.hookCommand;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = ctx.readJsonObject(
      (section as Record<string, unknown>).parameters,
    );
    return typeof parameters.prePushHook === "string";
  },

  validate(ctx: RuleContext): readonly Finding[] {
    const parameters = ctx.readJsonObject(
      ctx.readJsonObject(ctx.manifest.raw.hookCommand).parameters,
    );
    const allowedCommands = ctx.readJsonObject(parameters.allowedCommands);
    const stackCommands = ctx.readStringArray(allowedCommands[STACK]);
    const prePushHook =
      typeof parameters.prePushHook === "string" ? parameters.prePushHook : "";
    if (!ctx.isFile(prePushHook)) {
      return [];
    }
    const prePushText = ctx.read(prePushHook);
    const lines = prePushText.split(/\r?\n/);
    const validationCommandLine = lines.findIndex((line) =>
      line.startsWith("# Harness validation command: "),
    );
    const validationCommand =
      validationCommandLine >= 0
        ? lines[validationCommandLine]
          ?.replace("# Harness validation command: ", "")
          .trim() ?? ""
        : "";
    const severity = ctx.severityOf("hookCommand");
    const category = "hookCommand";
    const findings: Finding[] = [];
    if (validationCommand.length === 0) {
      findings.push({
        severity,
        category,
        message: "pre-push hook must declare Harness validation command",
        file: prePushHook,
        startLine: 1,
        startColumn: 1,
        endLine: 1,
        endColumn: 1,
      });
    }
    if (validationCommand && !stackCommands.includes(validationCommand)) {
      findings.push({
        severity,
        category,
        message: `pre-push hook declares unsupported validation command: ${validationCommand}`,
        file: prePushHook,
        startLine: validationCommandLine + 1,
        startColumn: 1,
        endLine: validationCommandLine + 1,
        endColumn: (lines[validationCommandLine]?.length ?? 0) + 1,
      });
    }
    if (validationCommand && !lines.includes(validationCommand)) {
      findings.push({
        severity,
        category,
        message: "pre-push hook must run the declared validation command",
        file: prePushHook,
        startLine: 1,
        startColumn: 1,
        endLine: 1,
        endColumn: 1,
      });
    }
    return findings;
  },
};
