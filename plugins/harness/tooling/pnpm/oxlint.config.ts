import { defineConfig } from "oxlint";
import core from "ultracite/oxlint/core";

/**
 * Configure the pnpm profile's native lint checks.
 */
export default defineConfig({
  ...(core.env === undefined ? {} : { env: core.env }),
  extends: [core],
  ...(core.ignorePatterns === undefined
    ? {}
    : { ignorePatterns: core.ignorePatterns }),
  jsPlugins: [
    { name: "react-hooks-js", specifier: "eslint-plugin-react-hooks" },
    "./plugins/no-direct-use-effect.ts",
    "./plugins/tsdoc-plugin.ts",
    {
      name: "function-spacing",
      specifier: "./plugins/no-blank-lines-in-functions.ts"
    },
    { name: "prose-style", specifier: "./plugins/no-em-dash.ts" },
    {
      name: "function-comments",
      specifier: "./plugins/no-inline-comments-in-functions.ts"
    },
    { name: "test-spacing", specifier: "./plugins/sibling-test-spacing.ts" }
  ],
  overrides: [
    {
      files: ["**/*.{js,jsx,mjs,cjs}"],
      rules: {
        "jsdoc/require-param": "deny",
        "jsdoc/require-param-name": "deny",
        "jsdoc/require-param-type": "deny",
        "jsdoc/require-returns": "deny",
        "jsdoc/require-returns-description": "allow",
        "jsdoc/require-returns-type": "deny"
      }
    }
  ],
  rules: {
    "function-comments/no-inline-comments-in-functions": "deny",
    "function-spacing/no-blank-lines-in-functions": "deny",
    "oxc/no-accumulating-spread": "deny",
    "prose-style/no-em-dash": "deny",
    "react-effects/no-direct-use-effect": "deny",
    "react-hooks-js/immutability": "deny",
    "react-hooks-js/purity": "deny",
    "react-hooks-js/rules-of-hooks": "deny",
    "react-hooks-js/set-state-in-effect": "deny",
    "react-hooks-js/set-state-in-render": "deny",
    "test-spacing/sibling-test-spacing": "deny",
    "tsdoc/require-export-tsdoc": "deny",
    "typescript/consistent-type-imports": [
      "error",
      { fixStyle: "separate-type-imports", prefer: "type-imports" }
    ],
    "typescript/no-floating-promises": ["error", { ignoreVoid: false }]
  }
});
