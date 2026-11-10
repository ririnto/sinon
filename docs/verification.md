# Verify A Change

Run commands from the repository root unless a command selects a package directory.
Use the Node.js range and pnpm version declared in `package.json`.
The complete check also needs `java`, `javac`, and `jar` on `PATH` for the Java launcher tests.
The Promtool lifecycle models use Java and the TLC distribution bundled with the locked `tla-chaos-loop` dependency.

```sh
pnpm install --frozen-lockfile
pnpm run check
```

The root `package.json` owns the Node.js engine range and pnpm pin, and `pnpm-workspace.yaml` owns dependency ranges.
Run `pnpm run versions:sync` after changing these values to update the self-contained plugin snapshots.
The complete gate checks those snapshots for drift.
Each installed target owns its resulting manifest and catalog independently.
The API documentation profile uses the named catalog for its compatible TypeScript peer.
The separately distributed specification plugin owns its standalone catalog and lockfile.

The root has no separate `build` or `test` script.
Its `check:*` scripts compose the component tests, TypeScript check, lint, and formatting.
The Promtool fixture runner requires a separate command and network access.

Harness fixtures use the profile's own pnpm version, which can differ from the root version.
Invoke pnpm directly so it selects each fixture's `packageManager` pin.

## Choose The Relevant Check

Start with the row matching the changed behavior, then run the complete gate before delivery.

| Changed surface | Command | Observable evidence |
| --- | --- | --- |
| Java launcher and Lombok discovery | `pnpm run check:java` | Shell probes exercise project discovery, and compiled JVM agents verify launcher arguments and options. |
| Harness installation, profiles, and custom lint rules | `pnpm run check:lint-rules` | Materialized configurations, hook entry points, and lint diagnostics run against temporary consumer projects. |
| Specification commands | `pnpm run check:sdd` | Command fixtures exercise frontmatter queries, validation, links, filtering, and diagram generation. |
| Promtool release verification and process handling | `pnpm run check:promtool` | Local archive and process fixtures test integrity checks and child-process behavior, while TLC checks bounded download and child lifecycle models. |
| Prometheus rule examples | `pnpm run test:promtool` | The verified official executable checks the rules and runs their expected alert cases. |
| TypeScript source | `pnpm run check:typescript` | The repository compiler configuration passes with emission disabled. |
| Source lint, formatting, and React effect policy | `pnpm run check:ultracite` | Root source and Harness tooling pass their configured lint and formatting checks, including the React effect policy. |
| Markdown and skill prose | `pnpm run check:markdownlint-cli2` | Markdown files satisfy the configured prose and structure rules. |

The root [package scripts](../package.json) own command composition.
The [Harness pnpm reference](../plugins/harness/docs/tools/pnpm.md) explains differences between root checks and installed profile checks.
The [state-machine inventory](state-machines.md) maps maintained lifecycles to their source and verification.
The [Promtool model reference](../plugins/observability-assets/tests/promtool/model/README.md) records model assumptions and source mappings.
Run `pnpm exec run-p check:promtool:download check:promtool:child` to check those models alone.
TLC checks the finite models without network access and removes its temporary state files after each run.
The models supplement runtime tests and do not prove network cancellation, operating-system behavior, or source equivalence.

## Exercise The Consumer

After catalog edits, validate the Claude marketplace:

```sh
claude plugin validate .
```

For specification tooling, exercise the bundled valid tree through the shipped CLI:

```sh
node --import tsx plugins/spec-driven-development/skills/spec-driven-development/scripts/sdd.ts validate plugins/spec-driven-development/skills/spec-driven-development/references/examples/valid-spec-tree/spec
```

For API documentation integrations, run the relevant native fixtures with the tools described by each profile available on `PATH`:

```sh
sh plugins/harness/tests/fixtures/api-docs/verify.sh
python3 plugins/harness/tests/fixtures/jvm-documentation/verify.py
HARNESS_GO_DOCS_TEST=1 pnpm exec vitest run plugins/harness/tests/go-doc-generation.test.ts
```

The first fixture installs its temporary Node and Python dependencies and needs Cargo and uv.
The JVM fixture needs a JDK, Gradle, Maven, and dependency access.
The Go fixture needs Go and dependency access for the pinned tools.
These fixtures generate documentation and exercise diagnostic failures; the default Go test run skips the external-tool checks.

For Go workspace changes, run the native integration fixture:

```sh
HARNESS_GO_WORKSPACE_TEST=1 pnpm exec vitest run plugins/harness/tests/go-workspace.test.ts
```

The fixture needs Go and dependency access for the pinned Task tool.

After changing packaged plugin content, run the host validator in that package, for example:

```sh
cd plugins/java
claude plugin validate .
```

These checks establish source and package behavior within their fixtures.
Harness tests substitute native build commands where needed, so they do not establish a target application's Gradle, Maven, or Go build.
Markdown checks do not establish skill selection or model adherence.
For those outcomes, use the affected package's runtime instructions and observe the requested behavior in the target host or application.

## Record The Result

Report the command, exit status, observed result, and any unmet prerequisite.
Keep failed and unrun checks visible alongside passing checks.
For a defect, retain the failing reproduction and rerun the same input after the fix.
Describe fixture substitutions and untested host behavior so reviewers can judge the evidence without replaying the run.
