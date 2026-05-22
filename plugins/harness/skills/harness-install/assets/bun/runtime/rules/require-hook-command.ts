#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

const STACK = "bun" as const;

/**
 * Require hooks to declare and run validation commands.
 */
export class RequireHookCommandRule implements HarnessCheckRule {
  static readonly category = "requireHookCommand";

  constructor(private readonly ctx: RuleContext) {}

  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireHookCommand;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = this.ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return typeof parameters.prePushHook === "string";
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const section = this.ctx.readJsonObject(manifest.requireHookCommand);
    const parameters = this.ctx.readJsonObject(section.parameters);
    const allowedCommands = this.ctx.readJsonObject(parameters.allowedCommands);
    const stackCommands = this.ctx.readStringArray(allowedCommands[STACK]);

    const prePushHook = typeof parameters.prePushHook === "string" ? parameters.prePushHook : "";
    if (!this.ctx.isFile(prePushHook)) {
      return [];
    }

    const prePushText = this.ctx.read(prePushHook);
    const validationCommand = prePushText
      .split(/\r?\n/)
      .find((line) => line.startsWith("# Harness validation command: "))
      ?.replace("# Harness validation command: ", "")
      .trim() ?? "";

    return [
      validationCommand.length === 0
        ? {
            severity: this.ctx.severityOf(manifest, RequireHookCommandRule.category),
            category: RequireHookCommandRule.category,
            message: "pre-push hook must declare Harness validation command",
          }
        : null,
      validationCommand && !stackCommands.includes(validationCommand)
        ? {
            severity: this.ctx.severityOf(manifest, RequireHookCommandRule.category),
            category: RequireHookCommandRule.category,
            message: `pre-push hook declares unsupported validation command: ${validationCommand}`,
          }
        : null,
      validationCommand && !prePushText.split(/\r?\n/).includes(validationCommand)
        ? {
            severity: this.ctx.severityOf(manifest, RequireHookCommandRule.category),
            category: RequireHookCommandRule.category,
            message: "pre-push hook must run the declared validation command",
          }
        : null,
    ].filter((f): f is Finding => f !== null);
  }
}
