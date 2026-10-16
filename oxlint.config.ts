import { defineConfig } from "oxlint";
import core from "ultracite/oxlint/core";

/**
 * Repository Oxlint configuration shared by all publishable packages.
 */
export default defineConfig({
  env: core.env,
  extends: [core],
  ignorePatterns: core.ignorePatterns
});
