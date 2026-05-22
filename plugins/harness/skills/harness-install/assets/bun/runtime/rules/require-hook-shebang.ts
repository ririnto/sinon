#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require hooks to have correct shebang.
 */
export const requireHookShebangRule = (ctx: RuleContext): HarnessCheckRule => ({
  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireHookShebang;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return ctx.readStringArray(parameters.hooks).length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const section = ctx.readJsonObject(manifest.requireHookShebang);
    const parameters = ctx.readJsonObject(section.parameters);
    const hooks = ctx.readStringArray(parameters.hooks);
    const expectedShebang = typeof parameters.expectedShebang === "string" ? parameters.expectedShebang : "#!/usr/bin/env sh";
    return hooks.flatMap((hook) => {
      if (!ctx.isFile(hook)) {
        return [];
      }
      return ctx.firstLine(hook) === expectedShebang
        ? []
        : [
            {
              severity: ctx.severityOf(manifest, "requireHookShebang"),
              category: "requireHookShebang",
              message: `${hook} must start with ${expectedShebang}`,
            },
          ];
    });
  }

});
