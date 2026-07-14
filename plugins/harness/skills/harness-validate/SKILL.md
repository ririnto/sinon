---
name: harness-validate
description: >-
  Validate an installed Harness target and diagnose its install-record or canonical stack-command failures. Use after installation, before target handoff, or when installed harness validation fails.
---

# Harness Validate

Validate one installed target directly. Native delegation may help investigate a large failure surface, but it is optional.

## First Safe Checks

1. Run from the target repository root.
2. Read `.harness/install-record.json`; it supplies the selected mode and canonical command.
3. If the record is missing, invalid, or partial, report it. Do not infer a replacement command.
4. Keep validation output and failure exits visible.

## Procedure

1. Validate the record:

    ```sh
    bun "${CLAUDE_PLUGIN_ROOT}/skills/harness-validate/scripts/validate-install-record.ts" .
    ```

2. If it passes, run the exact `canonicalCheckCommand` recorded there.
3. If a command fails, distinguish install-record failure, harness-contract drift, and native stack-tool failure.
4. Make only requested repairs, then rerun the record validator and the same canonical command.
5. When CI files are in scope, confirm each active file matches the canonical command. Installed `pre-commit` uses it too; `pre-push` may use a stricter local command.

## Decisions

- Missing required assets, incomplete inventory, unresolved conflict, command mismatch, or harness-owned drift require repair or a new installation. Do not hand-edit the record to hide them.
- Use `harness-install --adopt <path>` only after reviewing a legitimate target-owned divergence; it preserves target bytes. Then validate and refresh normally.
- Report generic placeholder content as missing target truth.
- Report tool failures that occur before harness checks separately from harness health.
- If CI is outside scope, report its mismatch instead of changing it.

## Outcome Interpretation

- Record validation passes: inventory, ownership, digests, and canonical command agree with the installed plan.
- Canonical command passes: the selected stack validation passed.
- Either command fails: the target is not fully validated; report the earliest failure and smallest next action.

## Output Contract

Report:

- `mode`.
- `command`: record validator and canonical stack command.
- `result`: pass or fail with exit status when available.
- `failures`: paths, category, and evidence.
- `ci`: checked status or out-of-scope status.
- `next action`: smallest valid repair or reason no repair was made.

## Support Files

- Open the installed `WORKFLOW.md` only when its project-local rules affect the requested validation work.
- Use the installer's stack assets to diagnose a recorded command; do not derive commands from broad stack detection when the record is valid.
