# Repository Guidelines

This file applies to `plugins/workspace-workflow/` and overrides broader plugin guidance.

## Project Structure

`skills/pr-mr-convention/` owns host-neutral integration handoff guidance and host references. Agents draft or route work; they do not integrate.

## Build, Test, and Development Commands

Inspect `git remote -v`, `git status --short --branch`, and `git branch -vv` before host advice. Run `claude plugin validate plugins/workspace-workflow`, `bun test scripts/workflow-contract.test.ts`, and `bun run check` after routing changes.

## Coding Style and Testing


## Commit and integration handoff

Preserve dirty worktrees and unrelated branches. Treat force-push and history rewriting as explicit user decisions. The top-level session owns commits and integration handoff.

## Security and Configuration

Report stale remote data and template uncertainty. Do not expose credentials or run host authentication checks before host selection.

## Scope and Precedence

State which host evidence selected before drafting integration handoff guidance.
