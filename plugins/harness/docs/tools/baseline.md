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

This document owns the standing fresh-project baseline, not a repository campaign.
The installer verifies eligibility and selects a project type before configuring it.
Never modify an archived repository.
For an existing repository, first verify meaningful source, build, or maintained behavior changes in the last three months.
Push timestamps and dependency-bot churn alone do not establish meaningful activity.
Skip inactive repositories and simple configuration-only repositories that need no Harness integration.
Current authorized project creation counts as activity for a new repository.
Keep the eligibility decision in task context.

## Fresh Roots

Use `scripts/configure.ts` from the current plugin root with supported Node LTS:

```sh
node <plugin-root>/scripts/configure.ts --target <target-root> --profile <profile> --eligibility eligible --non-interactive
```

Replace the placeholders with the verified paths and selected profile.
Use `--help` for the supported choices.
The command never prompts and reports only repository-relative output paths.
The command requires the eligibility decision explicitly and accepts `archived`, `inactive`, and `configuration-only` as no-write outcomes.
It materializes shared sources after checking every destination for conflicts and unsafe symlinks.
Identical files remain unchanged on a repeat run.
A conflicting baseline file stops the operation before writes.
An existing native Gradle version catalog remains target-owned.
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
Node, frontend, and React roots receive the canonical base Oxc configuration, three independent style configurations, Oxfmt configuration, and flat plugin modules.
The runtime fragment pins the supported Node 24 LTS line and pnpm baseline.
Verify current compatible releases before installing dependencies, preserving compatible target pins during bounded merges.

## Integration Proof

Complete the selected native profile's build integration before activating its hooks.
Gradle requires real `ktlintCheck` and `check` tasks and its checked-in wrapper.
Maven requires its wrapper and the selected Spotless and Checkstyle goals.
Go requires the target toolchain and project-local golangci-lint and NilAway binaries on the hook's `PATH`.
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
Repeat configuration and confirm no new files or changed bytes.
Verify each independently deliverable unit before integrating its successor.
Check the actual target artifacts and native outcomes rather than inferring success from copied files or script names.
A wrapper stub proves dispatch and failure propagation, not native compilation.
