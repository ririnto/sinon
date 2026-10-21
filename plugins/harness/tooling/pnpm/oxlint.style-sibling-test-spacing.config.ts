import { defineConfig } from "oxlint";
import core from "ultracite/oxlint/core";

/**
 * Configure the independent test-registration spacing rule pass.
 */
export default defineConfig({
  env: core.env,
  extends: [core],
  ignorePatterns: core.ignorePatterns,
  jsPlugins: ["./plugins/sibling-test-spacing.ts"],
  rules: { "style/sibling-test-spacing": "deny" }
});
