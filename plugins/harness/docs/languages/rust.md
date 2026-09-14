# Rust Language Rules

This document owns Rust-specific rules for the `rust` profile.
The [shared rules](../rules.md) apply alongside these Rust-only invariants.
The [Rust tool reference](../tools/rust.md) owns commands and configuration.

## Ownership And Borrowing

Prefer borrowing (`&T`, `&mut T`) over taking ownership when the function needs read or write access only.
Return owned values from constructors and parsers; borrow everywhere else.
Convert `clone()` calls into borrows unless ownership is genuinely required; let the clippy gate catch avoidable clones.
Use `Cow<'_, str>` only when a profiling need is proven, not speculatively.

## Option, Result, And Panics

Return `Result` for recoverable failures and `Option` for absent values; reserve `panic!`, `unwrap`, and `expect` for initialization and invariant violations with a written justification.
Convert errors with `?` and `thiserror`-style enums at module boundaries.
Never swallow an error into `let _ =` except where the linter gate permits documented no-op cases.
Use `must_use` attributes when ignoring a return value is a defect.

## Bindings And Mutability

Default to `let` bindings; add `mut` only where mutation happens.
Make structs and fields immutable by default; interior mutability (`Cell`, `RefCell`, `Mutex`) requires a stated concurrency or shared-state reason.
Prefer `const` and `static` items in uppercase for fixed values.

## Enum-Driven Design

Model domain states as enums with data-carrying variants instead of boolean flags.
Write exhaustive `match` expressions; avoid wildcard `_` arms when the compiler can check exhaustiveness.
Use `impl Trait` in argument and return position for internal APIs; reserve generics with trait bounds for public library surfaces.

## Documentation

Document every public item with `///` prose stating the contract, errors, and panics.
Use doc-comment examples for non-trivial APIs; `cargo test` runs them.
Keep module-level `//!` prose to one short paragraph.
Rustdoc syntax owns the format; do not import Javadoc conventions.

## Unsafe And Recursion

`unsafe` blocks require a `// Safety:` comment naming the invariant that makes them sound and appear only after a documented search for a safe alternative.
Recursion on untrusted input must be bounded; the compiler does not guarantee tail-call optimization, so convert deep recursion into iteration with an explicit stack.
