import { spawnSync } from "node:child_process";
import path from "node:path";
import { pathToFileURL } from "node:url";

const alwaysRunChecks = [
  "check:markdownlint-cli2",
  "check:typescript",
  "check:ultracite",
  "check:versions",
  "check:yaml-sequences"
];

const testScopes: readonly {
  tests: readonly string[];
  patterns: readonly RegExp[];
}[] = [
  {
    patterns: [/^\.github\/scripts\//u, /^\.husky\//u],
    tests: [".github/scripts/pre-commit-scope.test.ts"]
  },
  {
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
    ],
    tests: ["rules"]
  },
  {
    patterns: [/^plugins\/java\//u],
    tests: ["plugins/java/tests"]
  },
  {
    patterns: [/^plugins\/spec-driven-development\//u],
    tests: ["plugins/spec-driven-development/tests"]
  },
  {
    patterns: [/^plugins\/observability-assets\//u],
    tests: ["plugins/observability-assets/tests/promtool/release.test.ts"]
  }
];

export const selectChecks = (stagedPaths: readonly string[]): string[] => [
  ...alwaysRunChecks,
  ...(stagedPaths.some(
    (file) =>
      file.startsWith("plugins/observability-assets/") ||
      (file.startsWith(".claude/skills/formal-verification/assets/") &&
        /\.(?:tla|cfg)$/u.test(file))
  )
    ? ["check:promtool"]
    : []),
  `check:test:unit --exclude=plugins/harness/** ${[
    ".github/scripts/sync-versions.test.ts",
    ...testScopes
      .filter(({ patterns }) =>
        stagedPaths.some((stagedPath) =>
          patterns.some((pattern) => pattern.test(stagedPath))
        )
      )
      .flatMap(({ tests }) => tests)
  ].join(" ")}`
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
  const result = spawnSync(
    "pnpm",
    ["exec", "run-p", ...selectChecks(stagedPaths)],
    {
      stdio: "inherit"
    }
  );
  if (result.error) {
    throw result.error;
  }
  process.exitCode = result.status ?? 1;
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
