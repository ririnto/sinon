# Repository Guidelines

## Project Structure

`ARCHITECTURE.md` owns system boundaries, dependency direction, data flow, and verification boundaries.
`Taskfile.yaml` and `.golangci.yaml` own the exact tool lists, configured thresholds, and gate behavior.

## Build, Test, and Development Commands

Task 3.52.0 is required to run the quality gate.
`task check` is the full Go validation gate through parallel dependencies: `fmt-check`, `lint`, `nilaway`, and `test-race`.
The dependencies run concurrently and have no serial ordering, so do not treat the list above as a sequence.
`task fix` applies formatting then safe lint fixes by running `golangci-lint fmt` followed by `golangci-lint run --fix`.
Individual tasks are `task fmt` (`golangci-lint fmt`), `task fmt-check` (`golangci-lint fmt --diff`), `task lint` (`golangci-lint run`), `task nilaway` (`nilaway -include-pkgs="$(go list -m)" ./...`), `task test` (`go test -shuffle=on -count=1 ./...`), and `task test-race` (`go test -race -shuffle=on -count=1 ./...`).

## Coding Style and Testing

Keep generated, third-party, builtin, and example exclusions aligned with `.golangci.yaml` rather than weakening the gate.

## Security and Configuration

Keep module downloads read-only through the configured `modules-download-mode: readonly` setting.
Review Taskfile commands and linter execution for filesystem, network, credential, and publication effects before changing them.
