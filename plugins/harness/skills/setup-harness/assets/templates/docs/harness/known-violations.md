# Known Violations

Track accepted harness violations for {project-name}. Each entry needs an owner, mitigation, and removal condition.

## Active Violations

| ID | Rule | Location | Impact | Owner | Removal Condition |
| --- | --- | --- | --- | --- | --- |
| {violation-id} | {guardrail or validator rule} | `{path or command}` | {risk if left unresolved} | {owner} | {date, release, or evidence} |

## Accepted Risk

- {risk statement tied to an active violation and why it is acceptable temporarily}
- {monitoring, manual review, or mitigation that reduces the risk}

## Resolved Violations

| ID | Resolved By | Evidence | Date |
| --- | --- | --- | --- |
| {violation-id} | {change or decision} | `{command, PR, or log}` | {date} |

## Review Cadence

Review this file {cadence} and remove entries as soon as the referenced violation is fixed or the exception expires.
