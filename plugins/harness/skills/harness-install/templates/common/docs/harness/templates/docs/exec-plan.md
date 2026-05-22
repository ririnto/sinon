# {{yyyy-MM-dd}}-{{plan-slug}}

- Status: active
- Created: {{yyyy-MM-dd}}
- Last Updated: {{yyyy-MM-dd}}
- Completed: {{yyyy-MM-dd or empty while active}}
- Author: {{author}}
- Assignee: {{assignee}}

Update `Last Updated` whenever the plan body changes (a task is added, a checkbox flips, a phase opens or closes). Set `Completed` only when the plan moves to `docs/exec-plans/completed/`; while the plan is active the field MAY be left empty.

## File Naming Convention

Execution plan filenames MUST use the form `yyyy-MM-dd-<slug>.md` where the date is the creation date in the project's timezone. Active plans live under `docs/exec-plans/active/`. The slug MUST be kebab-case and describe the work, not the author.

`Author` records the person who drafted the plan. `Assignee` records the person or agent that executes the plan; the two MAY differ (for example, a tech lead drafts the plan and a separate engineer or agent executes it). `Assignee` MAY be a comma-separated list when execution is split across multiple owners.

## Plan Convention

- Phase: sequential execution unit. A phase MUST NOT start until the previous phase finishes. Phases form the top-level order of work.
- Task: parallel-safe unit inside one phase. Tasks within the same phase MUST be independent — no two tasks in the same phase write to the same file or otherwise contend for the same resource. Tasks SHOULD be sized to fit a single subagent invocation.
- Subagent delegation: tasks SHOULD be delegated to subagents in parallel within a phase. The main agent orchestrates phases sequentially and synthesizes results between phases.
- Dependencies: cross-phase dependencies are implied by phase order. Within a phase, dependencies between tasks MUST be expressed with `blocked by` so the executor knows what to wait for. A task with no `blocked by` is free to start immediately when the phase begins.
- Phase heading checkboxes: write phase headings with `[ ]` while in-flight and `[x]` once every task inside is checked. This keeps the table of contents scannable and lets validators detect partially-finished phases.

## Task Execution Order

Tasks MUST follow this order before any `[ ]` checkbox is flipped to `[x]`:

1. Read the task statement and any `blocked by` predecessor in this plan.
2. Perform the work (in-process or via subagent delegation).
3. Run the stack-specific harness validator (and any task-local check the task names). The validator command lives in `docs/harness/README.md`.
4. Only after the validator reports success, flip the task checkbox from `[ ]` to `[x]`.
5. When every task in a phase is `[x]`, flip that phase's heading checkbox from `[ ]` to `[x]`.

Skipping the validation step before flipping a checkbox is a contract violation. A task that intentionally cannot be validated yet (for example, because it sets up scaffolding for the next phase) MUST stay `[ ]` until a later task validates the combined result.

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

When every task is checked, move this file from `docs/exec-plans/active/` to `docs/exec-plans/completed/` without renaming, then change `- Status: active` to `- Status: completed` and append a `- Completed: yyyy-MM-dd` list item. The filename date stays the original creation date.
