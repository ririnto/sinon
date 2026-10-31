import { defineConfig } from "oxfmt";
import ultracite from "ultracite/oxfmt";

/**
 * Configure the pnpm profile's native format checks.
 */
export default defineConfig({
  ...ultracite,
  ignorePatterns: [...(ultracite.ignorePatterns ?? []), "**/*.md"],
  trailingComma: "none"
});
