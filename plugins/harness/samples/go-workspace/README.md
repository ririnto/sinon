# Go Workspace Sample

This sample contains an application module and a sibling greeting module.
The application declares its dependency and local replacement in its own `go.mod`, so it works with or without `go.work`.
The project's golangci-lint configuration permits that local replacement.
Copy this directory into a project to own its configuration.
Install Go 1.27, Node and pnpm as declared in `package.json`.
Install the pinned Go tools and project dependencies:

```sh
export GOBIN="$(pwd)/.bin"
go install github.com/golangci/golangci-lint/v2/cmd/golangci-lint@v2.15.0
GOTOOLCHAIN=auto go install go.uber.org/nilaway/cmd/nilaway@v0.0.0-20260918162853-acb8859b9031
go install golang.org/x/pkgsite/cmd/pkgsite@v0.6.0
export PATH="${GOBIN}:${PATH}"
pnpm install
```

Then run:

```sh
go run .
go test ./...
go test ./greeting/...
GOWORK=off go test ./...
(cd greeting && GOWORK=off go test ./...)
```

`pnpm run go:check` runs lint, formatting, NilAway, and race tests across both modules.
`pnpm run go:docs` serves local API documentation.
The project-owned hooks run those checks on commit and push, and both CI configurations use the same script.
