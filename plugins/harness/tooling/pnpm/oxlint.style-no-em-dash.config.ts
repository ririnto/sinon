import { defineConfig } from "oxlint";
import core from "ultracite/oxlint/core";

/**
 * Configure the independent em-dash prose rule pass.
 */
export default defineConfig({
  ...(core.env === undefined ? {} : { env: core.env }),
  extends: [core],
  ...(core.ignorePatterns === undefined
    ? {}
    : { ignorePatterns: core.ignorePatterns }),
  jsPlugins: ["./plugins/no-em-dash.ts"],
  rules: { "style/no-em-dash": "deny" }
});
