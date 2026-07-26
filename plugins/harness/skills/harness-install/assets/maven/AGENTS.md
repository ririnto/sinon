# Repository Guidelines

## Project Structure

`ARCHITECTURE.md` owns system boundaries, dependency direction, data flow, and verification boundaries.

## Build, Test, and Development Commands

Run `./mvnw verify` for the full validation lifecycle.
Run `./mvnw exec:exec@format-markdown spotless:apply` to fix Markdown and apply Java formatting.
Keep guarded Markdown validation and fixing visible when `markdownlint-cli2` is unavailable.

## Coding Style and Testing

Use structured logging and preserve the configured naming, import, brace, documentation, and line length rules.
Keep formatter changes limited to approved machine-applicable fixes.

## Security and Configuration

Do not add parallel policy files for Java release, lifecycle, formatting, Checkstyle, or Markdown configuration.
Review Maven plugin and wrapper execution for filesystem, network, credential, and publication effects before changing them.
