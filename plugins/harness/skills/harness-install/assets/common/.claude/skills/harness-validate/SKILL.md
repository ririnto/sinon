---
name: harness-validate
description: >-
  Run the installed target repository harness validator and report contract readiness. Use this skill after changing CLAUDE.md, ARCHITECTURE.md, docs, .claude agents, .claude skills, docs/harness assets, hooks, CI, or generated artifacts.
---

# Harness Validate

Validate this target repository's installed harness. This target skill uses the local `docs/harness/README.md` command; plugin-level validation belongs to the plugin checkout.

## First Safe Checks

1. Read `docs/harness/README.md` for the stack command.
2. Confirm you are running from the target repository root.
3. Confirm whether the repository is Gradle, Maven, uv, bun, or shell based on local files.

## Commands

Run the matching command:

| Stack | Command |
| --- | --- |
| Gradle | `./gradlew ktlintCheck` (or `gradle ktlintCheck` if using system Gradle without a wrapper) |
| Maven | See the Maven command below. |
| uv | `uv run scripts/check.py` |
| Bun | `bun run check` |
| shell | `sh scripts/check.sh` |

Maven command:

```sh
root=$(pwd -P); files=$(git ls-files -- "*.java" | while IFS= read -r file; do case "$file" in *,*) echo "error: Java path contains comma and cannot be represented in spotlessFiles: $file" >&2; exit 1;; esac; printf '%s/%s\n' "$root" "$file" | sed 's/[][\\.^$*+?{}()|]/\\&/g; s/^/^/; s/$/$/'; done | paste -sd, -); if [ -z "$files" ]; then mvn validate; echo "spotless: no tracked Java files to check"; else mvn validate spotless:check -DspotlessFiles="$files"; fi
```

## Workflow

1. Run the matching command.
2. If it fails, classify the failure as missing contract file or directory, missing `.gitkeep`, stale placeholder, metadata/frontmatter problem, generated-artifact metadata gap, symlink safety issue, executable-bit or shebang problem, CI/pre-push command mismatch or pre-commit stage mismatch, or native stack-tool failure.
3. Fix only failures within the requested scope.
4. Re-run the same command after fixes.

## Invariants

- The installed `CLAUDE.md` and `.editorconfig`/stack config files are the target repository harness contract.
- Generated pre-commit and pre-push hooks both run the stack-specific validation command.
- `docs/generated/` may be empty, but generated files that exist need regeneration metadata.
- Seed references may be replaced when they do not match the target stack or domain.

## Output Contract

Report:

- `command`: exact command run.
- `result`: pass or fail.
- `failures`: contract category and file path.
- `next action`: fix made, required owner action, or blocker.
