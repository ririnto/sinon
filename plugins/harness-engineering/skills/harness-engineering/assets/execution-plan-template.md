# Execution Plan Template

Copy this template when writing execution plans for complex work. Place active plans in `docs/exec-plans/active/` and move completed plans to `docs/exec-plans/completed/`.

```markdown
# {Plan Title}

## Context

- Author: {agent or human name}
- Created: {date}
- Domain: {business domain}
- Status: active | completed | blocked

## Problem

{One paragraph describing what this plan addresses and why it matters.}

## Approach

{Describe the chosen approach. List alternatives considered if relevant.}

## Steps

- [ ] Step 1: {description}
- [ ] Step 2: {description}
- [ ] Step 3: {description}

## Decision log

| Date | Decision | Rationale |
| --- | --- | --- |
| {date} | {decision} | {why} |

## Progress

| Date | Update |
| --- | --- |
| {date} | {progress note} |

## Risks

- {risk 1}
- {risk 2}

## Dependencies

- {dependency 1}
- {dependency 2}
```

## Usage notes

- Ephemeral lightweight plans for small changes may use a shortened form: problem, steps, and status only.
- Complex work MUST use the full template with decision log and progress tracking.
- Plans are checked into the repository so agents can operate without external context.
- When a plan is completed, move it to `docs/exec-plans/completed/` and update the status field.

