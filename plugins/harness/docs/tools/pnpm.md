---
title: pnpm
metadata:
  reference:
    pnpm:
      url:
        - https://pnpm.io/installation
        - https://pnpm.io/catalogs
        - https://pnpm.io/cli/install
    TypeDoc:
      url:
        - https://typedoc.org/documents/Options.Input.html
        - https://typedoc.org/documents/Options.Validation.html
    Node.js:
      url: https://nodejs.org/en/about/previous-releases
    Oxlint:
      version: 1.90.0
      url:
        - https://oxc.rs/docs/guide/usage/linter/config.md
        - https://oxc.rs/docs/guide/usage/linter/writing-js-plugins.md
        - https://www.npmjs.com/package/oxlint/v/1.90.0
    Oxfmt:
      version: 0.75.0
      url:
        - https://oxc.rs/docs/guide/usage/formatter/config.md
        - https://www.npmjs.com/package/oxfmt/v/0.75.0
    Markdownlint CLI2:
      version: 0.24.0
      url: https://raw.githubusercontent.com/DavidAnson/markdownlint-cli2/v0.24.0/README.md
    Markdownlint:
      version: 0.42.0
      url:
        - https://raw.githubusercontent.com/DavidAnson/markdownlint/v0.42.0/doc/Rules.md
        - https://raw.githubusercontent.com/DavidAnson/markdownlint/v0.42.0/doc/CustomRules.md
    Search-replace rule:
      version: 1.2.0
      url: https://raw.githubusercontent.com/OnkarRuikar/markdownlint-rule-search-replace/main/README.md
    React lint rules:
      version: 7.1.1
      url:
        - https://react.dev/reference/eslint-plugin-react-hooks
        - https://react.dev/reference/eslint-plugin-react-hooks/lints/set-state-in-render
        - https://react.dev/reference/eslint-plugin-react-hooks/lints/set-state-in-effect
    Oxlint React Compiler rules:
      url: https://oxc.rs/docs/guide/usage/linter/rules/react/set-state-in-render.html
    Ultracite:
      version: 8.1.0
      url: https://raw.githubusercontent.com/haydenbleasel/ultracite/ultracite%408.1.0/packages/cli/README.md
    TypeScript compiler options:
      url:
        - https://www.typescriptlang.org/tsconfig/exactOptionalPropertyTypes.html
        - https://www.typescriptlang.org/tsconfig/noUncheckedIndexedAccess.html
        - https://www.typescriptlang.org/tsconfig/verbatimModuleSyntax.html
    npm-run-all2:
      version: 9.0.4
      url: https://registry.npmjs.org/npm-run-all2/9.0.4
    Husky:
      version: 9.1.7
      url:
        - https://raw.githubusercontent.com/typicode/husky/v9.1.7/docs/get-started.md
        - https://raw.githubusercontent.com/typicode/husky/v9.1.7/docs/how-to.md
    Oxc Project Types:
      version: 0.156.0
      url: https://registry.npmjs.org/@oxc-project/types/0.156.0
---

Use this profile for a pnpm-managed JavaScript, TypeScript, or JSX project.
Use the [project baseline](baseline.md) for eligibility and fresh-root materialization.
For frontend or React code, also read [frontend guidance](frontend.md).

## Detection And Selection

Select pnpm explicitly when the target uses pnpm.
Automatic detection requires `pnpm-lock.yaml`, `pnpm-workspace.yaml`, or a `packageManager` value beginning with `pnpm@`.
A `package.json` file alone does not prove that the target uses pnpm.
Inspect each package root in a monorepo and install the profile at each selected root.

## Managed Sources

The profile provides `oxlint.config.ts`, three independent style configs, `oxfmt.config.ts`, `plugins/`, `rules/`, and `.markdownlint-cli2.jsonc` as native lint and format sources.
The style configs are `oxlint.style-no-blank-lines-in-functions.config.ts`, `oxlint.style-no-inline-comments-in-functions.config.ts`, and `oxlint.style-sibling-test-spacing.config.ts`.
The base configuration owns core, TSDoc, and non-style rules.
Merge its settings into an existing Oxlint configuration without replacing the target's presets or rules.
Keep the profile's relative JavaScript plugin at the top level because Oxlint rejects relative plugin specifiers inside extended configurations.
Preserve both configurations' plugins, overrides, environment, and ignore patterns during the merge.
Each style configuration registers one plugin and one canonical `style/<rule>` identifier.
Copy `plugins/` next to the Oxlint configs because their `jsPlugins` entries use `./plugins/*.ts` paths.
Copy `rules/` next to `.markdownlint-cli2.jsonc` because its `customRules` entries use `rules/*.ts` paths.
Omit the `*.test.ts` files from both copies.
It provides `.editorconfig` only when the target has no existing editor configuration or when the user approves a bounded merge.
The fragment is a regular source file, not an instruction to overwrite an existing editor configuration.
It does not provide a complete replacement `package.json`.
Copy `package.fragment.json`, `pnpm-workspace.fragment.yaml`, and `tsconfig.fragment.json` as merge guidance only.
Do not copy any fragment over an existing manifest, workspace configuration, or compiler configuration.

## Bounded Manifest Merge

Preserve the target package name, workspaces, package manager, dependency versions, scripts, and unrelated fields.
Add missing `check`, `fix`, `pre-commit`, `pre-push`, and `prepare` script entries after reviewing existing commands.
Add `test` only when the target has a native test owner.
If an existing script owns one of these names, preserve it and report the required command substitution.
Do not silently replace an existing native check, formatter, test runner, or build task.
Before adding or upgrading any profile dependency, check the npm registry for its latest stable release compatible with the target's runtime, Oxc plugin API, and lockfile.
Keep existing compatible target pins and lockfile choices.
Store shared versions in `pnpm-workspace.yaml` catalogs and reference them with `catalog:` or the target's named catalog.
Merge missing catalog entries without replacing existing versions or unrelated catalogs.
Preserve existing workspace package globs and unrelated settings when merging `pnpm-workspace.yaml`.
Resolve catalog references from the target's workspace root before running installation.
Merge the `allow-builds.fragment.yaml` entry for esbuild only when the selected Node tooling requires its native binary installation.
Preserve existing build approval policy and review conflicts instead of approving every dependency's scripts.
Do not add direct `oxlint` or `oxfmt` script entries.
Route lint and format execution only through Ultracite.
Add `npm-run-all2` only when the merged scripts use `run-p` or `run-s`.
Add `@oxc-project/types` only when the target keeps TypeScript custom plugins and the target's Oxc version requires its declarations.
Use the [Git hook integration](#git-hook-integration) for the script surface.

## API Documentation

For a library with public JavaScript or TypeScript exports, merge `tooling/pnpm/api-docs.package.fragment.json` and `tooling/pnpm/api-docs.pnpm-workspace.fragment.yaml` into the target manifest and catalog.
Use a TypeDoc version compatible with the target's TypeScript version and Node runtime.
Check the selected TypeDoc release's published TypeScript peer range and preserve a compatible target pin.
Copy `tooling/pnpm/typedoc.json` to the project root when no TypeDoc configuration exists, or merge its settings without replacing the target's entry points or other options.
The configuration writes HTML to `build/api-docs` and makes warnings fail the command.
Ignore that generated directory in version control.

TypeDoc discovers entry points from `exports` or `main` in `package.json` when those resolve to the source.
Otherwise set `entryPoints` in the target's `typedoc.json`, or pass the actual public entry points after `pnpm exec typedoc`.
For JavaScript, configure TypeScript's `allowJs` and include the source in the target's `tsconfig.json`.
For a monorepo, choose the package entry points or TypeDoc's packages strategy according to the target's exports.
Run `pnpm run api:docs` after installing dependencies and inspecting the selected entry points.
Fix invalid links and other emitted warnings in the source or target configuration.
Do not enable blanket missing-comment diagnostics that require text on every trivial symbol.
The generated site reflects only the selected public entry points; review the output for missing public modules.

## Git Hook Integration

Add the compatible Husky catalog entry and development dependency when installing this script surface.
The `prepare` script runs `husky` to install its Git dispatchers.
Copy `.husky/pre-commit` and `.husky/pre-push` from the selected profile's tooling directory to the selected Git root.
Use `tooling/pnpm/.husky` for Node, frontend, and React, and `tooling/gradle/.husky` for Kotlin.
Copy `.husky/.gitignore` from the pnpm tooling source for every profile.
The Husky hook files invoke `pnpm run pre-commit` or `pnpm run pre-push`.
The matching package scripts define the sequential `run-s` check configurations.
Git and manual pnpm runs execute the same configurations.
Each configuration runs the target's `check` gate before the profile's selected native tasks.
Keep the generated `.husky/_/` dispatchers ignored.

Inspect `core.hooksPath`, existing Git hooks, and any existing `prepare` command before installation.
Merge an existing lifecycle command explicitly and preserve its behavior.
Resolve an existing hook manager's ownership before installing Husky instead of creating competing dispatchers or overwriting hooks.
In a monorepo, keep one hook owner at the Git root and compose the selected package checks through that owner's scripts.
Do not install independent Git dispatchers in each package.
After merging, run `pnpm install` and `pnpm run prepare` at the selected root.
Verify actual Git commit and push invocations in a disposable repository, including a failing check that blocks each operation.
Manual script execution alone does not prove hook installation.

The base and style configurations extend the target-installed Ultracite Oxc core preset.
The base configuration owns TSDoc and non-style checks.
Each style configuration preserves `core.env` and `core.ignorePatterns`, registers one plugin named `style`, and enables only its original canonical rule.
The blank-line pass checks physical blank lines inside executable function bodies.
The inline-comment pass diagnoses explanatory comments and preserves declaration documentation, required directives, and license markers.
The sibling-spacing pass requires one blank line only when an unshadowed named import from Vitest, Jest globals, or Bun proves a registration boundary.
Direct `describe` callbacks and program-level registrations are recognized, including named import aliases, static `only`, `skip`, `concurrent`, and `sequential` modifiers, and direct `each` factories.
Global names, namespace calls, unknown factories, shadowed bindings, ordinary callbacks, and gaps containing comments remain outside the registration-spacing diagnostic.
Required directives between registrations stay intact because moving them can change their target.
The built-in test-padding rule supplies padding around test calls but does not express this exact sibling lifecycle and proven-binding contract, so the AST-aware diagnostic handles it without an automatic fix.
The inline-comment pass exempts recognized tool directive comments, such as `@ts-expect-error`, `@ts-ignore`, `eslint-disable*`, `oxlint-disable*`, `oxfmt-ignore`, `prettier-ignore`, and `v8 ignore`.
The TSDoc plugin checks the [TypeScript declaration documentation requirements](../languages/typescript.md#exports-and-tsdoc).
Read those requirements when configuring TypeScript documentation checks.
Protected, private, and `#private` members and constructors are exempt.
It reports missing documentation separately from invalid delimiter placement.
For local identifiers exported indirectly, a declaration or export comment satisfies the check.
External re-exports and non-identifier default or assignment expressions are not checked.
The JavaScript override retains the JSDoc parameter and return checks while allowing return descriptions.
The custom plugin API is alpha and must be checked against the target Oxlint version before updates.
Markdownlint support is a common shared capability installed at the target project root.
Merge the canonical `.markdownlint-cli2.jsonc` from this profile into an existing target configuration, or create it when none exists.
The source schema path resolves the installed dependency from the repository root.
When merging the configuration manually, set `$schema` to `./node_modules/markdownlint-cli2/schema/markdownlint-cli2-config-schema.json` relative to the target root.
The baseline configurator makes that adjustment when creating the target configuration.
The profile ships its custom Markdownlint rules under `rules/`.
Configured `search-replace` from `markdownlint-rule-search-replace` replaces the retired `docs/no-box-drawing` implementation.
It reports the first U+2500 through U+257F character per line, including code, with its UTF-16 source column.
The profile supplies no replacement text or automatic fix.
Keep its dependency and catalog entry when merging the shared Markdown configuration.
`docs/table-separators` requires exactly three hyphens in the delimiter row that follows a table header, allows alignment colons, and ignores fenced code and ordinary data rows.
Sentence-per-line formatting remains an authoring guideline rather than an enforced Markdownlint rule.
`docs/no-em-dash` reports em dashes in authored prose in paragraphs, list items, and GitHub alerts.
`docs/no-relative-skill-links` reports relative `SKILL.md` destinations in authored links and reference definitions.
Ordinary reference links and absolute URLs remain valid.
The link rule classifies external targets from the raw destination and removes query or fragment data before comparing paths.
It normalizes only ASCII unreserved percent escapes once, following [RFC 3986 Section 6.2.2.2](https://www.rfc-editor.org/rfc/rfc3986.html#section-6.2.2.2).
Reserved delimiters such as `%2F`, `%3A`, `%3F`, and `%23` stay encoded.
Control bytes, non-ASCII bytes, and malformed escapes stay literal.
HTML character references, backslash escapes, and recursive percent decoding remain outside the normalization.
The rule never rewrites links or reported source ranges.
These two authored-prose rules use separate rule files and share `markdown-prose.ts` for token, quotation, and document protections.
The punctuation rule checks visible prose, while the link rule inspects destination tokens without treating them as prose.
The `docs/no-em-dash` and `docs/no-relative-skill-links` rules preserve code, frontmatter, quotations, and generated or licensed documents without automatic fixes.
The Sinon repository enables these two rules only for Harness content, while the portable profile enables them for installed projects.
Copy that directory alongside the config so the `customRules` paths resolve.
The profile-owned `ignores` entry excludes `node_modules` even when a fresh target has no `.gitignore`.
Retain the target's existing ignore configuration when merging.
Do not copy the root-only custom rule paths from other repositories.
The Sinon repository root loads thin entry points from `rules/`.
Those entry points re-export the canonical rules from `tooling/pnpm/rules/` inside this package.
Add `markdownlint-cli2` as a development dependency and `check:markdownlint-cli2` plus `fix:markdownlint-cli2` script entries through the manifest merge above.
The `check:markdownlint-cli2` task runs the plain `markdownlint-cli2` command.
The `fix:markdownlint-cli2` task adds `--fix`.
The selected target runtime must support loading the profile's TypeScript custom rules natively.
Both resolve configuration and globs from the config file.

### Sensitive-Content Export

Use `sinon/rules/no-sensitive-content` for the opt-in, diagnostic-only Markdownlint rule.
The existing private Sinon root package exports the canonical `tooling/pnpm/rules/no-sensitive-content.mjs` implementation.
It loads as native ESM without a TypeScript loader, build step, or runtime dependency.
The root's `0.0.0` version enables packaging and does not identify an npm registry release.
Install from a named Sinon branch containing this export, preserving the target's package manager and dependency policy.

```sh
pnpm add --save-dev "sinon@git+https://github.com/ririnto/sinon.git#main"
```

Git dependencies retain the root's existing `prepare: husky` lifecycle and install its development dependencies during preparation.
pnpm can reject preparation with `ERR_PNPM_GIT_DEP_PREPARE_NOT_ALLOWED` until the consumer explicitly approves that dependency.
Review the resolved candidate and approve only its exact package key through the consumer's native `allowBuilds` policy.
Use the candidate-specific key reported by pnpm rather than granting wildcard or global trust.
Preserve existing approvals and all other security checks.
The downstream owner retains this approval decision.
Normal Git dependency installation was verified with pnpm `11.28.2` and `12.15.0` on Node.js `24.22.1`.
The source package retains its declared Node.js engine range and pnpm version for preparation.

Merge these entries into the consumer's existing `.markdownlint-cli2.jsonc`, preserving its other rules, globs, and ignores.

```json
{
  "config": {
    "sensitive-content": true
  },
  "customRules": ["sinon/rules/no-sensitive-content"]
}
```

Keep the consumer's existing Markdownlint CLI2 dependency and check command.
Run `pnpm exec markdownlint-cli2` through its normal configuration.
Package subpath loading and CLI2 diagnostics were verified with Markdownlint CLI2 `0.24.0` and Markdownlint `0.42.0`.
Keep an existing consumer implementation until the exported rule passes its complete applicable current Markdown corpus and security controls.
Remove the superseded local implementation and loader entry together during the consumer's authorized migration.
The portable profile and Sinon root do not enable this rule automatically.

The rule keeps the `sensitive-content` identifier, `security` tag, and `parser: none` contract.
It checks Markdownlint-provided lines, including prose, inline code, indented code, and fenced code.
Markdownlint preprocesses frontmatter and HTML comments before invoking the rule, so their content is outside this coverage.
Each affected line receives one generic diagnostic without a value, context excerpt, range, or fix.
Applying Markdownlint fixes leaves the input unchanged for this rule.

Credential patterns cover Telegram tokens, GitHub tokens, AWS access keys, Google API keys, Slack tokens, bearer credentials, and `sk-` keys.
The first matching credential assignment on each line is checked for a value of at least 20 supported characters.
Assignment names include token, API key, secret, password, private key, and access key forms.
Values containing recognized example, sample, replacement, placeholder, changeme, or your-token markers are treated as assignment placeholders.
Those markers do not exempt independent token, path, or hash matches on the same line.
Home-path patterns cover Unix and Windows user directories, preserving the exact macOS Shared and Guest directory exceptions.
Relative paths and portable home-variable examples remain valid.
Hash patterns cover hexadecimal Git URL identifiers, labelled Git or file hashes, and bare 40- or 64-character hexadecimal identifiers.

These are bounded lexical heuristics, not validation of credential validity, filesystem identity, or secret completeness.
Short, encoded, unfamiliar, or differently delimited values can escape detection.
A placeholder assignment before another assignment on the same line can mask the later assignment.
Public hashes, incidental hexadecimal identifiers, and placeholder-like real values can be misclassified.
Review diagnostics and validate every target corpus before adoption without weakening existing security checks.
Passing establishes only that the supported patterns were absent from the supplied lines.

| Pillar | Interface Contract |
| --- | --- |
| Stability | Maintained controls preserve security diagnostics, native loader behavior, and failures for unavailable exports. |
| Consistency | Consumers share one rule identity, one diagnostic per affected line, and their existing CLI2 command. |
| Maintainability | One canonical implementation and one explicit package subpath replace consumer-owned copies after verified migration. |
| Performance | Bounded in-process line scanning uses fixed patterns without subprocess scanners or a measured-speed claim. |

## React Effect Policy

`oxlint.config.ts` owns the custom direct-effect policy and maintained upstream React checks alongside the base rules.
The [rule mapping](lint-rule-mapping.md) records exact IDs, upstream comparisons, and consumer migration boundaries.
Projects using React directly or through framework dependencies consume this same configuration through Ultracite.
The shared Node profile also installs it because source-proven matching leaves unrelated code outside the custom diagnostic.
Merge the base configuration into the target's existing configuration and keep the independent style configurations intact.
For an existing React-effect pass, migrate any target-owned rules and settings that the merged base still needs.
Update or remove the obsolete `ultracite:check:react-effects` script and its references to `oxlint.react-effects.config.ts`, preserving unrelated commands.
Remove the old configuration only after confirming no retained command references it.

The configuration registers `eslint-plugin-react-hooks` with the supported `jsPlugins` object alias.
Its `name` is `react-hooks-js`, and its `specifier` is `eslint-plugin-react-hooks`.
The configuration and mapping table own the adopted upstream rule inventory.
Declare `eslint-plugin-react-hooks` through the supplied dependency and catalog fragments.
Keep compatible Oxlint, Ultracite, and Oxc type dependencies from the shared profile.
Oxlint loads these rules inside Ultracite without a separate ESLint invocation or wrapper.

`react-effects/no-direct-use-effect` reports calls proven to originate from React's runtime export.
The self-contained `plugins/no-direct-use-effect.ts` has the same identity and behavior in the standalone Sinon distribution and this profile.
Supported imports include named, default, namespace, string-named, and aliased forms.
Property access supports identifier properties and computed string-literal keys, including optional calls.
The resolver also follows const aliases, useEffect destructuring, and supported unshadowed CommonJS require forms.
Type-only imports, unrelated hooks, local shadowing, Preact imports, and unresolved globals supply no runtime React origin proof.
File extensions and unrelated React imports do not establish the called binding's origin.

Mutable aliases, dynamic imports, cross-module re-exports, template-literal keys, runtime-computed properties, and call/apply indirection remain outside this source-local analysis.
Auto-imported globals need target-specific import evidence rather than filename heuristics.
The rule reports diagnostics without fixes because replacing effects can change lifecycle timing and cleanup.
The [mapping](lint-rule-mapping.md#oxlint-policies) records upstream fixture results and compiler coverage limits.
The custom call diagnostic covers a separate requirement from the upstream checks.

`ultracite:check:base` runs the base configuration with type awareness, including the React policy.
The shared check and fix commands include the base configuration without additional formatter-writing passes.
Verify both positive and negative target fixtures before claiming consumer adoption.

## Type-Aware Promise Checks

Install the profile's compatible `oxlint-tsgolint` development dependency alongside Ultracite.
The profile scripts pass `--type-aware` through Ultracite and the native configuration enables the built-in `typescript/no-floating-promises` rule with `ignoreVoid: false`.
The regression fixture invokes only Ultracite, proves the diagnostic disappears when type awareness is off, and proves normal fixes preserve `void` expressions.
The rule diagnoses unhandled promises rather than every use of `void`.
It accepts explicit rejection handling, awaited calls, and returned promises.
It provides suggestions upstream, but normal Ultracite fixes do not rewrite `void` into `await`.
Keep that conversion under the [common async policy](../rules.md#code-rules-common-to-all-languages) because scheduling and cancellation require semantic review.
The Sinon root `ultracite:check` script omits `--type-aware`.
Adding the flag reports this configuration diagnostic:

```text
typescript(tsconfig-error): Invalid tsconfig
Option 'allowImportingTsExtensions' can only be used when one of 'noEmit', 'emitDeclarationOnly', or 'rewriteRelativeImportExtensions' is set.
```

The root `tsconfig.json` enables `allowImportingTsExtensions` without `noEmit`, `emitDeclarationOnly`, or `rewriteRelativeImportExtensions`.
The root lint command runs without the flag.
The separate root `check:typescript` script runs `tsc --noEmit`, which does not configure Ultracite's TypeScript checks.
A passing root check does not establish that the profile's type-aware promise diagnostic ran.
Use the target's real TypeScript project configuration and verify type-aware execution before relying on this diagnostic.
The tested backend contract is documented in the [Oxlint rule implementation](https://github.com/oxc-project/oxc/blob/oxlint_v1.90.0/crates/oxc_linter/src/rules/typescript/no_floating_promises.rs) and the [Ultracite command implementation](https://github.com/haydenbleasel/ultracite/blob/ultracite%408.1.0/packages/cli/src/commands/check.ts).

## TypeScript Strictness Merge

`tsconfig.fragment.json` lists strictness flags that the maintainer's TypeScript projects already use.
They are `exactOptionalPropertyTypes`, `noFallthroughCasesInSwitch`, `noImplicitOverride`, `noImplicitReturns`, `noPropertyAccessFromIndexSignature`, `noUncheckedIndexedAccess`, and `verbatimModuleSyntax`.
Merge only the flags the target's `compilerOptions` do not already set, and preserve any value the target chose.
Keep the target's `extends`, `module`, `target`, `include`, and `types` settings unchanged.
Each flag can introduce new compiler errors in existing code, so run `pnpm exec tsc --noEmit` after merging and report the new errors instead of fixing unrelated source.
Skip the fragment for a JavaScript-only target.
`verbatimModuleSyntax` requires `import type` for type-only imports, which matches the profile's `typescript/consistent-type-imports` rule.

## Commands

Run `pnpm install` after a dependency change.
Run `pnpm run check` for read-only lint and formatting checks owned by the target package.
The `check:ultracite` task runs the base and three style passes through `run-p --continue-on-error ultracite:check:**`.
The base pass includes the React policy, and the recursive pattern includes the independent style configurations.
All passes report findings before the combined command fails if any pass fails.
Each Ultracite check reports findings without writing source changes.
The nested `ultracite:` scripts keep the top-level `run-p check:*` from discovering style passes twice.
The `check:markdownlint-cli2` task lints Markdown files at the project root without writing changes.
Run `pnpm run fix` within the authorized source-edit scope.
The `fix:ultracite` task runs one formatter-writing base fix, then read-only style checks through `run-s --continue-on-error`.
The command preserves a failing exit status while collecting diagnostics from every pass.
The `fix:markdownlint-cli2` task applies Markdownlint fixes.
Run `pnpm run test` only when the target owns that native test command.
The source profile uses `vitest run` for non-watching tests.
Omit the test command when the target has no native test owner rather than creating a dummy suite or relying on an empty-suite flag.
Run `pnpm exec tsc --noEmit` only when the target has TypeScript sources, a compiler configuration, and compiler dependencies.
Provide the selected test runner's declarations when compiler inputs include its tests.
Do not run a TypeScript compile check for a JavaScript-only target.

## Runtime And CI

Select the development runtime from the target's manifest and explicit requirements.
Check tool and custom plugin compatibility before changing the target's selected runtime.
Keep source execution requirements separate from development dependency engine requirements.

pnpm 12 is a native executable and does not require Node.js after installation.
Installing pnpm 12 through npm requires Node.js 22.13 or newer.
Bootstrap it with a compatible development Node.js version or use its standalone installer.
Pin pnpm through the target's `packageManager` field.
The GitHub and GitLab catalogs install the version specified in the target's `packageManager` field through npm.
Both catalogs supply Node.js 26 as a source example.
Replace that runtime selection when the target requires another compatible version.
The npm installation does not verify integrity suffixes in the `packageManager` field.
Adapt runtime versions, working directories, and image pins to the target's existing CI requirements.
CI runs `pnpm install --frozen-lockfile` when `pnpm-lock.yaml` exists, then the target's `pnpm run check` script.
Require a committed lockfile for a frozen installation.
Replace the install mode only for an authorized dependency update job or an explicit target requirement.
CI must run `pnpm run test` only when the target has a native test owner.
CI must run `pnpm exec tsc --noEmit` only when TypeScript is selected and a target compiler configuration exists.
Existing CI jobs and package scripts take precedence over these catalog entries.

## Source Profile Version Evidence

The source profile catalog fragments carry the selected version ranges.
Each target selects its own pnpm version.
The source-profile fragments are synchronized from the Sinon root catalog before publication.
The root `pnpm-workspace.yaml` catalog, `package.json`, and `pnpm-lock.yaml` own installed root tool versions.
Verify Ultracite upgrades against its selected-version source and supported Oxlint, Oxfmt, and plugin APIs.
The target owns final dependency versions through its manifest and lockfile.
The fragment's catalog ranges are source-profile evidence, not mandatory target pins.
Check npm before merging them.
