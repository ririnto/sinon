import { defineConfig } from "oxlint";
import core from "ultracite/oxlint/core";

/**
 * Combine the local effect policy with maintained React Compiler diagnostics.
 */
export default defineConfig({
  env: core.env,
  extends: [core],
  ignorePatterns: core.ignorePatterns,
  jsPlugins: [
    { name: "react-hooks-js", specifier: "eslint-plugin-react-hooks" },
    "./plugins/no-direct-use-effect.ts"
  ],
  rules: {
    "oxc/no-accumulating-spread": "deny",
    "react-effects/no-direct-use-effect": "deny",
    "react-hooks-js/immutability": "deny",
    "react-hooks-js/purity": "deny",
    "react-hooks-js/rules-of-hooks": "deny",
    "react-hooks-js/set-state-in-effect": "deny",
    "react-hooks-js/set-state-in-render": "deny"
  }
});
