import { defineConfig } from "oxlint";
import core from "ultracite/oxlint/core";

/**
 * Configure the independent function-body blank-line rule pass.
 */
export default defineConfig({
  ...(core.env === undefined ? {} : { env: core.env }),
  extends: [core],
  ...(core.ignorePatterns === undefined
    ? {}
    : { ignorePatterns: core.ignorePatterns }),
  jsPlugins: ["./plugins/no-blank-lines-in-functions.ts"],
  rules: { "style/no-blank-lines-in-functions": "deny" }
});
