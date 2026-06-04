import { defineConfig } from "oxfmt";
import ultracite from "ultracite/oxfmt";

export default defineConfig({
  ...ultracite,
  ignorePatterns: [
    ...(ultracite.ignorePatterns ?? []),
    "**/*.md",
    "**/*.markdown",
    "**/*.yaml",
    "**/*.yml",
    "**/*.json",
    "**/*.jsonc",
  ],
});
