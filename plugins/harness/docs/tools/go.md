---
metadata:
  reference:
    golangci-lint:
      tag: v2.15.0
      url:
        - https://github.com/golangci/golangci-lint/releases/tag/v2.15.0
        - https://raw.githubusercontent.com/golangci/golangci-lint/v2.15.0/.golangci.reference.yml
    NilAway:
      url: https://github.com/uber-go/nilaway
    Go tool dependencies:
      url:
        - https://go.dev/doc/modules/managing-dependencies#tools
        - https://go.dev/ref/mod#go-work-file
    Go package documentation server:
      version: v0.6.0
      url: https://pkg.go.dev/golang.org/x/pkgsite/cmd/pkgsite
---

# Go Tool Reference

This document owns the `go` profile commands, dependencies, and target integration rules for Go module and workspace targets.
It accompanies the [Go language rules](../languages/go.md) and [shared rules](../rules.md).
Native configuration sources live under `tooling/go/`.

## Scope And Detection

Select the `go` profile for a root that has a `go.mod` or `go.work` file.
The root `go.mod` owns Task, golangci-lint, NilAway, and pkgsite through Go tool directives.
The configurator generates the Taskfile and package scripts and leaves native module changes to the installer.

## Install The Tools

If the root has only a `go.work`, explicitly create a root module with a project-owned module path using `GOWORK=off go mod init <module-path>`.
Keep the root module out of the workspace's `use` list when it only owns tools.
The existing workspace modules still provide the packages checked by the Taskfile.
Go needs this root module to store tool directives.

Run the following from the root to add the profile's recommended tool versions:

```sh
pnpm run go:tools
```

The script runs `go get -tool` with workspace mode disabled and writes the root `go.mod` and `go.sum`.
If the project already selects a compatible version of a tool, use that version in the `go get -tool` arguments.
Review the module diff before staging it.
Keep the module path, compatible Go version, requirements, and replacements, including replacements needed with `GOWORK=off`.
Go can raise the minimum Go version and change shared dependency versions to resolve the tools.
If the root is a workspace member and its minimum Go version increases past the workspace version, review the corresponding `go.work` version update while preserving its module list and replacements.
Commit the reviewed module and checksum files together.

For an existing Harness installation, merge the updated Taskfile, package scripts, and CI commands.
Verify them against the root module before removing the old generated tool module.

## Toolchain

- Go: use a toolchain compatible with the target's `go` directive and the selected tools.
  Task v3.54.0 requires at least Go 1.26.4.
- golangci-lint: v2.15.0.
- NilAway: pinned pseudo-version `v0.0.0-20260918162853-acb8859b9031` from the upstream default branch.
- pkgsite: v0.6.0 for local API documentation, built with Go 1.26 or newer.
- Task: v3.54.0.

The Go module proxy and upstream release tags determine the latest stable golangci-lint version at installation time.
Check its compatibility with the target's Go directive before changing the profile pin.
NilAway has no stable release tag.
Review the current upstream commit and target Go compatibility before proposing a replacement for its reviewed pseudo-version.

## Native Configuration Sources

| Source file | Destination | Write rule |
| --- | --- | --- |
| `tooling/go/.golangci.yaml` | `.golangci.yaml` at the module root | create; merge `linters` and `formatters` entries when a config already exists |
| `tooling/go/.editorconfig` | merge into existing `.editorconfig` | merge only missing sections and keys |
| `tooling/go/Taskfile.yaml` | `Taskfile.yaml` | create |

The configuration is a real curated v2 config, not a starter.
When the target already runs golangci-lint, keep its existing `version`, linter set, and thresholds, and merge only missing Harness invariants.
Do not delete target-specific exclusions to match this file.

The source profile enables `errcheck` for ignored errors, including blank assignments and unchecked type assertions.
`disable-default-exclusions: true` removes errcheck's built-in function exemptions.
The empty exclusion presets also prevent golangci-lint's standard error-handling exclusions from hiding these diagnostics.
Keep target-owned exclusions when merging.

`godoclint` starts from `default: none` and checks package comments, declaration comment openings, and unused documentation links.
Read the [Go documentation requirements](../languages/go.md#documentation) when configuring these checks.
It does not require a separate comment for every trivial declaration.
`containedctx` enforces the [Go context placement rule](../languages/go.md#context-and-concurrency).
These checks enforce Go-specific contracts rather than multiline comment delimiters.

## Validation Commands

Use the [project baseline](baseline.md) for the root pnpm manifest, Markdownlint configuration, catalog, and real Husky hooks.
The hooks in `tooling/go/.husky/` run the shared check before native lint, formatting, and NilAway on commit.
Both hooks run the race-enabled test gate after Markdown validation and the Go checks.
The hooks use the tools declared in the root `go.mod`.

Run at the root that owns the tools:

```sh
pnpm check:go:lint
pnpm check:go:format
pnpm check:go:nilaway
pnpm check:test:go
```

The scripts capture the active `GOWORK` before launching Task with workspace mode disabled.
The Taskfile resolves its tools from the root module and restores the captured workspace for project checks.
It enumerates the active main modules and runs each check from its module root.
NilAway receives each module's own path while analysis retains workspace dependency selection.
Place a golangci-lint configuration where each module can discover it, including modules outside the workspace root.
Run `GOWORK=off go test ./...` from each application module to detect dependencies supplied only by the workspace.

The `check` command runs all four tasks alongside Markdown checks.
Update tool versions in the root module after checking compatibility with the target's Go directive.

## API Documentation

Run `pnpm go:docs` from the root that owns the tools, then open the local address printed by pkgsite.
The command generates and serves the module's documentation as HTML and does not publish it.
`-list=false` disables serving required dependency modules.
Use `go doc -all <package>` to print one package's documentation as text without pkgsite.
Run `pnpm check:go:lint` to validate documentation comments and fix its findings.
The documentation server does not report every missing or unclear contract, so review the rendered API for useful descriptions and links.

## CI Behavior

The GitHub catalog `ci/github/go.yaml` and GitLab catalog `ci/gitlab/go.gitlab-ci.yaml` run the same four checks in one job.
Both use the versions declared in the root module and keep application module downloads read-only through the config.
Before adopting the catalog's `golang:1.27` image or GitHub actions, check their official releases against the target's `go.mod` and CI policy.
Keep compatible target pins.
The GitLab job declares `stage: validate`.
Add that stage to the target's pipeline stages when it does not exist.
A working-directory adjustment is required when the workspace or single module is not at the repository root.
Set the job's working directory to that root instead of changing the commands.

## Known Limitations

- NilAway publishes no stable release tag.
  The pin is a dated pseudo-version of the default branch, so upstream changes can alter behavior between pin refreshes.
- NilAway through the golangci-lint module-plugin system (`.custom-gcl.yml`) is the upstream-recommended integration, but it builds a custom golangci-lint binary.
  This profile runs NilAway as a separate pinned binary to avoid that build step.
- The race detector requires cgo and a supported platform.
  On an unsupported runner, run `go test -shuffle=on -count=1 ./...` and report the dropped race coverage as a gap.
