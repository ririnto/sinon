import { configDefaults, defineConfig } from "vitest/config";

/**
 * Discover root-owned tests without entering independent consumer projects.
 */
export default defineConfig({
  test: {
    exclude: [
      ...configDefaults.exclude,
      "**/.claude/worktrees/**",
      "**/.worktrees/**"
    ],
    include: [
      "{.github/scripts,rules}/**/*.test.ts",
      "plugins/*/tests/**/*.test.ts",
      "plugins/harness/tooling/pnpm/**/*.test.ts"
    ]
  }
});
