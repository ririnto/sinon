# Go Tool Reference

This document owns the `go` profile commands, dependencies, and target integration rules for Go module targets.
It accompanies the [Go language rules](../languages/go.md) and [shared rules](../rules.md).
Native configuration sources live under `tooling/go/`.

## Scope And Detection

Select the `go` profile for a root that has a `go.mod` file.
The module path, Go version line, and dependency list stay target-owned; this profile never creates or edits a `go.mod`.
In a multi-module repository, install and validate per module root.
The `go` toolchain in `PATH` provides build and test support; golangci-lint and NilAway are project-local tools installed into a build-local directory, never globally.

## Toolchain

- Go: use the version declared by the target's `go.mod` `go` directive; install a matching toolchain when missing.
- golangci-lint: v2.13.2 (pinned at authoring time from upstream releases).
- NilAway: pinned pseudo-version `v0.0.0-20260910170248-571480214735` from the upstream default branch.
- Task: not required. Direct native commands below provide the same checks the historical Taskfile bundled, so this profile adds no Task dependency.

## Native Configuration Sources

| Source file | Destination | Write rule |
| --- | --- | --- |
| `tooling/go/.golangci.yaml` | `.golangci.yaml` at the module root | create; merge `linters` and `formatters` entries when a config already exists |
| `tooling/go/.editorconfig` | merge into existing `.editorconfig` | merge only missing sections and keys |

The configuration is a real curated v2 config, not a starter.
When the target already runs golangci-lint, keep its existing `version`, linter set, and thresholds, and merge only missing Harness invariants.
Do not delete target-specific exclusions to match this file.

## Validation Commands

Run inside the module root:

```sh
golangci-lint run
golangci-lint fmt --diff
nilaway -include-pkgs="$(go list -m)" ./...
go test -race -shuffle=on -count=1 ./...
```

`golangci-lint fmt` applies formatting; `golangci-lint run --fix` applies safe lint fixes.
Tool installation for local runs and CI uses a build-local `GOBIN`, for example:

```sh
export GOBIN="$(pwd)/.bin"
go install github.com/golangci/golangci-lint/v2/cmd/golangci-lint@v2.13.2
go install go.uber.org/nilaway/cmd/nilaway@v0.0.0-20260910170248-571480214735
export PATH="${GOBIN}:${PATH}"
```

## CI Behavior

The GitHub catalog `ci/github/go.yaml` and GitLab catalog `ci/gitlab/go.gitlab-ci.yaml` run the same four checks in one job.
Both pin golangci-lint and NilAway to the versions above and keep module downloads read-only through the config.
The GitLab job declares `stage: validate`; add that stage to the target's pipeline stages when it does not exist.
A working-directory adjustment is required when the module is not at the repository root; set the job's working directory to the module root instead of changing the commands.

## Known Limitations

- NilAway publishes no stable release tag; the pin is a dated pseudo-version of the default branch, so upstream changes can alter behavior between pin refreshes.
- NilAway through the golangci-lint module-plugin system (`.custom-gcl.yml`) is the upstream-recommended integration, but it builds a custom golangci-lint binary; this profile runs NilAway as a separate pinned binary to avoid that build step.
- The race detector requires cgo and a supported platform; on an unsupported runner, run `go test -shuffle=on -count=1 ./...` and report the dropped race coverage as a gap.
