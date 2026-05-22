#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require CI configuration to match hook validation commands.
 */
export const requireCiCommandMatchesHookRule = (ctx: RuleContext): HarnessCheckRule => ({
  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireCiCommandMatchesHook;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    return enabled !== false && typeof ctx.readJsonObject((section as Record<string, unknown>).parameters).referenceHook === "string";
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const section = ctx.readJsonObject(manifest.requireCiCommandMatchesHook);
    const parameters = ctx.readJsonObject(section.parameters);
    const referenceHook = typeof parameters.referenceHook === "string" ? parameters.referenceHook : "";
    const ciFiles = ctx.readStringArray(parameters.ciFiles);
    if (!ctx.isFile(referenceHook)) {
      return [];
    }
    const refText = ctx.read(referenceHook);
    const refCommand = refText
      .split(/\r?\n/)
      .find((line) => line.startsWith("# Harness validation command: "))
      ?.replace("# Harness validation command: ", "")
      .trim() ?? "";
    if (!refCommand) {
      return [];
    }
    return ciFiles.flatMap((ciFile) => {
      return ctx.isFile(ciFile) && !ctx.read(ciFile).includes(refCommand)
        ? [
            {
              severity: ctx.severityOf(manifest, "requireCiCommandMatchesHook"),
              category: "requireCiCommandMatchesHook",
              message: `${ciFile}: CI command mismatch — expected ${refCommand}`,
            },
          ]
        : [];
    });
  }

});
