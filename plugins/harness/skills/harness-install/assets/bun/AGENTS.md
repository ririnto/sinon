# Repository Guidelines

## Project Structure

`ARCHITECTURE.md` owns system boundaries, dependency direction, data flow, and verification boundaries.
Treat `package.json`, `oxlint.config.ts`, and `oxfmt.config.ts` as the authority for scripts, lint, and format behavior.
The plugins subtree owns plugin, TSDoc, and parser implementation details.

## Build, Test, and Development Commands

Run `bun run check` for validation and `bun run fix` for approved fixes.
After validation passes, run `bun test --pass-with-no-tests` before handoff.
Husky is the sole Git hook mechanism, and `bun run prepare` installs hooks after dependencies are present.
Run `bun run prepare` only when hook activation is approved.
Hooks must invoke package scripts rather than define a second policy.

## Coding Style and Testing

Use Oxfmt for configured source formatting, and keep Markdown formatting under the configured Markdownlint command rather than Oxfmt.

## Security and Configuration

Review Husky hook installation and package scripts for filesystem, network, credential, and publication effects before changing them.
