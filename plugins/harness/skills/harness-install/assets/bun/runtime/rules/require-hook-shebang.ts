#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require hooks to have correct shebang.
 */
export class RequireHookShebangRule implements HarnessCheckRule {
  static readonly category = "requireHookShebang";

  constructor(private readonly ctx: RuleContext) {}

  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireHookShebang;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = this.ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return this.ctx.readStringArray(parameters.hooks).length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const section = this.ctx.readJsonObject(manifest.requireHookShebang);
    const parameters = this.ctx.readJsonObject(section.parameters);
    const hooks = this.ctx.readStringArray(parameters.hooks);
    const expectedShebang = typeof parameters.expectedShebang === "string" ? parameters.expectedShebang : "#!/usr/bin/env sh";
    return hooks.flatMap((hook) => {
      if (!this.ctx.isFile(hook)) {
        return [];
      }
      return this.ctx.firstLine(hook) === expectedShebang
        ? []
        : [
            {
              severity: this.ctx.severityOf(manifest, RequireHookShebangRule.category),
              category: RequireHookShebangRule.category,
              message: `${hook} must start with ${expectedShebang}`,
            },
          ];
    });
  }
}
