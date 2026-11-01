---
title: Project Tooling Baseline
metadata:
  reference:
    pnpm catalogs:
      url: https://pnpm.io/catalogs
    Husky:
      version: 9.1.7
      url: https://raw.githubusercontent.com/typicode/husky/v9.1.7/docs/get-started.md
---

Inspect the target and select a project type before configuring it.
Preserve target-owned files and obtain authority for changes before writing.

## Fresh Roots

Use `scripts/configure.ts` from the current plugin root with supported Node LTS:

```sh
node <plugin-root>/scripts/configure.ts --target <target-root> --profile <profile> --eligibility eligible --non-interactive
```

Replace the placeholders with the verified paths and selected profile.
Use `--help` for the supported choices.
The command never prompts and reports only repository-relative output paths.
The command requires `--eligibility` with one of `eligible`, `archived`, `inactive`, or `configuration-only`.
The `eligible` value permits configuration, while the other values return a skipped result without writes.
It materializes shared sources after checking every destination for conflicts and unsafe symlinks.
Identical files remain unchanged on a repeat run.
A conflicting baseline file stops the operation before writes.
Repeat configuration and confirm no new files or changed bytes before modifying generated files.
For TypeScript, add `--typescript-profile` with `node`, `vite-react`, `vue`, `react-native`, or `next` to materialize the corresponding target-owned compiler configuration and catalog dependency.
Select it only for TypeScript projects.
An existing native Gradle version catalog remains target-owned.
Rust formatter, Clippy, toolchain and EditorConfig files also remain target-owned, including hidden formatter/linter names and the legacy toolchain filename.
For an existing configured root or a polyglot root, use the profile's bounded merges rather than overwriting its manifest or applying competing installers.

| Profile | Native selection | Canonical manifest source |
| --- | --- | --- |
| `gradle` | Java Gradle | `tooling/gradle/package.json` |
| `kotlin` | Kotlin Gradle | `tooling/gradle/package.json` |
| `maven` | Java Maven | `tooling/maven/package.json` |
| `go` | Go module | `tooling/go/package.json` |
| `rust` | Cargo workspace or package | `tooling/rust/package.json` |
| `uv` | uv-managed Python | `tooling/uv/package.json` |
| `shell` | Tracked shell scripts | `tooling/shell/package.json` |
| `node` | JavaScript or TypeScript | `tooling/pnpm/package.fragment.json` |
| `frontend` | HTML with Vite Storybook | pnpm plus `tooling/frontend/package.fragment.json` |
| `react` | React with Vite Storybook | pnpm plus `tooling/react/package.fragment.json` |

Every selected root receives a private pnpm manifest, workspace catalog, compact-table Markdownlint CLI2 configuration with sorted object keys, and custom Markdown rules.
Its canonical manifest selects the adjacent profile-owned Husky hook sources.
The pnpm script entrypoints and Git invoke those same configurations.
Gradle and Kotlin roots create `gradle/libs.versions.toml` only when missing.
Use that catalog as the root's single native version source when integrating its build.
Keep each independent Gradle root's catalog separate.
Rust roots create missing native configuration according to the [Rust profile's filename and preservation rules](rust.md#native-configuration-sources).
The configurator leaves the Cargo manifest, crate identity, edition and minimum compiler unchanged.
Node, frontend, and React roots receive the canonical base Oxc configuration, three independent style configurations, Oxfmt configuration, and flat plugin modules.
Node API roots also receive TypeDoc configuration and a compatible TypeScript peer in the pnpm catalog.
uv roots receive Sphinx configuration under `docs/` for the target's public package.
Complete each selected profile's native documentation integration for its actual public entry points before running generation.
The runtime fragment supplies the source profile's Node.js engine range.
Verify current compatible releases before installing dependencies, preserving compatible target pins during bounded merges.
Set the selected pnpm version in the target root's `packageManager` field before installing dependencies or using the CI catalog.

## Integration Proof

Complete the selected native profile's build integration before activating its hooks.
Gradle requires real `ktlintCheck` and `check` tasks and its checked-in wrapper.
Maven requires its wrapper and the selected Spotless and Checkstyle goals.
Go requires a toolchain compatible with the target and its separate `tools/go.mod`.
Rust requires the target-owned Cargo project and its selected toolchain with Clippy and rustfmt.
uv requires the target-owned Python project and its synced Ruff dependency.
Shell requires ShellCheck and shfmt on the hook's `PATH` and checks Git-tracked scripts only.
Java configuration assets use the selected Gradle or Maven manifest, and the Kotlin ruleset module uses its selected native build owner.
These support directories do not introduce competing root manifests.
The frontend selections require their matching Storybook renderer and real component stories.
Other frontend frameworks use [frontend guidance](frontend.md) and a compatible target-owned renderer through a bounded merge.

Run `pnpm install` and `pnpm run prepare` after reviewing the generated files.
Verify `core.hooksPath` and actual Git commit and push operations through the shared [Husky integration](pnpm.md#git-hook-integration).
Run the root `pnpm run check` and the selected native gate.
Verify both pnpm hook entrypoints and prove a disposable violation blocks the matching Git operation.
Verify each independently deliverable unit before integrating its successor.
Check the actual target artifacts and native outcomes rather than inferring success from copied files or script names.
A wrapper stub proves dispatch and failure propagation, not native compilation.
