# Rust Tool Reference

This document owns the `rust` profile commands, dependencies, and target integration rules for Cargo workspace targets.
It accompanies the [Rust language rules](../languages/rust.md) and [shared rules](../rules.md).
Native configuration sources live under `tooling/rust/`.

## Scope And Detection

Select the `rust` profile for a root that has a `Cargo.toml`.
A root `Cargo.toml` with a `[workspace]` table is the normal target; a single-package root also qualifies.
The package names, edition, and dependency list stay target-owned; this profile never invents a package.
In a workspace with member subdirectories, install configuration at the workspace root so `cargo` workspace commands cover every member.

## Toolchain

- Rust: use the channel declared by the target's `rust-toolchain.toml` when present; otherwise the stable toolchain with `clippy` and `rustfmt` components.
- The supported rustup representations are a named channel such as `stable`, a major/minor channel such as `1.98`, or a full version such as `1.98.1`; Cargo has no compatible range syntax for this file.
- The required components are `clippy` and `rustfmt`; add them to an existing toolchain with `rustup component add clippy rustfmt`.

## Native Configuration Sources

| Source file | Destination | Write rule |
| --- | --- | --- |
| `tooling/rust/rustfmt.toml` | `rustfmt.toml` at the workspace root | create; merge keys when a config already exists |
| `tooling/rust/clippy.toml` | `clippy.toml` at the workspace root | create; merge keys when a config already exists |
| `tooling/rust/rust-toolchain.toml` | `rust-toolchain.toml` at the workspace root | create only when the target has no toolchain file |
| `tooling/rust/.editorconfig` | merge into existing `.editorconfig` | merge only missing sections and keys |

Never overwrite an existing `rust-toolchain.toml`.
The target's channel, profile, and component pins stay authoritative.
A target pinned to an older channel keeps its pin; do not bump it to satisfy this profile.
The thresholds in `clippy.toml` and the layout options in `rustfmt.toml` are project choices; when the target already sets a value, its value wins.

## Validation Commands

Run inside the workspace root:

```sh
cargo fmt --all -- --check
cargo clippy --workspace --all-targets --all-features -- -D warnings
cargo test --workspace
```

`cargo fmt --all` applies formatting.
`cargo clippy --fix --workspace --all-targets --all-features --allow-dirty --allow-staged -- -D warnings` applies safe machine-applicable fixes; never use `--broken-code`.
The `--all-targets --all-features -- -D warnings` lint strictness and the `--workspace` test scope are project choices, not universal defaults; match the target's own documented gate when it differs.

## CI Behavior

The GitHub catalog `ci/github/rust.yaml` and GitLab catalog `ci/gitlab/rust.gitlab-ci.yaml` run the same three commands in one job.
The GitLab job declares `stage: validate`; add that stage to the target's pipeline stages when it does not exist.
The GitHub job uses `dtolnay/rust-toolchain@stable` with the two components; use the target's pinned channel in place of `stable` when its toolchain file names one.
A working-directory adjustment is required when the workspace is not at the repository root; set the job's working directory instead of changing the commands.
Neither catalog configures Rust caching; adding a maintained cache action is a target decision, not part of this profile.

## Known Limitations

- `cargo test --workspace` builds every member; a workspace with broken or unrelated members fails in full. Scope the command to named members only when the target documents that gate.
- The `stable` action reference in the GitHub catalog tracks the floating stable channel and is intentionally unpinned; targets needing reproducible CI should pin a channel through their toolchain file instead.
- The reference `rust-toolchain.toml` also uses floating `stable`, so it has no major or version ceiling.
- Refresh that selection with `rustup update stable` before the target's normal gate.
- For reproducibility, install a reviewed channel such as `1.98.1`, set the same value in the target's toolchain file, and use it in CI.

## Sources

The supported rustup channel forms and toolchain-file behavior follow the official [rustup toolchain documentation](https://rust-lang.github.io/rustup/concepts/toolchains.html), read 2026-09-14.
