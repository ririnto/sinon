---
name: validate
description: >-
  Run the installed target repository validator and report contract readiness.
  Use this skill after changing `CLAUDE.md`, `ARCHITECTURE.md`, docs, .claude agents, .claude skills, docs assets, hooks, CI, or generated artifacts.
---

# Validate

Validate this target repository's installed contract.
This target skill uses the local `docs/README.md` command; plugin-level validation belongs to the plugin checkout.

## First Safe Checks

1. Read `docs/README.md` for the stack command.
2. Confirm you are running from the target repository root.
3. Confirm whether the repository is Gradle, Maven, uv, bun, or shell based on local files.

## Commands

Run the command rendered into `docs/README.md`:

```sh
{{validation_command}}
```

## Workflow

1. Run the rendered validation command from `docs/README.md`.
2. If it fails, classify the failure as missing contract file or directory, missing `.gitkeep`, stale placeholder, metadata/frontmatter problem, generated-artifact metadata gap, symlink safety issue, executable-bit or shebang problem, CI/pre-push command mismatch or pre-commit stage mismatch, or native stack-tool failure.
3. Fix only failures within the requested scope.
4. Re-run the same command after fixes.

## Invariants

- The installed `CLAUDE.md` and `.editorconfig`/stack config files are the target repository contract.
- Generated pre-commit and pre-push hooks both run the stack-specific validation command.
- `docs/generated/` may be empty, but generated files that exist need regeneration metadata.
- Seed references may be replaced when they do not match the target stack or domain.

## Output Contract

Report:

- `command`: exact command run.
- `result`: pass or fail.
- `failures`: contract category and file path.
- `next action`: fix made, required owner action, or blocker.
