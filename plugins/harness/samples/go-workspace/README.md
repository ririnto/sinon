# Go Workspace Sample

This sample contains an application module and a sibling greeting module.
The application declares its dependency and local replacement in its own `go.mod`, so it works with or without `go.work`.
The project's golangci-lint configuration permits that local replacement.
Copy this directory into a project to own its configuration.
Install Go 1.27.3, Node and pnpm as declared in `package.json`.
Install project dependencies with `pnpm install`.
The separate `tools/go.mod` pins the Go tools used by the Taskfile.

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
The project-owned hooks run those checks on commit and push, and both CI configurations use the same Taskfile.
