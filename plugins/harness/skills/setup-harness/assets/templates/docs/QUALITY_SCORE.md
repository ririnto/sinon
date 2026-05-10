# Quality Score

Track repository quality signals, current grade, and ratchet criteria for {project-name}.

## Current Grade

{A/B/C/D/F or repository-specific quality grade with date and owner}

## Signals

| Signal | Status | Evidence |
| --- | --- | --- |
| {test, lint, type, security, docs, or reliability signal} | {pass/fail/warn} | `{command, CI run, or report path}` |
| {harness validation signal} | {pass/fail/warn} | `sh scripts/harness/validate_harness.sh` |

## Grade Criteria

- {measurable condition required before increasing the grade}
- {regression that immediately lowers or blocks the grade}

## History

| Date | Grade | Change |
| --- | --- | --- |
| {date} | {grade} | {what changed} |
