import { defineConfig } from "oxlint";
import core from "ultracite/oxlint/core";

/**
 * Configure the independent inline-comment rule pass.
 */
export default defineConfig({
  env: core.env,
  extends: [core],
  ignorePatterns: core.ignorePatterns,
  jsPlugins: ["./plugins/no-inline-comments-in-functions.ts"],
  rules: { "style/no-inline-comments-in-functions": "deny" }
});
