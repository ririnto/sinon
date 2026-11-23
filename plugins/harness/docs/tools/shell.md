---
metadata:
  reference:
    Go tool dependencies:
      url: https://go.dev/doc/modules/managing-dependencies#tools
    shfmt:
      version: 3.14.1
      url: https://raw.githubusercontent.com/mvdan/sh/v3.14.1/cmd/shfmt/shfmt.1.scd
---

# Shell Tool Reference

This document owns the `shell` profile commands and target integration rules for repositories with shell scripts.
It accompanies the [shell language rules](../languages/shell.md) and [shared rules](../rules.md).
Native configuration and manifest sources live under `tooling/shell/`.

## Scope And Detection

Select the `shell` profile for a repository root that tracks `*.sh` files.
The native file list comes from Git itself.
The profile's Husky hooks compose the commands below after the shared Markdown checks.
The profile introduces no shell test harness.

## Toolchain

The profile registers shfmt v3.14.1 as a Go tool in the target's root `go.mod`.
Use a Go toolchain compatible with the root module and shfmt, which requires Go 1.26 or later.

## Install The Formatter

If the target has no root `go.mod`, run `GOWORK=off go mod init <module-path>` from the repository root with a project-owned module path.
For a Go project, reuse its root module and keep its existing module path, requirements, and replacements.
Run the setup command from the repository root after the baseline supplies the package scripts:

```sh
pnpm run shell:tools
```

The command adds shfmt to the root module with `go get -tool`.
If the module already selects a compatible shfmt version, use that version in the `go get -tool` arguments.
Review the changes and commit `go.mod` and `go.sum` together.
For an existing profile installation, update the shell package scripts and the selected CI job together.

## Native Configuration Sources

| Source file | Destination | Write rule |
| --- | --- | --- |
| `tooling/shell/.editorconfig` | `.editorconfig` | create when absent, otherwise merge only missing sections and keys during native integration |

The fresh-root configurator creates this file when it is absent and keeps an existing file verbatim.
The shell section selects two-space indentation through EditorConfig.
Without parser or printer flags, shfmt reads these settings instead of using its default tab indentation.
When the target already owns an `.editorconfig`, keep its shell section and resolve conflicts in favor of the target.

## Validation Commands

Use the [project baseline](baseline.md) for the root pnpm manifest, catalog, Markdownlint configuration and real Husky hooks.
The hooks in `tooling/shell/.husky/` run the shared Markdown check before the shell format check on both commit and push.

Run at the repository root, over git-tracked scripts only:

```sh
pnpm run check:shell:format
```

The check resolves shfmt from the root module with Go workspace mode disabled.
Run `GOWORK=off go tool mvdan.cc/sh/v3/cmd/shfmt -w -- <path>` to apply approved formatting fixes in place.
Read the [shell path-handling rules](../languages/shell.md#files-and-paths) when adapting these pipelines.

## Dialect Selection

shfmt infers the dialect from the shebang line.
A `#!/usr/bin/env sh` shebang checks as POSIX `sh`.
A `bash` shebang checks as Bash and permits Bash-only syntax.
When a script's shebang and its syntax disagree, fix the shebang or the syntax at the root cause instead of adding per-file exemptions.

## CI Behavior

The GitHub catalog `ci/github/shell.yaml` selects Go from the root `go.mod`.
The GitLab catalog `ci/gitlab/shell.gitlab-ci.yaml` uses the `golang:1.27` image.
Both jobs use the root module's shfmt version and check the same Git-tracked `*.sh` files as the package script.
The GitLab job declares `stage: validate`.
Add that stage to the target's pipeline stages when it does not exist.
