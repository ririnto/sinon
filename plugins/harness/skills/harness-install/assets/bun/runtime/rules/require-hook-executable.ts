#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require hooks to be executable.
 */
export class RequireHookExecutableRule implements HarnessCheckRule {
  static readonly category = "requireHookExecutable";

  constructor(private readonly ctx: RuleContext) {}

  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireHookExecutable;
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
    const section = this.ctx.readJsonObject(manifest.requireHookExecutable);
    const parameters = this.ctx.readJsonObject(section.parameters);
    const hooks = this.ctx.readStringArray(parameters.hooks);
    return hooks.flatMap((hook) => {
      if (!this.ctx.isFile(hook)) {
        return [];
      }
      return this.ctx.isExecutablePath(hook)
        ? []
        : [
            {
              severity: this.ctx.severityOf(manifest, RequireHookExecutableRule.category),
              category: RequireHookExecutableRule.category,
              message: `${hook} must be executable`,
            },
          ];
    });
  }
}
