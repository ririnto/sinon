---
status: active
created: "{{yyyy-MM-dd}}"
updated: "{{yyyy-MM-dd}}"
completed: "{{yyyy-MM-dd or empty while active}}"
author: "{{author}}"
assignee: "{{assignee}}"
---

# {{yyyy-MM-dd}}-{{plan-slug}}

<!--
Template notes:
- Update `updated` whenever the plan body changes; set `completed` only when moving the plan to `docs/exec-plans/completed/`.
- Save active plans as `docs/exec-plans/active/yyyy-MM-dd-<slug>.md`; keep the slug kebab-case and work-focused.
- `Author` records who drafted the plan. `Assignee` records who executes it and MAY list multiple owners.
- Phases are sequential. Tasks inside one phase MUST be independent and parallel-safe; express intra-phase dependencies with `blocked by`.
- Mark phase headings `[x]` only when every task inside is checked.
- Flip task checkboxes only after the work and named validation command pass.
- If a task cannot be validated yet, keep it unchecked until a later task validates the combined result.
-->

{{short description}}

## Backgrounds

{{why-this-plan-exists-and-what-evidence-triggered-it}}

## Goal

{{single-sentence-outcome}}

## Non-Goals

- {{out-of-scope-item}}

## Phases

### [ ] Phase 1: {{phase-1-title}}

- [ ] Task 1.1 — {{task-description}} (subagent: {{agent-type-or-main}})
- [ ] Task 1.2 — {{task-description}} (subagent: {{agent-type-or-main}})
- [ ] Task 1.3 — {{task-description}} (subagent: {{agent-type-or-main}}, blocked by: Task 1.1)

### [ ] Phase 2: {{phase-2-title}}

- [ ] Task 2.1 — {{task-description}} (subagent: {{agent-type-or-main}})

## Validation

Run the stack-specific harness validation command after each phase that touches required harness assets. Record the command and result in the relevant task checkbox.

## Rollback Criteria

{{rollback-criteria}}

## Completion

<!--
When every task is checked, move this file from `docs/exec-plans/active/` to `docs/exec-plans/completed/` without renaming, then change `status: active` to `status: completed` and set `completed: yyyy-MM-dd` in frontmatter. The filename date stays the original creation date.
-->
