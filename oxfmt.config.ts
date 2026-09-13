import { defineConfig } from "oxfmt";
import ultracite from "ultracite/oxfmt";

/**
 * Repository Oxfmt configuration shared by all publishable packages.
 */
export default defineConfig({
  ...ultracite,
  ignorePatterns: [...(ultracite.ignorePatterns ?? []), "**/*.md"],
  trailingComma: "none"
});
