---
status: active
created: "{{yyyy-MM-dd}}"
updated: "{{yyyy-MM-dd}}"
completed: "{{yyyy-MM-dd or empty while active}}"
author: "{{author}}"
assignee: "{{assignee}}"
---

# {{yyyy-MM-dd}}-{{plan-slug}} Implementation Plan

> For agentic workers: REQUIRED SUB-SKILL: use the installed `.claude/skills/harness-orchestrate` skill to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking; flip a checkbox only after the step's work and its named validation command pass.

Goal: {{single-sentence-outcome}}

Architecture: {{2-3-sentences-on-approach-and-why-this-plan-exists}}

Tech Stack: {{key-technologies-and-the-stack-validation-command}}

---

<!--
Template notes:
- Save active plans as `docs/exec-plans/active/yyyy-MM-dd-<slug>.md`; keep the slug kebab-case and work-focused.
- Update `updated` whenever the plan body changes; set `completed` only when moving the plan to `docs/exec-plans/completed/`.
- `author` records who drafted the plan; `assignee` records who executes it and MAY list multiple owners.
- Decompose work into bite-sized tasks: each step is one 2-5 minute action with exact file paths, the exact code or command, and the expected result.
- No placeholders in a real plan: replace every `{{...}}` slot with concrete content before execution. `TBD`, `add error handling`, or `write tests for the above` without the actual test code are plan failures.
- Tasks are ordered. Express cross-task dependencies inline (for example, `depends on: Task 1`). Independent tasks MAY run in parallel.
-->

## Task 1: {{component-name}}

Files:

- Create: `{{exact/path/to/file}}`
- Modify: `{{exact/path/to/existing}}:{{line-range}}`
- Test: `{{exact/path/to/test}}`

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

- [ ] Step 5: Run the harness validator

    Run: `{{stack-validation-command}}`

    Expected: validator green

- [ ] Step 6: Commit

    ```sh
    git add {{changed-paths}}
    git commit -m "{{conventional-commit-message}}"
    ```

## Task 2: {{component-name}}

Files:

- Create: `{{exact/path/to/file}}`
- Test: `{{exact/path/to/test}}`

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

- [ ] Step 5: Commit

    ```sh
    git add {{changed-paths}}
    git commit -m "{{conventional-commit-message}}"
    ```

## Validation

Run the stack-specific harness validation command after each task that touches required harness assets, and record the command and result in that task's checkbox.

## Rollback Criteria

{{rollback-criteria}}

## Completion

<!--
When every task checkbox is checked, move this file from `docs/exec-plans/active/` to `docs/exec-plans/completed/` without renaming, then change `status: active` to `status: completed` and set `completed: yyyy-MM-dd` in frontmatter. The filename date stays the original creation date. Plans in `docs/exec-plans/completed/` MUST NOT contain any unchecked `- [ ]` task lines.
-->
