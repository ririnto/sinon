# Verify A Change

Run commands from the repository root unless a command selects a package directory.
Use the Node.js range and pnpm version declared in `package.json`.
The complete check also needs `java`, `javac`, and `jar` on `PATH` for the Java launcher tests.

```sh
pnpm install --frozen-lockfile
pnpm run check
```

The root has no separate `build` or `test` script.
Its `check:*` scripts compose the component tests, TypeScript check, lint, formatting, and catalog validation.
The Promtool fixture runner requires a separate command and network access.

Harness fixtures use the profile's own pnpm version, which can differ from the root version.
Invoke pnpm directly so it selects each fixture's `packageManager` pin.

## Choose The Relevant Check

Start with the row matching the changed behavior, then run the complete gate before delivery.

| Changed surface | Command | Observable evidence |
| --- | --- | --- |
| Marketplace generation and package identity | `pnpm run check:marketplace` | The checked-in Codex catalog matches its inputs, and generator fixtures exercise metadata, path boundaries, and output protection. |
| CodeGraph startup and worktree hooks | `pnpm run check:codegraph` | Shell hooks run against real Git fixtures with a substituted `npx` executable. |
| Java launcher and Lombok discovery | `pnpm run check:java` | Shell probes exercise project discovery, and compiled JVM agents verify launcher arguments and options. |
| Harness installation, profiles, and custom lint rules | `pnpm run check:lint-rules` | Materialized configurations, hook entry points, and lint diagnostics run against temporary consumer projects. |
| Specification commands | `pnpm run check:sdd` | Command fixtures exercise frontmatter queries, validation, links, filtering, and diagram generation. |
| Promtool release verification and process handling | `pnpm run check:promtool-release` | Local archive and process fixtures test integrity checks and child-process behavior. |
| Prometheus rule examples | `pnpm run test:promtool` | The verified official executable checks the rules and runs their expected alert cases. |
| TypeScript source | `pnpm run check:typescript` | The repository compiler configuration passes with emission disabled. |
| Source lint and formatting | `pnpm run check:ultracite` | Root source and the hidden marketplace tooling pass their configured lint and formatting checks. |
| React effect policy | `pnpm run check:react-effects` | The separate React policy configuration accepts the repository source. |
| Markdown and skill prose | `pnpm run check:markdownlint-cli2` | Markdown files satisfy the configured prose and structure rules. |

The root [package scripts](../package.json) own command composition.
The [Harness pnpm reference](../plugins/harness/docs/tools/pnpm.md) explains differences between root checks and installed profile checks.

## Exercise The Consumer

After catalog edits, regenerate the Codex view and check it before committing both catalog files:

```sh
pnpm run marketplace:generate
pnpm run check:marketplace
```

For specification tooling, exercise the bundled valid tree through the shipped CLI:

```sh
node --import tsx plugins/spec-driven-development/skills/spec-driven-development/scripts/sdd.ts validate plugins/spec-driven-development/skills/spec-driven-development/references/examples/valid-spec-tree/spec
```

After changing packaged plugin content, run the host validator in that package, for example:

```sh
cd plugins/java
claude plugin validate .
```

These checks establish source and package behavior within their fixtures.
CodeGraph tests substitute the package runner, so they do not establish upstream indexing or MCP availability.
Harness tests substitute native build commands where needed, so they do not establish a target application's Gradle, Maven, or Go build.
Markdown checks do not establish skill selection or model adherence.
For those outcomes, use the affected package's runtime instructions and observe the requested behavior in the target host or application.

## Record The Result

Report the command, exit status, observed result, and any unmet prerequisite.
Keep failed and unrun checks visible alongside passing checks.
For a defect, retain the failing reproduction and rerun the same input after the fix.
Describe fixture substitutions and untested host behavior so reviewers can judge the evidence without replaying the run.
