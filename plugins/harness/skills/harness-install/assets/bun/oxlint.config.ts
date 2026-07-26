import { defineConfig } from "oxlint";
import core from "ultracite/oxlint/core";

export default defineConfig({
  extends: [core],
  ignorePatterns: core.ignorePatterns,
  jsPlugins: ["./plugins/style-plugin.ts", "./plugins/tsdoc-plugin.ts"],
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
    "style/no-blank-lines-in-functions": "deny",
    "style/no-inline-comments-in-functions": "deny",
    "tsdoc/require-export-tsdoc": "deny",
    "typescript/consistent-type-imports": [
      "error",
      { fixStyle: "separate-type-imports", prefer: "type-imports" }
    ]
  }
});
