import { defineConfig } from "oxlint";
import core from "ultracite/oxlint/core";

/**
 * Configure the independent function-body blank-line rule pass.
 */
export default defineConfig({
  env: core.env,
  extends: [core],
  ignorePatterns: core.ignorePatterns,
  jsPlugins: ["./plugins/no-blank-lines-in-functions.ts"],
  rules: { "style/no-blank-lines-in-functions": "deny" }
});
