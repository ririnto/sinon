#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require /usr/bin/env shebangs in executable scripts.
 */
export const requireEnvShebangUnderRule = (ctx: RuleContext): HarnessCheckRule => ({
  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireEnvShebangUnder;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return ctx.readStringArray(parameters.directories).length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const parameters = ctx.readJsonObject(ctx.readJsonObject(manifest.requireEnvShebangUnder).parameters);
    const directories = ctx.readStringArray(parameters.directories);
    const expectedPrefix = typeof parameters.expectedPrefix === "string" ? parameters.expectedPrefix : "#!/usr/bin/env ";

    return directories.flatMap((dir) => {
      const [files, warnings] = ctx.walkDirectory(dir);
      return warnings.concat(
        files.flatMap((file) => {
          if (!ctx.isExecutablePath(file)) {
            return [];
          }
          return (ctx.firstLine(file)).startsWith("#!") && !(ctx.firstLine(file)).startsWith(expectedPrefix)
            ? [
                {
                  severity: ctx.severityOf(manifest, "requireEnvShebangUnder"),
                  category: "requireEnvShebangUnder",
                  message: `executable script should use /usr/bin/env shebang: ${file}`,
                },
              ]
            : [];
        })
      );
    });
  }

});
