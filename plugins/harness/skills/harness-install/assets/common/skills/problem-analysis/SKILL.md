---
name: problem-analysis
description: >-
  Investigate reported or suspected problems and decide whether to record them. Use when a user asks about a specific problem, duplicate reports, related upstream work, code causes, or open-ended improvement candidates.
---

# Problem Analysis

Investigate whether a reported or suspected problem should become a recorded task or change description.
End with a report unless the user asks to create or update a local record.

## Operating Rules

- Do not implement a fix during problem analysis.
- Use internal exploration for repository code, tests, logs, validation output, docs, and local review records.
- Use external exploration when duplicate records, upstream reports, release notes, dependency behavior, or user reports may affect the answer.
- Create a task record, plan, or local review note only when the user requests it.
- Use the templates under `docs/templates/` for task and change-description records when the repository provides them.
- Name blockers when evidence is missing or an owner decision is required.

## First Safe Checks

1. Identify whether the user named a specific problem or asked for open-ended candidates.
2. Identify the repository documented record or workflow guidance, if any.
3. Choose direct exploration or scoped subagent delegation.
4. Decide whether external exploration is required for duplicate, upstream, dependency, or user-report evidence.

## Named Problem Procedure

Use this procedure when the user names a problem, suspected bug, review comment, or related request.

1. Read the record and linked artifacts.
2. Search existing task and change records, review records, and recent reports for duplicates or related work.
3. Inspect relevant repository code, tests, logs, validation output, and docs.
4. Inspect external sources when upstream behavior, dependency releases, or public reports may affect the answer.
5. Report duplicate links, related records, likely cause, affected files, impact, risk, and evidence.
6. Create or update the task record, plan, or local review note only when the user requested it.

## Open-Ended Mining Procedure

Use this procedure when no problem is assigned and the user asks for improvement candidates.

1. Survey repository structure, architecture docs, validation failures, review gaps, user-facing workflows, and repeated maintenance cost.
2. Check external sources when upstream projects, dependency behavior, existing records, or user reports may confirm or reject a candidate.
3. Group findings by product risk, correctness risk, security risk, maintenance cost, or documentation drift.
4. Report candidates with impact, evidence, affected files, duplicate or related records, and suggested task titles.
5. Create or update the task record, plan, or local review note only when the user requested it.

## Record Output

When the user requests a recorded task, prepare:

- title
- problem statement
- evidence
- affected files or systems
- expected behavior
- current behavior
- duplicate or related records
- validation or reproduction notes
- owner decision needed, if any

Use the templates under `docs/templates/` when the repository provides them.

## Report Output

Return:

- `summary`: problem status and recommendation
- `duplicates`: matching or related records
- `cause`: likely source and affected files
- `evidence`: internal and external evidence checked
- `record`: created or updated record, or reason not recorded
- `blockers`: missing evidence or owner decisions
