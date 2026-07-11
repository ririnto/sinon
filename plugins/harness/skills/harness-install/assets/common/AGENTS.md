# Repository Guidelines

This file defines the installed repository safety and precedence contract.
A closer `AGENTS.md` overrides it for its subtree.
`CLAUDE.md` points here exactly.
`WORKFLOW.md` is the sole operational policy; open it before planning, dispatch, review, validation, or completion.

## Project Structure

Use `.claude/agents/` and `.claude/skills/` only for their named task.
Keep durable documents under `docs/` and use `docs/templates/` for new project artifacts.
Read a nearer `AGENTS.md` before changing its subtree because it replaces this contract.
Do not edit credentials, local environment files, caches, or vendored code unless the user names them.

## Build, Test, and Development Commands

Use the project’s existing setup and development commands rather than inventing replacements.
Report changed files, validation evidence, skipped checks, and risks.
Name each unavailable command and explain why it was not run.

## Coding Style and Testing

Keep changes scoped to the request and local patterns.
Preserve other work.
Do not broaden a requested change into unrelated cleanup.
Use English Markdown headings, language-tagged fences, and ASCII tree markers.
Update related documentation, templates, agents, skills, validation helpers, and generated metadata when behavior changes.

## Commit and Publication

`WORKFLOW.md` owns the lifecycle process.
This contract adds no lifecycle rules.

## Security and Configuration

Review hooks, scripts, MCP, settings, and generated assets for command, filesystem, network, credential, and publication risks.
For changes that can trigger external behavior, identify the actor, boundary, and affected target.
