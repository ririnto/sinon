import { defineConfig } from "oxlint";
import core from "ultracite/oxlint/core";

/**
 * Repository Oxlint configuration shared by all publishable packages.
 */
export default defineConfig({
  env: core.env,
  extends: [core],
  ignorePatterns: core.ignorePatterns,
  jsPlugins: [
    { name: "react-hooks-js", specifier: "eslint-plugin-react-hooks" },
    "./plugins/harness/tooling/pnpm/plugins/no-direct-use-effect.ts"
  ],
  overrides: [
    {
      files: ["plugins/observability-assets/tests/promtool/**/*.test.ts"],
      plugins: [...(core.plugins ?? []), "vitest"],
      rules: {
        "vitest/require-to-throw-message": "deny"
      }
    }
  ],
  rules: {
    "typescript/no-floating-promises": ["error", { ignoreVoid: false }],
    "oxc/no-accumulating-spread": "deny",
    "react-effects/no-direct-use-effect": "deny",
    "react-hooks-js/immutability": "deny",
    "react-hooks-js/purity": "deny",
    "react-hooks-js/rules-of-hooks": "deny",
    "react-hooks-js/set-state-in-effect": "deny",
    "react-hooks-js/set-state-in-render": "deny"
  }
});
