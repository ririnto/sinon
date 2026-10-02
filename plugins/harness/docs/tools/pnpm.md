---
metadata:
  reference:
    pnpm:
      version: 12.8.1
      url:
        - https://pnpm.io/installation
        - https://pnpm.io/catalogs
        - https://pnpm.io/cli/install
    pnpm setup:
      version: 3.0.0
      url: https://github.com/pnpm/setup/blob/v3.0.0/README.md
    Node.js:
      url: https://nodejs.org/en/about/previous-releases
    Corepack:
      url: https://nodejs.org/docs/latest-v24.x/api/corepack.html
    Oxlint:
      url:
        - https://oxc.rs/docs/guide/usage/linter/config.html
        - https://oxc.rs/docs/guide/usage/linter/writing-js-plugins.html
    Oxfmt:
      url: https://oxc.rs/docs/guide/usage/formatter/config.html
    Markdownlint CLI2:
      version: 0.23.3
      url: https://github.com/DavidAnson/markdownlint-cli2/blob/v0.23.3/README.md
    Ultracite:
      version: 7.12.2
      url: https://github.com/haydenbleasel/ultracite/blob/ultracite%407.12.2/packages/cli/README.md
    npm-run-all2:
      version: 9.0.3
      url: https://registry.npmjs.org/npm-run-all2/9.0.3
    Oxc Project Types:
      version: 0.152.0
      url: https://registry.npmjs.org/@oxc-project/types/0.152.0
---

# pnpm

Use this profile for a pnpm-managed JavaScript, TypeScript, or JSX project.

## Detection And Selection

Select pnpm explicitly when the target uses pnpm.
Automatic detection requires `pnpm-lock.yaml`, `pnpm-workspace.yaml`, or a `packageManager` value beginning with `pnpm@`.
A `package.json` file alone does not prove that the target uses pnpm.
Inspect each package root in a monorepo and install the profile at each selected root.

## Managed Sources

The profile provides `oxlint.config.ts`, `oxfmt.config.ts`, `plugins/`, `rules/`, and `.markdownlint-cli2.jsonc` as one native lint and format source.
It provides `.editorconfig` only when the target has no existing editor configuration or when the user approves a bounded merge.
The fragment is a regular source file, not an instruction to overwrite an existing editor configuration.
It does not provide a complete replacement `package.json`.
Copy `package.fragment.json` and `pnpm-workspace.fragment.yaml` as merge guidance only.
Do not copy either fragment over an existing manifest or workspace configuration.

## Bounded Manifest Merge

Preserve the target package name, workspaces, package manager, dependency versions, scripts, and unrelated fields.
Add only missing `check` and `fix` script entries after reviewing existing commands.
Add `test` only when the target has a native test owner.
If an existing script owns one of these names, preserve it and report the required command substitution.
Do not silently replace an existing native check, formatter, test runner, or build task.
Before adding or upgrading any profile dependency, check the npm registry for its latest stable release compatible with the target's runtime, Oxc plugin API, and lockfile.
Keep existing compatible target pins and lockfile choices.
Store shared versions in `pnpm-workspace.yaml` catalogs and reference them with `catalog:` or the target's named catalog.
Merge missing catalog entries without replacing existing versions or unrelated catalogs.
Preserve existing workspace package globs and unrelated settings when merging `pnpm-workspace.yaml`.
Resolve catalog references from the target's workspace root before running installation.
Do not add direct `oxlint` or `oxfmt` script entries.
Route lint and format execution only through Ultracite.
Add `npm-run-all2` only when the merged scripts use `run-p`.
Add `@oxc-project/types` only when the target keeps TypeScript custom plugins and the target's Oxc version requires its declarations.
Do not add Husky or create hooks automatically.

The source configuration extends the target-installed Ultracite Oxc presets.
The style plugin preserves the historical checks for blank lines and non-documentation comments inside block function bodies.
The TSDoc plugin checks exported TypeScript function, variable, and class declarations and public class methods.
It requires multiline TSDoc with opening and closing delimiters on separate lines.
This format is a local rule.
It reports missing documentation separately from invalid delimiter placement.
The JavaScript override keeps the historical JSDoc parameter and return checks while allowing return descriptions.
The custom plugin API is alpha and must be checked against the target Oxlint version before updates.
Markdownlint support is a common shared capability installed at the target project root.
Merge the canonical `.markdownlint-cli2.jsonc` from this profile into an existing target configuration, or create it when none exists.
The profile ships two custom Markdownlint rules, `docs/no-box-drawing` and `docs/table-separators`, under `rules/`.
Copy that directory alongside the config so the `customRules` paths resolve.
The profile-owned `ignores` entry excludes `node_modules` even when a fresh target has no `.gitignore`.
Retain the target's existing ignore configuration when merging.
Do not copy the root-only custom rule paths from other repositories.
Add `markdownlint-cli2` as a development dependency and `check:markdownlint-cli2` plus `fix:markdownlint-cli2` script entries through the manifest merge above.
The `check:markdownlint-cli2` task runs the plain `markdownlint-cli2` command.
The `fix:markdownlint-cli2` task adds `--fix`.
The selected target runtime must support loading the profile's TypeScript custom rules natively.
Both resolve configuration and globs from the config file.

## Commands

Run `pnpm install` after a dependency change.
Run `pnpm run check` for read-only lint and formatting checks owned by the target package.
The `check:ultracite` task runs `ultracite check`, which reports findings without writing source changes.
The `check:markdownlint-cli2` task lints Markdown files at the project root without writing changes.
Run `pnpm run fix` within the authorized source-edit scope.
The `fix:ultracite` task runs the Ultracite fixer.
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
The GitHub catalog uses `pnpm/setup@v3.0.0` and reads that pin from the target manifest.
The catalog supplies Node.js 24 as a source example.
Replace that runtime selection when the target requires another compatible version.
It does not infer pnpm from `package.json` alone.
The GitLab catalog supplies a Node.js 24 image example and enables Corepack to use the target's `packageManager` pin.
Adapt runtime versions, working directories, and image pins to the target's existing CI requirements.
CI runs `pnpm install --frozen-lockfile` when `pnpm-lock.yaml` exists, then the target's `pnpm run check` script.
Require a committed lockfile for a frozen installation.
Replace the install mode only for an authorized dependency update job or an explicit target requirement.
CI must run `pnpm run test` only when the target has a native test owner.
CI must run `pnpm exec tsc --noEmit` only when TypeScript is selected and a target compiler configuration exists.
Existing CI jobs and package scripts take precedence over these catalog entries.

## Source Profile Version Evidence

The source profile selects pnpm `12.8.1`, Oxlint `1.86.0`, Oxfmt `0.71.0`, Ultracite `7.12.2`, Markdownlint CLI2 `0.23.3`, and `@oxc-project/types` `0.152.0`.
The target owns final dependency versions through its manifest and lockfile.
The fragment's catalog ranges are source-profile evidence, not mandatory target pins.
Check npm before merging them.
