---
name: review
description: Use when reviewing a diff or completed change for correctness and requirement compliance.
---

# Review

Judge the change against the stated requirements and the target repository's own rules, not unstated preferences.
Use the [shared engineering rules](../../docs/rules.md) for instruction discovery, authority, validation, evidence, and review criteria.
From their index, load language and tool guidance only for affected files, profiles, or suspected findings.

## Review

Read the complete scoped diff, surrounding code, relevant callers, and existing validation evidence.
Verify each suspected finding with a concrete failure scenario or a violated explicit requirement.
For a rule-based finding, name its owning file and quote the relevant rule.
Run additional checks only for changed behavior, failed checks, or unresolved concerns not covered by valid evidence.

Report findings by severity with file and line references, evidence, and material assumptions or validation gaps.
State explicitly when no findings remain.
Follow the task's output format and distinguish source inspection from runtime proof and independent review.
Review authority does not grant edits or Git actions.
When fixes are authorized, reuse the implementer's context when supported and rerun only affected checks.
