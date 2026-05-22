#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Forbid unsafe symlinks (validation is implicit via other rules).
 */
export class ForbidUnsafeSymlinksRule implements HarnessCheckRule {
  static readonly category = "forbidUnsafeSymlinks";

  constructor(private readonly ctx: RuleContext) {}

  applies(manifest: HarnessManifest): boolean {
    const section = manifest.forbidUnsafeSymlinks;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = this.ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return Array.isArray(parameters.allowedSymlinkPairs) && parameters.allowedSymlinkPairs.length > 0;
  }

  validate(_root: string, _manifest: HarnessManifest): Finding[] {
    return [];
  }
}
