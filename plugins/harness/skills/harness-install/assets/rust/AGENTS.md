# Repository Guidelines

## Project Structure

`ARCHITECTURE.md` owns system boundaries, dependency direction, data flow, and verification boundaries.

## Build, Test, and Development Commands

Run `cargo fmt --all -- --check` to verify formatting.
Run `cargo clippy --workspace --all-targets --all-features -- -D warnings` to verify lint cleanliness.
Run `cargo test --workspace --all-targets --all-features` to run the workspace test suite.
Use `cargo fmt --all` for formatting fixes.
Use `cargo clippy --fix --workspace --all-targets --all-features --allow-dirty --allow-staged -- -D warnings` for safe machine-applicable Clippy fixes, and do not use `--broken-code`.
Optionally run `markdownlint-cli2 "**/*.md" "#target"` for Markdown validation when it is available.

## Coding Style and Testing

Treat the workspace-wide target, feature, and warning-denial settings as project choices rather than universal defaults.

## Security and Configuration

Review Cargo execution and dependency changes for network, credential, generated-output, and vendored-source effects before changing them.
