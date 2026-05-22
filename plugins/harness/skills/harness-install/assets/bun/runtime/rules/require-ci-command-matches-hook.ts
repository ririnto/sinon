#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require CI configuration to match hook validation commands.
 */
export class RequireCiCommandMatchesHookRule implements HarnessCheckRule {
  static readonly category = "requireCiCommandMatchesHook";

  constructor(private readonly ctx: RuleContext) {}

  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireCiCommandMatchesHook;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = this.ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return typeof parameters.referenceHook === "string";
  }

  validate(_root: string, manifest: HarnessManifest): Finding[] {
    const section = this.ctx.readJsonObject(manifest.requireCiCommandMatchesHook);
    const parameters = this.ctx.readJsonObject(section.parameters);
    const referenceHook = typeof parameters.referenceHook === "string" ? parameters.referenceHook : "";
    const ciFiles = this.ctx.readStringArray(parameters.ciFiles);

    if (!this.ctx.isFile(referenceHook)) {
      return [];
    }

    const refText = this.ctx.read(referenceHook);
    const refCommand = refText
      .split(/\r?\n/)
      .find((line) => line.startsWith("# Harness validation command: "))
      ?.replace("# Harness validation command: ", "")
      .trim() ?? "";

    if (!refCommand) {
      return [];
    }

    return ciFiles.flatMap((ciFile) => {
      if (!this.ctx.isFile(ciFile)) {
        return [];
      }
      return this.ctx.read(ciFile).includes(refCommand)
        ? []
        : [
            {
              severity: this.ctx.severityOf(manifest, RequireCiCommandMatchesHookRule.category),
              category: RequireCiCommandMatchesHookRule.category,
              message: `${ciFile}: CI command mismatch — expected ${refCommand}`,
            },
          ];
    });
  }
}
