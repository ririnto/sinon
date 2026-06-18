---
name: validate
description: >-
  Run the installed target repository validator and report contract readiness.
  Use this skill after changing `CLAUDE.md`, `ARCHITECTURE.md`, docs, .claude agents, .claude skills, docs assets, hooks, CI, or generated artifacts.
---

# Validate

Validate this target repository's installed contract.
This target skill uses the validation command supplied in the task prompt.
Plugin-level validation belongs to the plugin checkout.

## First Safe Checks

1. Confirm the task prompt names the stack validation command.
2. Confirm you are running from the target repository root.
3. Confirm whether the repository is Gradle, Maven, uv, bun, or shell based on local files.

## Commands

Run the supplied validation command:

```sh
<selected-stack-validation-command>
```

## Workflow

1. Run the supplied validation command.
2. If it fails, classify the failure as missing contract file or directory, missing `.gitkeep`, stale placeholder, metadata/frontmatter problem, generated-artifact metadata gap, symlink safety issue, executable-bit or shebang problem, CI/pre-push command mismatch or pre-commit stage mismatch, or native stack-tool failure.
3. Fix only failures within the requested scope.
4. Re-run the same command after fixes.

## Invariants

- The installed `AGENTS.md` and `.editorconfig`/stack config files are the target repository contract.
- Installed pre-commit and pre-push hooks both run the stack-specific validation command.
- `docs/generated/` may be empty, but generated files that exist need regeneration metadata.
- Seed references may be replaced when the target stack or domain uses different sources.

## Output Contract

Report:

- `command`: exact command run.
- `result`: pass or fail.
- `failures`: contract category and file path.
- `next action`: fix made, required owner action, or blocker.
