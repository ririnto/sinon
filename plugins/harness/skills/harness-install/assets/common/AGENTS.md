# Repository Guidelines

This file defines the installed repository safety and precedence contract for any agent runtime.
A closer `AGENTS.md` overrides it for its subtree.
`WORKFLOW.md` defines target lifecycle policy, orchestration, model-tier routing, and runtime fallbacks.
You MUST open it when that lifecycle or another target-local workflow rule governs the current task.

## Project Structure

Use installed skills only for their named tasks.
Skills live under `.claude/skills/` and are mirrored at `.agents/skills/`; a host without a skill loader reads each `SKILL.md` as a procedure document.
Let the host select agents through its native delegation mechanism, or follow the sequential fallback in `WORKFLOW.md` when it has none.
Keep durable documents under `docs/` and use `docs/templates/` for new project artifacts.
Do not edit credentials, local environment files, caches, or vendored code unless the user names them.

## Build, Test, and Development Commands

Use the project’s setup and development commands instead of inventing replacements.
Report changed files, validation evidence, skipped checks, and risks.
Name each unavailable command and explain why it was not run.

## Coding Style and Testing

Keep changes scoped to the request and local patterns.
Preserve other work.
Do not broaden a requested change into unrelated cleanup.
Use English Markdown headings, language-tagged fences, and ASCII tree markers.
Update related documentation, templates, delegation policy, skills, validation helpers, and generated metadata when behavior changes.

## Commit and Publication

`WORKFLOW.md` owns the lifecycle process.
This contract adds no lifecycle rules.

## Security and Configuration

Review hooks, scripts, MCP, settings, and generated assets for command, filesystem, network, credential, and publication risks.
For changes that can trigger external behavior, identify the actor, boundary, and affected target.
