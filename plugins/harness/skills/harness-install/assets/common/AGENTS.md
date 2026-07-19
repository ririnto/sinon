# Repository Guidelines

This file defines the installed repository safety and precedence contract for any agent runtime.
A closer `AGENTS.md` overrides it for its subtree.
`WORKFLOW.md` defines target lifecycle policy, orchestration, worker selection, and runtime fallbacks.
You MUST open it when that lifecycle or another target-local workflow rule governs the current task.

## Project Structure

Use installed skills only for their named tasks.
Keep durable documents under `docs/` and use `docs/templates/` for new project artifacts.
Do not edit credentials, local environment files, caches, or vendored code unless the user names them.

## Build, Test, and Development Commands

Use the project’s setup and development commands instead of inventing replacements.
Report changed files, validation evidence, skipped checks, and risks.
Name each unavailable command and explain why it was not run.

## Coding Style and Testing

Use English Markdown headings, language-tagged fences, and ASCII tree markers.
Test executable behavior at the smallest useful scope.
Use unit tests by default.
Use integration tests only when behavior requires a real process, database, network, filesystem boundary, container, or framework runtime.
Use end-to-end tests only for distinct core user journeys that lower-level tests do not already prove.
Review prose guidance directly instead of testing its wording or file layout.

## Security and Configuration

Review hooks, scripts, MCP, settings, and generated assets for command, filesystem, network, credential, and publication risks.
For changes that can trigger external behavior, identify the actor, boundary, and affected target.
