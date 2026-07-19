# Repository Guidelines

This file applies to `plugins/spring/` and overrides broader plugin guidance.

## Project Structure

Spring skills and references live under `skills/`; `agents/` contains structural reviewers. Open the affected skill before changing examples or version-specific guidance.

## Build, Test, and Development Commands

Verify release-sensitive claims against official Spring documentation and published artifact metadata.

## Coding Style and Testing

State the target release when a claim is pinned; otherwise use version-line wording. Keep BOM-managed dependency examples versionless. Use JUnit 5 unless a documented Spring 7 or Boot 4 path requires JUnit 6. Keep Java method declarations, calls, and related assertions compact.

## Security and Configuration

Open the relevant skill reference for configuration, dependency, or runtime detail rather than placing a current version inventory here.

## Scope and Precedence

Keep release facts in the owning skill.
Record the exact source used for a changed compatibility claim.
