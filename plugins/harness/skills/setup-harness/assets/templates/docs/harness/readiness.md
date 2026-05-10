# Harness Readiness

Track whether {project-name} is ready to move from advisory harness checks to enforced CI or hook gates.

## Current Status

| Field | Value |
| --- | --- |
| Current stage | {0, 1, 2, 3, or 4} |
| Target stage | {target stage} |
| Enforcement level | {warn or error} |
| Decision owner | {owner} |
| Last reviewed | {date} |

## Readiness Checklist

- [ ] `docs/harness/config.json` matches the installed file tree.
- [ ] Required docs are present and customized for this repository.
- [ ] Required commands run locally without missing-tool failures.
- [ ] Known violations are recorded in `docs/harness/known-violations.md`.
- [ ] CI or hook enforcement has an identified rollback owner.

## Validation Evidence

| Check | Command | Result | Evidence |
| --- | --- | --- | --- |
| Harness config | `sh scripts/harness/validate_harness.sh` | {pass/warn/fail} | `{log, CI run, or date}` |
| Build | `{build command}` | {pass/warn/fail} | `{log, CI run, or date}` |
| Tests | `{test command}` | {pass/warn/fail} | `{log, CI run, or date}` |

## Next Gate

{Describe the next readiness decision, required owner approval, and the exact config change that would enable enforcement.}
