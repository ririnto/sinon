import { spawnSync } from "node:child_process";
import path from "node:path";
import { pathToFileURL } from "node:url";

const alwaysRunChecks = [
  "check:markdownlint-cli2",
  "check:typescript",
  "check:ultracite",
  "check:versions"
];

const triggeredChecks: readonly {
  check: string;
  patterns: readonly RegExp[];
}[] = [
  {
    check: "check:hook-scope",
    patterns: [/^\.github\/scripts\//u, /^\.husky\//u]
  },
  {
    check: "check:lint-rules",
    patterns: [
      /^plugins\/harness\//u,
      /^rules\//u,
      /^package\.json$/u,
      /^pnpm-lock\.yaml$/u,
      /^pnpm-workspace\.yaml$/u,
      /^vitest\.config\.ts$/u,
      /^oxlint\.config\.ts$/u,
      /^tsconfig\.json$/u,
      /^\.markdownlint-cli2\.jsonc$/u
    ]
  },
  {
    check: "check:java",
    patterns: [/^plugins\/java\//u]
  },
  {
    check: "check:sdd",
    patterns: [/^plugins\/spec-driven-development\//u]
  },
  {
    check: "check:promtool-release",
    patterns: [/^plugins\/observability-assets\//u]
  }
];

/**
 * Selects the pre-commit checks for the staged paths.
 * The always-run set keeps repository-wide validators in every commit;
 * the triggered set adds each suite whose inputs the staged paths touch.
 */
export const selectChecks = (stagedPaths: readonly string[]): string[] => [
  ...alwaysRunChecks,
  ...triggeredChecks
    .filter(({ patterns }) =>
      stagedPaths.some((stagedPath) =>
        patterns.some((pattern) => pattern.test(stagedPath))
      )
    )
    .map(({ check }) => check)
];

const runCli = (): void => {
  const staged = spawnSync("git", ["diff", "--cached", "--name-only"], {
    encoding: "utf-8"
  });
  if (staged.status !== 0 || staged.stdout === null) {
    throw new Error(
      `git diff failed with status ${staged.status}: ${staged.stderr}`
    );
  }
  const stagedPaths = staged.stdout.split("\n").filter((line) => line !== "");
  process.stdout.write(selectChecks(stagedPaths).join(" "));
};

if (
  process.argv[1] !== undefined &&
  import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href
) {
  try {
    runCli();
  } catch (error) {
    process.stderr.write(
      `${error instanceof Error ? error.message : String(error)}\n`
    );
    process.exitCode = 1;
  }
}
