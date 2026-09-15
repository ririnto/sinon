# Repository Guidelines

## Project Structure

`skills/change-description/` owns Git-contained change-description guidance.
Agents draft or route work.
They do not publish.

## Change Description

Inspect the requested diff and relevant Git state before drafting change context.
Check branch and comparison information when the handoff needs it, not as a fixed command sequence.
Use repository-relative paths, portable examples, and evidence from the local Git history.
Keep rationale, validation, review focus, and merge handoff self-contained.

## Commit and Integration

Treat force-push and history rewriting as explicit user decisions.
Use the repository's selected Git merge strategy and preserve the required review evidence.

## Security and Configuration

Do not expose credentials or private local environment details.
Keep external work-item identifiers and review URLs out of committed text.
