import { configDefaults, defineConfig } from "vitest/config";

/**
 * Keep native worktree copies out of canonical component test discovery.
 */
export default defineConfig({
  test: {
    exclude: [
      ...configDefaults.exclude,
      "**/.claude/worktrees/**",
      "**/.worktrees/**"
    ]
  }
});
