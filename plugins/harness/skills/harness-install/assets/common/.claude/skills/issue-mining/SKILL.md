---
name: issue-mining
description: >-
  Investigate reported or suspected issues and decide whether to register them. Use when a user asks about a specific issue, duplicate reports, related upstream issues, code causes, or open-ended improvement candidates.
---

# Issue Mining

Investigate whether a reported or suspected problem should become an issue.
End with a report unless the user asks to create or update a host issue, plan, or local review record.

## Operating Rules

- Do not implement a fix during issue mining.
- Use internal exploration for repository code, tests, logs, validation output, docs, and local review records.
- Use external exploration when duplicate issues, upstream reports, release notes, dependency behavior, or user reports may affect the answer.
- Register a host issue, plan, or local review record only when the user requests registration.
- Use the current workflow's host policy for GitHub, GitLab, or local record creation.
- Name blockers when evidence is missing or an owner decision is required.

## First Safe Checks

1. Identify whether the user named a specific issue or asked for open-ended candidates.
2. Identify the current host flow from `WORKFLOW.md`.
3. Choose direct exploration or scoped subagent delegation.
4. Decide whether external exploration is required for duplicate, upstream, dependency, or user-report evidence.

## Specific Issue Procedure

Use this procedure when the user names an issue, suspected bug, review comment, or related request.

1. Read the record and linked artifacts.
2. Search open issues, review records, and recent reports for duplicates or related work.
3. Inspect relevant repository code, tests, logs, validation output, and docs.
4. Inspect external sources when upstream behavior, dependency releases, or public reports may affect the answer.
5. Report duplicate links, related records, likely cause, affected files, impact, risk, and evidence.
6. Create or update the host issue, plan, or local review record only when the user requested registration.

## Open-Ended Mining Procedure

Use this procedure when no issue is assigned and the user asks for improvement candidates.

1. Survey repository structure, architecture docs, validation failures, review gaps, user-facing workflows, and repeated maintenance cost.
2. Check external sources when upstream projects, dependency behavior, host issues, or user reports may confirm or reject a candidate.
3. Group findings by product risk, correctness risk, security risk, maintenance cost, or documentation drift.
4. Report candidates with impact, evidence, affected files, duplicate or related records, and suggested issue titles.
5. Create or update the host issue, plan, or local review record only when the user requested registration.

## Registration Output

When the user requests issue registration, prepare:

- title
- problem statement
- evidence
- affected files or systems
- expected behavior
- current behavior
- duplicate or related records
- validation or reproduction notes
- owner decision needed, if any

Use the host command or local record path selected by `WORKFLOW.md`.

## Report Output

Return:

- `summary`: issue status and recommendation
- `duplicates`: matching or related records
- `cause`: likely source and affected files
- `evidence`: internal and external evidence checked
- `registration`: created or updated record, or reason not registered
- `blockers`: missing evidence or owner decisions
