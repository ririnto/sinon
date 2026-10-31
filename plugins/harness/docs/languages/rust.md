---
title: Rust Language Rules
metadata:
  reference:
    Google Rust style guide:
      url: https://google.github.io/styleguide/rustguide.html
    Rust:
      url:
        - https://doc.rust-lang.org/book/ch09-02-recoverable-errors-with-result.html
        - https://doc.rust-lang.org/std/clone/trait.Clone.html
---

This document owns Rust-specific rules for the `rust` profile.
Apply it to Rust source, examples, and API decisions affected by Cargo or toolchain changes.
The [shared rules](../rules.md) apply alongside these Rust-only invariants.
The [Rust tool reference](../tools/rust.md) owns commands and configuration.
Use the portable Google Rust style guidance within the target's documented contracts.
Keep Chromium build layout, import machinery, async and thread restrictions, and Fuchsia synchronization choices scoped to those platforms.

## Modules And Imports

Name a leaf module after its primary type or responsibility.
Keep non-leaf modules focused on declarations and re-exports, with only small amounts of necessary integration code.
Use explicit imports from one module per `use` statement.
Keep glob imports limited to established cases such as inline test modules, enum-focused code, and deliberate re-exports.
Qualify external free functions with their module and import type names directly unless names would collide.
Import a trait near the code that uses it when its methods are not needed throughout the module.

## Ownership And Borrowing

Prefer borrowing (`&T`, `&mut T`) over taking ownership when the function needs read or write access only.
Return owned values from constructors and parsers, and transfer ownership when the contract requires it.
Use `&str`, `&[T]`, and `Option<&T>` when the function needs borrowed data rather than a specific container.
Avoid deep clones that merely evade an ownership design problem.
Keep a clone when independent ownership or a substantially simpler lifetime contract justifies it.
Prefer unique ownership over `Rc` or `Arc` unless shared ownership makes the actual design clearer.
Use `Cow<'_, str>` only for a demonstrated allocation or ownership benefit.

## Option, Result, And Panics

Return `Result` for recoverable failures and `Option` for absent values.
Reserve `panic!`, `unwrap`, and `expect` for initialization and invariant violations with a written justification.
Prefer an explanatory `expect` message when an invariant justifies a panic.
Use pattern matching instead of checking a value and then unwrapping it.
Propagate recoverable errors with `?` and preserve meaningful error types and context at module boundaries.
Reuse the target's error representation before adding an error library.
Never swallow an error into `let _ =` except where the linter gate permits documented no-op cases.
Use `must_use` attributes when ignoring a return value is a defect.

## Bindings And Mutability

Default to `let` bindings.
Add `mut` only where mutation happens.
Make structs and fields immutable by default.
Interior mutability (`Cell`, `RefCell`, `Mutex`) requires a stated concurrency or shared-state reason.
Prefer `const` and `static` items in uppercase for fixed values.

## Types And Interfaces

Model distinct domain states as enums with data-carrying variants.
Use an enum when a boolean argument would hide its meaning at the call site.
Use newtypes where distinct meanings or invariants need type safety, and named fields where tuple positions would be unclear.
Write exhaustive `match` expressions.
Avoid wildcard `_` arms when the compiler can check exhaustiveness.
Keep explicit arms for known variants even when a non-exhaustive enum requires a fallback.
Choose non-exhaustive public types when downstream construction or matching must allow future variants or fields.

Define traits for an actual polymorphic contract or extension behavior, not merely to group methods.
Use inherent implementations otherwise.
Implement standard traits when they express the intended behavior and derive implementations when appropriate.
Choose generics, `impl Trait`, or dynamic dispatch from caller needs, runtime selection, and code-size constraints.
Use iterator inputs or outputs when a reusable API benefits from them.
Keep slices, owned collections, and direct loops when they make application code clearer.

## Documentation

Document meaningful effectively public contracts with `///` prose stating behavior, relevant parameters, errors, and panics.
Use links where they help callers navigate related API items.
Do not add boilerplate to trivial items merely to satisfy a missing-docs lint.
Account for enclosing-module visibility before treating an item as public API.
Use doc-comment examples for non-trivial APIs.
Verify them through the target's documentation-test gate.
Keep module-level `//!` prose to one short paragraph.
Rustdoc syntax owns the format.
Do not import Javadoc conventions.

## Unsafe And Recursion

Prefer a safe API and isolate necessary unsafe operations behind a sound interface.
Every `unsafe` block requires a `// SAFETY:` comment explaining why its invariants hold at that use.
Document caller obligations in a `# Safety` section on public unsafe functions and traits.
Recursion on untrusted input must be bounded.
The compiler does not guarantee tail-call optimization, so convert deep recursion into iteration with an explicit stack.
