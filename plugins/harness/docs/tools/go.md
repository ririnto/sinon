---
title: Go Tool Reference
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
      url: https://go.dev/doc/modules/managing-dependencies#tools
    Go package documentation server:
      version: v0.6.0
      url: https://pkg.go.dev/golang.org/x/pkgsite/cmd/pkgsite
---

This document owns the `go` profile commands, dependencies, and target integration rules for Go module targets.
It accompanies the [Go language rules](../languages/go.md) and [shared rules](../rules.md).
Native configuration sources live under `tooling/go/`.

## Scope And Detection

Select the `go` profile for a root that has a `go.mod` file.
The module path, Go version line, and dependency list stay target-owned.
This profile never creates or edits a `go.mod`.
In a multi-module repository, install and validate per module root.
The `go` toolchain in `PATH` provides build and test support.
Golangci-lint and NilAway are project-local tools installed into a build-local directory, never globally.

## Toolchain

- Go: use the version declared by the target's `go.mod` `go` directive.
  Install a matching toolchain when missing.
- golangci-lint: v2.15.0.
- NilAway: pinned pseudo-version `v0.0.0-20260918162853-acb8859b9031` from the upstream default branch.
- pkgsite: v0.6.0 for local API documentation, built with Go 1.26 or newer.
- Task: not required.
  This profile adds no Task dependency.

The Go module proxy and upstream release tags determine the latest stable golangci-lint version at installation time.
Check its compatibility with the target's Go directive before changing the profile pin.
NilAway has no stable release tag.
Review the current upstream commit and target Go compatibility before proposing a replacement for its reviewed pseudo-version.

## Native Configuration Sources

| Source file | Destination | Write rule |
| --- | --- | --- |
| `tooling/go/.golangci.yaml` | `.golangci.yaml` at the module root | create; merge `linters` and `formatters` entries when a config already exists |
| `tooling/go/.editorconfig` | merge into existing `.editorconfig` | merge only missing sections and keys |

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
On push it also runs the race-enabled test gate.
Install the native tools below and expose their project-local binaries on the hook's `PATH` before activation.

Run inside the module root:

```sh
golangci-lint run
golangci-lint fmt --diff
nilaway -include-pkgs="$(go list -m)" ./...
go test -race -shuffle=on -count=1 ./...
```

`golangci-lint fmt` applies formatting.
`golangci-lint run --fix` applies safe lint fixes.
Tool installation for local runs and CI uses a build-local `GOBIN`.
The commands below preserve profile baselines.
For a new installation, use the checked stable compatible golangci-lint version and the reviewed NilAway commit instead of assuming these pins remain current:

```sh
export GOBIN="$(pwd)/.bin"
go install github.com/golangci/golangci-lint/v2/cmd/golangci-lint@v2.15.0
GOTOOLCHAIN=auto go install go.uber.org/nilaway/cmd/nilaway@v0.0.0-20260918162853-acb8859b9031
export PATH="${GOBIN}:${PATH}"
```

`GOTOOLCHAIN=auto` applies only to the NilAway install.
Analysis and project commands use the target's declared Go toolchain.

## API Documentation

Install the official documentation server into the same project-local binary directory:

```sh
GOBIN="$(pwd)/.bin" go install golang.org/x/pkgsite/cmd/pkgsite@v0.6.0
```

Build this tool with Go 1.26 or newer, independently of the target's supported Go version.
Run `pnpm go:docs` from the module root with `.bin` on `PATH`, then open the local address printed by pkgsite.
The command generates and serves the module's documentation as HTML and does not publish it.
`-list=false` disables serving required dependency modules.
Use `go doc -all <package>` to print one package's documentation as text without pkgsite.
Run `golangci-lint run` to validate documentation comments and fix its findings.
The documentation server does not report every missing or unclear contract, so review the rendered API for useful descriptions and links.

## CI Behavior

The GitHub catalog `ci/github/go.yaml` and GitLab catalog `ci/gitlab/go.gitlab-ci.yaml` run the same four checks in one job.
Both pin golangci-lint and NilAway to the versions above and keep module downloads read-only through the config.
Before adopting the catalog's `golang:1.27` image or GitHub actions, check their official releases against the target's `go.mod` and CI policy.
Keep compatible target pins.
The GitLab job declares `stage: validate`.
Add that stage to the target's pipeline stages when it does not exist.
A working-directory adjustment is required when the module is not at the repository root.
Set the job's working directory to the module root instead of changing the commands.

## Known Limitations

- NilAway publishes no stable release tag.
  The pin is a dated pseudo-version of the default branch, so upstream changes can alter behavior between pin refreshes.
- NilAway through the golangci-lint module-plugin system (`.custom-gcl.yml`) is the upstream-recommended integration, but it builds a custom golangci-lint binary.
  This profile runs NilAway as a separate pinned binary to avoid that build step.
- The race detector requires cgo and a supported platform.
  On an unsupported runner, run `go test -shuffle=on -count=1 ./...` and report the dropped race coverage as a gap.
