# Go Language Rules

This document owns Go-specific rules for the `go` profile.
The [shared rules](../rules.md) apply alongside these Go-only invariants.
The [Go tool reference](../tools/go.md) owns commands and configuration.

## Errors

Wrap errors with `%w` so callers can inspect the chain.
Return the error rather than logging it; log at the boundary where handling ends.
Name error variables `err` and check them immediately after the call.
Do not discard errors with `_`; the linter gate enforces this.
Sentinel errors stay package-level and named `ErrXxx`; error types stay named `XxxError`.

## Context And Concurrency

Pass `context.Context` as the first parameter of request-scoped functions.
Do not store contexts in structs; pass them explicitly.
Every goroutine launched inside a function must have a documented shutdown path: a context cancellation, a channel close, or a `WaitGroup` the caller joins.
Never share memory between goroutines without synchronization; the race detector gate enforces this.

## Interfaces And Values

Define interfaces where they are consumed, not where they are implemented.
Accept interfaces, return concrete types.
Take pointer receivers only when mutation or shared identity is needed; keep value receivers as the default for small structs.
Use `any` over `interface{}` in new code.

## Strings, Maps, And Slices

Use `strings.Builder` for repeated concatenation in loops.
Copy or pre-size maps and slices in hot paths; `make(T, 0, n)` states the intent.
Never rely on map iteration order; sort keys when order is observable.
Use `slices` and `maps` packages over hand-written loops for standard operations.

## Documentation

Every exported identifier carries a doc comment starting with its name.
Package comments state the package's single responsibility.
Comments stay grammatical English prose with no restatement of the signature.
Go doc syntax owns the format; do not force Kotlin KDoc or Javadoc tags onto it.

## Testing

Table-driven tests with subtests are the default shape for pure logic.
Name test cases by the behavior they protect.
Use `t.Parallel()` for independent cases.
Test observable output, not implementation wording.
