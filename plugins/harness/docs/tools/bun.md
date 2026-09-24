---
metadata:
  reference:
    Bun:
      url: https://bun.com/docs/pm/cli/install
    oven-sh/setup-bun:
      version: 2.2.0
      url: https://github.com/oven-sh/setup-bun/blob/v2.2.0/README.md
    Oxlint:
      url:
        - https://oxc.rs/docs/guide/usage/linter/config.html
        - https://oxc.rs/docs/guide/usage/linter/writing-js-plugins.html
    Oxfmt:
      url: https://oxc.rs/docs/guide/usage/formatter/config.html
---

# Bun

Use this profile for a Bun-managed JavaScript, TypeScript, or JSX project.

## Detection And Selection

Select Bun explicitly when the target uses Bun.
Automatic detection requires `bun.lock`, `bun.lockb`, a `packageManager` value beginning with `bun@`, an `engines.bun` entry, or a user-selected Bun runtime.
A `package.json` file alone does not prove that the target uses Bun.
Inspect each package root in a monorepo and install the profile at each selected root.

## Managed Sources

The profile provides `oxlint.config.ts`, `oxfmt.config.ts`, `plugins/`, `rules/`, and `.markdownlint-cli2.jsonc` as one native lint and format source.
It provides `.editorconfig` only when the target has no existing editor configuration or when the user approves a bounded merge.
The fragment is a regular source file, not an instruction to overwrite an existing editor configuration.
It does not provide a complete replacement `package.json`.
Copy `package.fragment.json` as merge guidance only.
Do not copy it over an existing manifest.

## Bounded Manifest Merge

Preserve the target package name, workspaces, package manager, dependency versions, scripts, and unrelated fields.
Add only missing `check` and `fix` script entries after reviewing existing commands.
Add `test` only when the target has a Bun test owner.
If an existing script owns one of these names, preserve it and report the required command substitution.
Do not silently replace an existing native check, formatter, test runner, or build task.
Before adding or upgrading any profile dependency, check the npm registry for its latest stable release compatible with the target's runtime, Oxc plugin API, and lockfile.
Keep existing compatible target pins and lockfile choices.
Do not add direct `oxlint` or `oxfmt` script entries.
Route lint and format execution only through Ultracite.
Add `npm-run-all2` only when the merged scripts use `run-p`.
Add `@oxc-project/types` only when the target keeps TypeScript custom plugins and the target's Oxc version requires its declarations.
Do not add Husky or create hooks automatically.

The source configuration extends the target-installed Ultracite Oxc presets.
The style plugin preserves the historical checks for blank lines and non-documentation comments inside block function bodies.
The TSDoc plugin preserves the historical check for exported TypeScript declarations and public class methods.
The JavaScript override keeps the historical JSDoc parameter and return checks while allowing return descriptions.
The custom plugin API is alpha and must be checked against the target Oxlint version before updates.
Markdownlint support is a common shared capability installed at the target project root.
Merge the canonical `.markdownlint-cli2.jsonc` from this profile into an existing target configuration, or create it when none exists.
The profile ships two custom Markdownlint rules, `docs/no-box-drawing` and `docs/table-separators`, under `rules/`.
Copy that directory alongside the config so the `customRules` paths resolve.
The profile-owned `ignores` entry excludes `node_modules` even when a fresh target has no `.gitignore`.
Retain the target's existing ignore configuration when merging.
Do not copy the root-only custom rule paths from other repositories.
The profile's custom rules ship with the fragment under `rules/`.
Add `markdownlint-cli2` as a development dependency and `check:markdownlint-cli2` plus `fix:markdownlint-cli2` script entries through the manifest merge above.
The `check:markdownlint-cli2` task runs the plain `markdownlint-cli2` command.
The `fix:markdownlint-cli2` task runs it with `--fix`.
Both resolve configuration and globs from the config file.

## Commands

Run `bun install` after a dependency change.
Run `bun run check` for read-only lint and formatting checks owned by the target package.
The `check:ultracite` task runs `ultracite check`, which reports findings without writing source changes.
The `check:markdownlint-cli2` task lints Markdown files at the project root without writing changes.
Run `bun run fix` only when the user approves source changes.
The `fix:ultracite` task runs the Ultracite fixer.
The `fix:markdownlint-cli2` task applies Markdownlint fixes.
Run `bun test` only when the target owns Bun tests.
Omit the test command when the target has no native test owner rather than creating a dummy suite or relying on an empty-suite flag.
Run `bunx tsc --noEmit` only when the target has TypeScript sources, a compiler configuration, and the target provides TypeScript and Bun test type declarations when its test files are included.
Do not run a TypeScript compile check for a JavaScript-only target.

## CI

The GitHub catalog uses `oven-sh/setup-bun@v2` and reads the Bun version from the target's `packageManager`, `engines.bun`, or selected version.
It does not infer Bun from `package.json` alone.
The GitLab catalog uses the target's Bun container image or a user-selected image version.
It must be adjusted when the target requires a pinned image digest or a different registry.
Do not present `oven/bun:1` as a target version pin.
CI runs `bun install --frozen-lockfile` when `bun.lock` exists, then the target's `bun run check` script.
Replace the install mode only when the target uses another lockfile or an intentional dependency update job.
CI must run `bun test` only when the target has a Bun test owner.
Add that bounded step after confirming the target's test command.
Targets without tests should omit this step.
CI must run `bunx tsc --noEmit` only when TypeScript is selected and a target compiler configuration exists.
Add that bounded step after verifying the target's compiler dependencies.
The catalog does not infer this step from file names.
Existing CI jobs and package scripts take precedence over these catalog entries.

## Source Profile Version Evidence

The source profile was checked against the repository's available Bun `1.3.14`, Oxc `1.83.0`, Oxfmt `0.68.0`, Ultracite `7.12.0`, Markdownlint CLI2 `0.23.3`, and `@oxc-project/types` `0.150.0` packages.
The target owns final dependency versions through its manifest and lockfile.
The fragment's version ranges are source-profile evidence, not mandatory target pins.
Check npm before merging them.
