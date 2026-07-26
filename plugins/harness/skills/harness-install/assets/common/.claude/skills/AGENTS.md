# Project Skill Guidelines

## Project Structure

### Ownership

Keep each skill limited to its named project task and host-neutral procedure.
Put the common path in `SKILL.md`, and move exceptions and blocker detail to references.

## Build, Test, and Development Commands

Do not invent a skill-local build, test, or command layer that duplicates the target repository policy.

## Coding Style and Testing

### Review

Keep frontmatter valid and machine-consumable for the host skill loader.
State authority, scope, stop conditions, and output contracts for autonomous procedures.

## Security and Configuration

Keep credentials, private configuration, caches, vendored content, and generated output outside the project skill tree.
Treat host loader frontmatter and skill references as machine-consumed configuration, and preserve their declared boundaries.
