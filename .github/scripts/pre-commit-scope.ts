import { spawnSync } from "node:child_process";
import path from "node:path";
import { pathToFileURL } from "node:url";

interface CheckScope {
  readonly checks: readonly string[];
  readonly patterns: readonly RegExp[];
}

interface TestScope {
  readonly tests: readonly string[];
  readonly patterns: readonly RegExp[];
}

const allCheckPatterns = [
  /(?<parent>^|\/)package\.json$/u,
  /(?<parent>^|\/)pnpm-lock\.yaml$/u,
  /^pnpm-workspace\.yaml$/u
];

const allTestPatterns = [/^vitest\.config\.ts$/u];

const scopes = [
  {
    checks: ["check:markdownlint-cli2"],
    patterns: [
      /\.md$/u,
      /^\.gitignore$/u,
      /^\.markdownlint-cli2\.jsonc$/u,
      /^rules\/(?:no-box-drawing|no-em-dash|no-relative-skill-links|skill-frontmatter|table-separators)\.ts$/u,
      /^plugins\/harness\/tooling\/pnpm\/rules\/(?:no-em-dash|no-relative-skill-links|table-separators)\.ts$/u
    ]
  },
  {
    checks: ["check:typescript", "check:ultracite"],
    patterns: [
      /\.(?:[cm]?[jt]sx?)$/u,
      /^(?:tsconfig\.json|oxlint\.config\.ts)$/u
    ]
  },
  {
    checks: ["check:versions"],
    patterns: [
      /^\.github\/scripts\/sync-versions\.ts$/u,
      /^plugins\/harness\/tooling\/(?:pnpm|frontend|react)\/pnpm-workspace\.fragment\.yaml$/u,
      /^plugins\/harness\/tooling\/pnpm\/(?:api-docs|vue)\.pnpm-workspace\.fragment\.yaml$/u,
      /^plugins\/harness\/tooling\/pnpm\/runtime\.fragment\.json$/u,
      /^plugins\/harness\/ci\/gitlab\/pnpm\.gitlab-ci\.yaml$/u
    ]
  },
  {
    checks: ["check:yaml-sequences"],
    patterns: [
      /^(?!.*(?:^|\/)pnpm-lock\.yaml$).*\.ya?ml$/u,
      /^rules\/yaml-sequence-style\.ts$/u
    ]
  },
  {
    checks: ["check:promtool:child"],
    patterns: [
      /^\.claude\/skills\/formal-verification\/assets\/child-lifecycle\/.+\.(?:tla|cfg)$/u,
      /^\.claude\/skills\/formal-verification\/scripts\/check\.ts$/u
    ]
  },
  {
    checks: ["check:promtool:download"],
    patterns: [
      /^\.claude\/skills\/formal-verification\/assets\/download-lifecycle\/.+\.(?:tla|cfg)$/u,
      /^\.claude\/skills\/formal-verification\/scripts\/check\.ts$/u
    ]
  },
  {
    checks: ["check:formal-layout"],
    patterns: [
      /^\.claude\/skills\/formal-verification\/assets\/.+\.(?:cfg|tla)$/u,
      /^\.claude\/skills\/formal-verification\/references\/.+\.(?:mmd|mnd|svg)$/u,
      /^\.claude\/skills\/formal-verification\/scripts\/(?:check-layout|render-statecharts)\.ts$/u,
      /^\.husky\//u,
      /\.(?:cfg|tla|mmd|mnd|svg|sh|bash|zsh|ksh|fish|py|rb|pl|php|lua|ps1|bat|cmd|r|jl|kts|gradle|groovy|js|mjs|cjs|jsx|ts|mts|cts|tsx)$/u
    ]
  },
  {
    checks: ["check:statecharts"],
    patterns: [
      /^\.claude\/skills\/formal-verification\/references\/.+\.mmd$/u,
      /^\.claude\/skills\/formal-verification\/references\/.+\.svg$/u,
      /^\.claude\/skills\/formal-verification\/scripts\/render-statecharts\.ts$/u
    ]
  },
  {
    checks: ["check:sensitive-content"],
    patterns: [
      /^\.github\/scripts\/sync-sensitive-content\.ts$/u,
      /^plugins\/harness\/tooling\/pnpm\/rules\/no-sensitive-content\.ts$/u,
      /^plugins\/harness\/tooling\/pnpm\/runtime\/no-sensitive-content\.js$/u
    ]
  }
] satisfies readonly CheckScope[];

const testScopes = [
  {
    tests: [".github/scripts/pre-commit-scope.test.ts"],
    patterns: [
      /^\.github\/scripts\/pre-commit-scope(?:\.test)?\.ts$/u,
      /^\.husky\/(?:pre-commit|pre-push)$/u
    ]
  },
  {
    tests: [".github/scripts/sync-versions.test.ts"],
    patterns: [
      /^\.github\/scripts\/sync-versions(?:\.test)?\.ts$/u,
      /^package\.json$/u,
      /^pnpm-workspace\.yaml$/u,
      /^plugins\/harness\/tooling\/(?:pnpm|frontend|react)\/pnpm-workspace\.fragment\.yaml$/u,
      /^plugins\/harness\/tooling\/pnpm\/(?:api-docs|vue)\.pnpm-workspace\.fragment\.yaml$/u,
      /^plugins\/harness\/tooling\/pnpm\/runtime\.fragment\.json$/u,
      /^plugins\/harness\/ci\/gitlab\/pnpm\.gitlab-ci\.yaml$/u
    ]
  },
  {
    tests: ["plugins/harness"],
    patterns: [/^plugins\/harness\//u]
  },
  {
    tests: ["rules"],
    patterns: [/^rules\//u, /^oxlint\.config\.ts$/u]
  },
  {
    tests: ["rules/formal-layout.test.ts"],
    patterns: [
      /^\.claude\/skills\/formal-verification\/scripts\/check-layout\.ts$/u,
      /^\.claude\/skills\/formal-verification\/assets\/.+\.(?:cfg|tla)$/u,
      /^\.claude\/skills\/formal-verification\/references\/.+\.(?:mmd|mnd|svg)$/u
    ]
  },
  {
    tests: ["plugins/java/tests"],
    patterns: [/^plugins\/java\//u]
  },
  {
    tests: ["plugins/spec-driven-development/tests"],
    patterns: [/^plugins\/spec-driven-development\//u]
  },
  {
    tests: ["plugins/observability-assets/tests/promtool/release.test.ts"],
    patterns: [/^plugins\/observability-assets\//u]
  },
  {
    tests: ["rules/markdownlint-cli2.test.ts"],
    patterns: [
      /^\.gitignore$/u,
      /^\.markdownlint-cli2\.jsonc$/u,
      /^plugins\/harness\/tooling\/pnpm\/rules\/(?:no-em-dash|no-relative-skill-links|table-separators)\.ts$/u
    ]
  },
  {
    tests: ["plugins/harness/tooling/pnpm/config.test.ts"],
    patterns: [
      /^\.markdownlint-cli2\.jsonc$/u,
      /^oxlint\.config\.ts$/u,
      /^tsconfig\.json$/u
    ]
  },
  {
    tests: ["plugins/harness/tooling/pnpm/rules/markdown-rules.test.ts"],
    patterns: [/^\.github\/scripts\/sync-sensitive-content\.ts$/u]
  }
] satisfies readonly TestScope[];

export const selectChecks = (stagedPaths: readonly string[]): string[] => {
  const matchesPath = ({
    patterns
  }: {
    readonly patterns: readonly RegExp[];
  }) =>
    stagedPaths.some((stagedPath) =>
      patterns.some((pattern) => pattern.test(stagedPath))
    );
  if (
    stagedPaths.some((stagedPath) =>
      allCheckPatterns.some((pattern) => pattern.test(stagedPath))
    )
  ) {
    return ["check:*"];
  }
  const selectedChecks = new Set(
    scopes
      .filter(matchesPath)
      .flatMap(({ checks: scopedChecks }) => scopedChecks)
  );
  const selectedTests = [
    ...new Set(
      testScopes
        .filter(matchesPath)
        .flatMap(({ tests: scopedTests }) => scopedTests)
    )
  ];
  const runAllTests = stagedPaths.some((stagedPath) =>
    allTestPatterns.some((pattern) => pattern.test(stagedPath))
  );
  const harnessTests = selectedTests.filter(
    (test) => test === "plugins/harness" || test.startsWith("plugins/harness/")
  );
  const otherTests = selectedTests.filter(
    (test) => test !== "plugins/harness" && !test.startsWith("plugins/harness/")
  );
  return [
    ...selectedChecks,
    ...(runAllTests ? ["check:test"] : []),
    ...(runAllTests || otherTests.length === 0
      ? []
      : [
          `check:test:unit --exclude=plugins/harness/** ${otherTests.join(" ")}`
        ]),
    ...(runAllTests || harnessTests.length === 0
      ? []
      : [`check:test:unit ${harnessTests.join(" ")}`])
  ];
};

const runCli = (): void => {
  const staged = spawnSync(
    "git",
    ["diff", "--cached", "--no-renames", "--name-only", "-z"],
    { encoding: "utf-8" }
  );
  if (staged.status !== 0 || staged.stdout === null) {
    throw new Error(
      `git diff failed with status ${staged.status}: ${staged.stderr}`
    );
  }
  const stagedPaths = staged.stdout.split("\0").filter(Boolean);
  const checks = selectChecks(stagedPaths);
  if (
    stagedPaths.length > 0 &&
    !checks.includes("check:formal-layout") &&
    !checks.includes("check:*")
  ) {
    const shebangs = spawnSync(
      "git",
      [
        "--literal-pathspecs",
        "grep",
        "--cached",
        "--text",
        "--line-number",
        "--null",
        "--only-matching",
        "--max-count=1",
        "-e",
        "^#!",
        "--",
        ...stagedPaths
      ],
      { encoding: "utf-8" }
    );
    if (shebangs.error || (shebangs.status !== 0 && shebangs.status !== 1)) {
      throw shebangs.error ?? new Error(shebangs.stderr.trim());
    }
    if (/\0\d+(?::|\0)#!\n/u.test(shebangs.stdout)) {
      checks.push("check:formal-layout");
    }
  }
  if (checks.length === 0) {
    return;
  }
  const result = spawnSync("pnpm", ["exec", "run-p", ...checks], {
    stdio: "inherit"
  });
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
