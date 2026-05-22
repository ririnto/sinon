#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require /usr/bin/env shebangs in executable scripts.
 */
export class RequireEnvShebangUnderRule implements HarnessCheckRule {
  static readonly category = "requireEnvShebangUnder";

  constructor(private readonly ctx: RuleContext) {}

  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireEnvShebangUnder;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = this.ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return this.ctx.readStringArray(parameters.directories).length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const section = this.ctx.readJsonObject(manifest.requireEnvShebangUnder);
    const parameters = this.ctx.readJsonObject(section.parameters);
    const directories = this.ctx.readStringArray(parameters.directories);
    const expectedPrefix = typeof parameters.expectedPrefix === "string" ? parameters.expectedPrefix : "#!/usr/bin/env ";

    return directories.flatMap((dir) => {
      const [files, warnings] = this.ctx.walkDirectory(dir);
      return warnings.concat(
        files.flatMap((file) => {
          if (!this.ctx.isExecutablePath(file)) {
            return [];
          }
          const first = this.ctx.firstLine(file);
          return first.startsWith("#!") && !first.startsWith(expectedPrefix)
            ? [
                {
                  severity: this.ctx.severityOf(manifest, RequireEnvShebangUnderRule.category),
                  category: RequireEnvShebangUnderRule.category,
                  message: `executable script should use /usr/bin/env shebang: ${file}`,
                },
              ]
            : [];
        })
      );
    });
  }
}
