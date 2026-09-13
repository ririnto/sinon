import { defineConfig } from "oxfmt";
import ultracite from "ultracite/oxfmt";

/**
 * Configure the Bun profile's native format checks.
 */
export default defineConfig({
  ...ultracite,
  ignorePatterns: [...(ultracite.ignorePatterns ?? []), "**/*.md"],
  trailingComma: "none"
});
