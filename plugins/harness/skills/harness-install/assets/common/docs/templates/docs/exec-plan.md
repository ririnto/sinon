---
status: active
created: "{{yyyy-MM-dd}}"
updated: "{{yyyy-MM-dd}}"
completed: "{{yyyy-MM-dd or empty while active}}"
author: "{{author}}"
assignee: "{{assignee}}"
---

# {{yyyy-MM-dd}}-{{plan-slug}} Implementation Plan

> For agentic workers:
> Use the task prompt as the source of workflow decisions.
> Steps use checkbox (`- [ ]`) syntax for tracking.
> Flip a checkbox only after the step's work and its named validation command pass.

Goal: {{single-sentence-outcome}}

Architecture: {{2-3-sentences-on-approach-and-why-this-plan-exists}}

Tech Stack: {{key-technologies-and-the-stack-validation-command}}

Success Criteria: {{observable-outcomes-that-prove-the-goal-is-done}}

Must Not Change: {{explicit-out-of-scope-files-behaviors-or-public-contracts}}

Decision Record: {{repo-evidence-defaults-and-owner-decisions-that-remove-open-questions}}

Open Questions: {{none, or list the owner decisions that must be resolved before execution}}

---

<!--
Template notes:
- Save execution plans as files under `docs/exec-plans/` named `yyyy-MM-dd-<slug>.md`.
  - Keep the slug kebab-case and work-focused.
- Update `updated` whenever the plan body changes.
  - Set `completed` only when moving the plan to the completed-state location in `docs/exec-plans/`.
- `author` records who drafted the plan.
  - `assignee` records who executes it and MAY list multiple owners.
- Make the plan decision-complete.
  - A worker should not need another interview before implementation.
- Decompose work into bite-sized tasks.
  - Each step names exact file paths, the exact code or command, and the expected result.
- No placeholders in a real plan.
  - Replace every `{{...}}` slot with concrete content before execution.
  - `TBD`, `add error handling`, or `write tests for the above` without the actual test code are plan failures.
- No open questions remain at execution time.
  - If a material fork still exists, resolve it before assigning the plan.
- Tasks are ordered.
  - Express cross-task dependencies inline (for example, `depends on: Task 1`).
  - Independent tasks MAY run in parallel.
- Every task needs agent-executed QA.
  - Include the command, expected evidence, and failure condition.
-->

## Task 1: {{component-name}}

Files:

- Create: `{{exact/path/to/file}}`
- Modify: `{{exact/path/to/existing}}:{{line-range}}`
- Test: `{{exact/path/to/test}}`

Acceptance:

- {{specific-observable-assertion}}

Dependencies:

- {{none|Task N}}

Parallelization:

- {{serial|parallel-with-task-numbers-and-reason}}

Steps:

- [ ] Step 1: Write the failing test

    ```text
    {{test-code-that-asserts-the-specific-behavior}}
    ```

- [ ] Step 2: Run the test to verify it fails

    Run: `{{stack-test-command}}`

    Expected: FAIL with `{{expected-failure-message}}`

- [ ] Step 3: Write the minimal implementation

    ```text
    {{minimal-implementation-code}}
    ```

- [ ] Step 4: Run the test to verify it passes

    Run: `{{stack-test-command}}`

    Expected: PASS

- [ ] Step 5: Review the change

    Run: `{{review-command-or-process}}`

    Expected: review findings resolved or explicitly recorded

- [ ] Step 6: Run the stack validator

    Run: `{{stack-validation-command}}`

    Expected: validator green

- [ ] Step 7: Commit

    ```sh
    git add {{changed-paths}}
    git commit -m "{{conventional-commit-message}}"
    ```

## Task 2: {{component-name}}

Files:

- Create: `{{exact/path/to/file}}`
- Test: `{{exact/path/to/test}}`

Acceptance:

- {{specific-observable-assertion}}

Dependencies:

- {{none|Task N}}

Parallelization:

- {{serial|parallel-with-task-numbers-and-reason}}

Steps:

- [ ] Step 1: Write the failing test

    ```text
    {{test-code-that-asserts-the-specific-behavior}}
    ```

- [ ] Step 2: Run the test to verify it fails

    Run: `{{stack-test-command}}`

    Expected: FAIL with `{{expected-failure-message}}`

- [ ] Step 3: Write the minimal implementation

    ```text
    {{minimal-implementation-code}}
    ```

- [ ] Step 4: Run the test to verify it passes

    Run: `{{stack-test-command}}`

    Expected: PASS

- [ ] Step 5: Review the change

    Run: `{{review-command-or-process}}`

    Expected: review findings resolved or explicitly recorded

- [ ] Step 6: Run the stack validator

    Run: `{{stack-validation-command}}`

    Expected: validator green

- [ ] Step 7: Commit

    ```sh
    git add {{changed-paths}}
    git commit -m "{{conventional-commit-message}}"
    ```

## Validation

Run the stack-specific validation command after each task that touches required contract assets.
Record the command and result in that task's checkbox.

| Check | Command | Evidence | Failure action |
| --- | --- | --- | --- |
| Unit behavior | `{{unit-test-command}}` | `{{expected-output-or-report}}` | `{{fix-or-rollback}}` |
| Stack validation | `{{stack-validation-command}}` | `{{expected-output-or-report}}` | `{{fix-or-rollback}}` |
| Review | `{{review-command-or-process}}` | `{{expected-output-or-report}}` | `{{fix-or-record-risk}}` |

## Rollback Criteria

{{rollback-criteria}}

## Completion

<!--
When every task checkbox is checked, move this file between execution-plan locations in `docs/exec-plans/` without renaming.
Then change `status: active` to `status: completed` and set `completed: yyyy-MM-dd` in frontmatter.
The filename date stays the original creation date.
Plans in completed-state entries under `docs/exec-plans/` MUST contain either checked task lines or no task list.
-->
