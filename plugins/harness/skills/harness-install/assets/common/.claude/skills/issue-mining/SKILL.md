---
name: issue-mining
description: >-
  Investigate reported or suspected repository issues and recommend whether to register them. Use for specific issues, duplicates, related reports, likely causes, or open-ended improvement candidates.
---

# Issue Mining

Investigate and report. Do not implement a fix or create a record unless the user asks.

## First Safe Checks

1. Identify a named issue or an open-ended request for candidates.
2. Define a bounded evidence surface: records, code, tests, logs, validation output, and docs.
3. Decide whether external evidence is needed for duplicates, upstream behavior, dependencies, releases, or user reports.

## Procedure

1. Read the named record and linked artifacts, or survey the bounded repository surface for candidates.
2. Search local issues, review records, and recent reports for duplicates or related work.
3. Inspect relevant code, tests, logs, validation output, and docs.
4. Check external sources only when they affect the conclusion.
5. Report cause, impact, evidence, affected files, and duplicate or related records.
6. Create or update an issue, plan, or local review record only with explicit user authority, a selected target, and available authenticated tooling.

## Decisions

- Keep repository exploration read-only unless the user requests registration.
- Name missing evidence and owner decisions as blockers.
- Use native delegation only when it materially improves bounded investigation.
- Read `WORKFLOW.md` only when target-local record rules apply.

## Output Contract

Return:

- `summary`: status and recommendation.
- `duplicates`: matching or related records.
- `cause`: likely source and affected files.
- `evidence`: internal and external sources checked.
- `registration`: created or updated record, or reason not registered.
- `blockers`: missing evidence or owner decisions.

When registration is requested, prepare `title`, `problem statement`, `evidence`, `affected files or systems`, `expected behavior`, `current behavior`, `duplicate or related records`, and `validation or reproduction notes`.
