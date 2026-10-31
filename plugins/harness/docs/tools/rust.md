---
title: Rust Tool Reference
metadata:
  reference:
    Google Rust style guide:
      url: https://google.github.io/styleguide/rustguide.html
    Rustfmt configuration:
      url: https://raw.githubusercontent.com/rust-lang/rustfmt/master/Configurations.md
    Rustdoc:
      url:
        - https://doc.rust-lang.org/cargo/commands/cargo-doc.html
        - https://doc.rust-lang.org/rustdoc/lints.html
    Cargo validation:
      url:
        - https://doc.rust-lang.org/cargo/commands/cargo-test.html
        - https://doc.rust-lang.org/cargo/reference/features.html
    Cargo rust-version:
      url: https://doc.rust-lang.org/cargo/reference/rust-version.html
    Clippy:
      url:
        - https://rust-lang.github.io/rust-clippy/master/index.html#cognitive_complexity
        - https://rust-lang.github.io/rust-clippy/master/index.html#too_many_arguments
        - https://rust-lang.github.io/rust-clippy/master/index.html#type_complexity
    Rust MSRV support:
      url: https://docs.cloud.google.com/rust/docs/supported-rust-versions
    rustup:
      url: https://rust-lang.github.io/rustup/concepts/toolchains.html
---

This document owns the `rust` profile commands, dependencies, and target integration rules for Cargo workspace targets.
It accompanies the [Rust language rules](../languages/rust.md) and [shared rules](../rules.md).
Native configuration sources live under `tooling/rust/`.

## Scope And Detection

Select the `rust` profile for a root that has a `Cargo.toml`.
A root `Cargo.toml` with a `[workspace]` table is the normal target.
A single-package root also qualifies.
The package names, edition, and dependency list stay target-owned.
This profile never invents a package.
In a workspace with member subdirectories, install configuration at the workspace root so `cargo` workspace commands cover every member.

## Toolchain

- Rust: use the channel declared by the target's `rust-toolchain.toml` or legacy `rust-toolchain` file when present.
  Otherwise use the stable toolchain with `clippy` and `rustfmt` components.
- The supported rustup representations are a named channel such as `stable`, a major/minor channel such as `1.99`, or a full version such as `1.99.0`.
  Cargo has no compatible range syntax for this file.
- The required components are `clippy` and `rustfmt`.
  Add them to an existing toolchain with `rustup component add clippy rustfmt`.

Cargo's `package.rust-version` field declares the package's minimum supported compiler.
`rust-toolchain.toml` selects the compiler used by local commands.
For a new application root, use the current stable channel unless its target contract requires a pin.
For a new published library, use the oldest stable Rust release from the preceding twelve months unless its documented consumer contract requires an older compiler.
Test a published library with its declared minimum compiler.
When raising that minimum drops a previously supported compiler, release the change as a minor version update.

For a new toolchain selection, check the official rustup release channel for the current stable version compatible with the target before pinning it.
For new crate dependencies, check crates.io for the latest stable version compatible with the target's Rust version and Cargo constraints.
Keep existing toolchain and crate pins unless the task authorizes changing them.

## Native Configuration Sources

| Source file | Destination | Write rule |
| --- | --- | --- |
| `tooling/rust/rustfmt.toml` | `rustfmt.toml` at the workspace root | Create only when neither `rustfmt.toml` nor `.rustfmt.toml` exists. |
| `tooling/rust/clippy.toml` | `clippy.toml` at the workspace root | Create only when neither `clippy.toml` nor `.clippy.toml` exists. |
| `tooling/rust/rust-toolchain.toml` | `rust-toolchain.toml` at the workspace root | Create only when neither `rust-toolchain.toml` nor `rust-toolchain` exists. |
| `tooling/rust/.editorconfig` | `.editorconfig` at the workspace root | Create only when `.editorconfig` is missing. |

The fresh-root configurator applies these creation rules and reports existing native files as kept.
It preserves their bytes, including both filenames when a target already has both, and rejects symlinks or non-file destinations before any writes.
After configuration, use a bounded merge only for authorized missing formatter, linter, or EditorConfig settings.
Never overwrite an existing toolchain file or create a competing filename to shadow it.
The target's channel, profile, and component pins stay authoritative.
A target pinned to an older channel keeps its pin.
Do not bump it to satisfy this profile.
The profile thresholds in `clippy.toml` and layout options in `rustfmt.toml` are defaults.
When the target already sets a value, its value wins.
The cognitive complexity threshold is 25, and the validation commands explicitly enable `clippy::cognitive_complexity`.
This restriction lint is allowed by default, so setting its threshold alone does not activate it.
The profile also sets `too-many-arguments-threshold` to 5 and `type-complexity-threshold` to 250.
Clippy warns by default for `clippy::too_many_arguments`, and the workspace command's `-D warnings` denies that diagnostic.
Keep additional package-specific Clippy lint tables and Cargo-deny rules in target-owned configuration.

## Formatter Compatibility

The supplied formatter uses a 100-column width and stable settings.
Its small-item layout choices are profile preferences rather than additional Google requirements.
Use the target-selected formatter and preserve its Cargo edition and formatting edition.
Check the installed formatter's support before adopting additional settings.
Keep module-level import grouping as review guidance when `imports_granularity = "Module"` is unavailable on that formatter.
Treat `format_macro_matchers` and hexadecimal-literal formatting the same way rather than introducing a nightly requirement.
Do not add formatting skips, lint suppressions, or weaker gates to make configuration pass.

## Validation Commands

Use the [project baseline](baseline.md) for the root pnpm manifest, catalog, Markdownlint configuration and real Husky hooks.
The hooks in `tooling/rust/.husky/` run the shared check before Cargo formatting and strict Clippy on commit.
On push it also runs the workspace test gate.
Preserve the target's toolchain and native configuration through this profile's bounded merges.

Run inside the workspace root:

```sh
cargo fmt --all -- --check
cargo clippy --workspace --all-targets --all-features -- -D clippy::cognitive_complexity -D warnings
cargo test --workspace
RUSTDOCFLAGS="${RUSTDOCFLAGS:+$RUSTDOCFLAGS }-D warnings" cargo doc --workspace --no-deps
```

`cargo fmt --all` applies formatting.
Use the check form while materializing profile settings.
Apply formatting only for an authorized source change after confirming that the output preserves comments and literals.
`cargo clippy --fix --workspace --all-targets --all-features --allow-dirty --allow-staged -- -D clippy::cognitive_complexity -D warnings` applies safe machine-applicable fixes.
Never use `--broken-code`.
The `--all-targets --all-features -- -D clippy::cognitive_complexity -D warnings` lint strictness and the `--workspace` test scope are project choices, not universal defaults.
Match the target's own documented gate when it differs.
When feature combinations are mutually exclusive, use the target's supported feature matrix while retaining its lint strictness.
`cargo doc` generates workspace API HTML under `target/doc` and denies emitted warnings, including broken intra-doc links.
Fix warnings instead of suppressing them.
The `rust:doc` script runs this command independently of the existing Git gates.
The command appends warning denial to any existing `RUSTDOCFLAGS`.
`cargo test --workspace` includes documentation tests by default.
If a target uses `cargo test --workspace --all-targets`, run its separate documentation-test gate because `--all-targets` does not include doctests.

## CI Behavior

The GitHub catalog `ci/github/rust.yaml` and GitLab catalog `ci/gitlab/rust.gitlab-ci.yaml` run the same three commands in one job.
The GitLab job declares `stage: validate`.
Add that stage to the target's pipeline stages when it does not exist.
The GitHub job uses `dtolnay/rust-toolchain@stable` with the two components.
Use the target's pinned channel in place of `stable` when its toolchain file names one.
A working-directory adjustment is required when the workspace is not at the repository root.
Set the job's working directory instead of changing the commands.
Neither catalog configures Rust caching.
Adding a maintained cache action is a target decision, not part of this profile.

## Known Limitations

- `cargo test --workspace` builds every member.
  A workspace with broken or unrelated members fails in full.
  Scope the command to named members only when the target documents that gate.
- The `stable` action reference in the GitHub catalog tracks the floating stable channel and is intentionally unpinned.
  Targets needing reproducible CI should pin a channel through their toolchain file instead.
- The reference `rust-toolchain.toml` also uses floating `stable`, so it has no major or version ceiling.
- Refresh that selection with `rustup update stable` before the target's normal gate.
- For reproducibility, install a reviewed channel such as `1.99.0`.
  Use that value in the target's toolchain file and CI.
