# Repository Guidelines

This file applies to `plugins/spring/` and overrides broader plugin guidance.

## Project Structure

Spring skills and references live under `skills/`; `agents/` contains structural reviewers. Open the affected skill before changing examples or version-specific guidance.

## Build, Test, and Development Commands

Run `claude plugin validate plugins/spring` and `bun run check` after Spring package changes. Verify release-sensitive claims against official Spring documentation and published artifact metadata.

## Coding Style and Testing

State the target release when a claim is pinned; otherwise use version-line wording. Keep BOM-managed dependency examples versionless. Use JUnit 5 unless a documented Spring 7 or Boot 4 path requires JUnit 6. Keep Java method declarations, calls, and related assertions compact.

## Commit and Publication

Keep review fixes within their owning skill scope and arrange follow-up review for substantive corrections. The root session publishes.

## Security and Configuration

Open the relevant skill reference for configuration, dependency, or runtime detail rather than placing a current version inventory here.

## Scope and Precedence

This file governs all Spring plugin content. A skill or reference may add narrower documented constraints. Keep release facts in the owning skill and rerun the package checks when a source-backed claim changes.
Record the exact source used for a changed compatibility claim.
