import { defineConfig } from "oxlint";
import core from "ultracite/oxlint/core";

export default defineConfig({
  extends: [core],
  plugins: ["jsdoc"],
  rules: {
    "jsdoc/require-param": "deny",
    "jsdoc/require-param-name": "deny",
    "jsdoc/require-param-type": "deny",
    "jsdoc/require-returns": "deny",
    "jsdoc/require-returns-type": "deny",
  },
});
