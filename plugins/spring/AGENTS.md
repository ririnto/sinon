# Repository Guidelines

## Project Structure

Spring skills and references live under `skills/`.
The `agents/` directory contains structural reviewers.
Open the affected skill before changing examples or version-specific guidance.

## Build, Test, and Development Commands

Verify release-sensitive claims against official Spring documentation and published artifact metadata.

## Coding Style and Testing

State the target release when a claim is pinned.
Otherwise use version-line wording.
Keep BOM-managed dependency examples versionless.
Use JUnit 6 as the default test line and keep JUnit 5 only for documented Boot 3.5 or Spring statemachine paths.
Keep Java method declarations, calls, and related assertions compact.

## Security and Configuration

Open the relevant skill reference for configuration, dependency, or runtime detail rather than placing a current version inventory here.

Keep release facts in the owning skill.
Record the exact source used for a changed compatibility claim.
