# Native Integration

## Native Source Mapping

| Target path | Canonical source |
| --- | --- |
| `<target-root>/.markdownlint-cli2.jsonc` | `<plugin-root>/tooling/pnpm/.markdownlint-cli2.jsonc` when the Node-side baseline is selected |
| `<target-root>/rules/<name>.ts` | `<plugin-root>/tooling/pnpm/rules/<name>.ts`, excluding `*.test.ts`, when the Node-side baseline is selected |
| `<target-git-root>/.husky/<name>` | `<plugin-root>/tooling/<profile>/.husky/<name>` for `pre-commit` and `pre-push` when the Node-side script surface is selected |
| `<target-git-root>/.husky/.gitignore` | `<plugin-root>/tooling/pnpm/.husky/.gitignore` for every selected profile |
| `<target-root>/oxlint.config.ts` | `<plugin-root>/tooling/pnpm/oxlint.config.ts` when pnpm is selected |
| `<target-root>/oxfmt.config.ts` | `<plugin-root>/tooling/pnpm/oxfmt.config.ts` when pnpm is selected |
| `<target-root>/plugins/<name>.ts` | `<plugin-root>/tooling/pnpm/plugins/<name>.ts`, excluding `*.test.ts`, when pnpm is selected |
| `<target-root>/typedoc.json` | `<plugin-root>/tooling/pnpm/typedoc.json` for a Node API profile |
| `<target-root>/docs/conf.py` and `docs/index.rst` | `<plugin-root>/tooling/uv/docs/` when uv is selected |
| `<target-root>/buildSrc/src/` | `<plugin-root>/tooling/kotlin/src/` for Kotlin Gradle targets |
| `<target-tooling>/kotlin/` | `<plugin-root>/tooling/kotlin/` for supported Kotlin Maven targets |

For a fresh root, run the baseline configurator with the selected eligibility value and project type before completing the selected native integration.
If the configurator returns `skipped`, stop without configuring the root.
It supplies the shared Node-side files and the selected profile's baseline native configuration from canonical sources.
It leaves project identity and native build integration with the target.
For frontend code, read [frontend guidance](../../../docs/tools/frontend.md) and select its compatible renderer and React dependencies only when applicable.
Read the target manifest, build files, editor configuration, CI, and project identity for each selected integration.
Use the profile's merge rules.
Add only missing settings and preserve target values on conflict.
Keep existing manifests, identity, source roots, toolchains, test owners, formatters, checkers, hooks, and unrelated CI jobs.
Do not replace whole configuration files or silently upgrade dependencies to match a profile.
Create native configuration only where the selected reference permits it and no target-owned file conflicts.
When the target owns a public API and its selected profile supports documentation generation, configure and run the generator against the actual public entry points.
Resolve actionable generator warnings and retain the configuration in the target without a dependency on the plugin checkout.

For pnpm, use the [bounded manifest, catalog, and Markdownlint merge](../../../docs/tools/pnpm.md#bounded-manifest-merge).
For pnpm, select and configure the target TypeScript base through the [TypeScript strictness merge](../../../docs/tools/pnpm.md#typescript-strictness-merge).
The pnpm profile owns the canonical Markdownlint configuration and rules, including config-based `node_modules` exclusion.
Its [Git hook integration](../../../docs/tools/pnpm.md#git-hook-integration) owns Husky setup and existing-hook conflicts for the Node-side script surface.
For Gradle, Maven, Go, Rust, uv, and shell, merge their canonical Node-side manifests through the [project baseline](../../../docs/tools/baseline.md).
Reuse the shared Markdown sources and select each profile's own Husky configurations.
For existing Gradle targets that do not use pnpm, adapt the Gradle hook commands to the target's package manager and preserve existing hooks and their owner.
Run the baseline's install, prepare, check, and hook proof using the corresponding target package-manager commands and adapted hooks instead of its pnpm commands.
This selection does not enable the JavaScript or TypeScript lint profile.
For Kotlin, retain the complete rule sources, resources, and tests.
For Gradle, merge them into `buildSrc` and configure its dependencies and checks as described in the Gradle reference.
For an existing target-owned service descriptor, preview a bounded merge that preserves its entries and adds the Harness provider only when absent.
Keep its ownership and file mode, and report `keep` on a repeat when the provider is already present.
For supported Maven targets, copy the complete native module and follow the Maven reference to attach its produced JAR to the actual ktlint runtime.
Verify that the native build graph runs the rules and their tests.
Otherwise report the integration gap.
Do not install the Kotlin rules when Kotlin is unselected.

Install CI catalogs only when explicitly selected, retaining least-privilege read permissions.
Adapt working directories, versions, stages, and test ownership to the target's existing jobs.
When no test owner exists, omit that test task and report it as not applicable.

## Hook and configuration mapping

Use the `pnpm` hook source for Node, frontend, and React, and `gradle` for Kotlin.
For Gradle targets using another package manager, adapt the source hook commands to invoke the corresponding target scripts with that manager.

In the target Markdownlint configuration, rewrite only the `$schema` path to `./node_modules/markdownlint-cli2/schema/markdownlint-cli2-config-schema.json`.

## Profile verification

A Kotlin integration needs the sources, tests, native configuration, and consumer-lint proof required by its selected tool reference.
A pnpm integration must resolve its Markdownlint configuration at the selected project root.
