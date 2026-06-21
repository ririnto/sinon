---
status: active
created: "{{yyyy-MM-dd}}"
updated: "{{yyyy-MM-dd}}"
completed: "{{yyyy-MM-dd or empty while active}}"
---

# {{plan-title}} Work Plan

## Goal

{{observable-outcome-and-reason}}

## Scope

- In scope: {{paths-or-behaviors-to-change}}
- Out of scope: {{paths-or-behaviors-not-to-change}}
- Assumptions: {{material-assumptions}}
- References: {{source-docs-or-code-paths}}

## Tasks

- [ ] {{task-1-action}}

    Scope: `{{exact/path}}`

    Acceptance: {{agent-verifiable-result}}

    Verify:

    ```sh
    {{command}}
    ```

- [ ] {{task-2-action}}

    Scope: `{{exact/path}}`

    Acceptance: {{agent-verifiable-result}}

    Verify:

    ```sh
    {{command}}
    ```

## Verification

- Focused check: `{{focused-command-or-review}}`
- Manual QA: {{observable-action-and-result}}
- Final check:

```sh
{{stack-validation-command}}
```

## Success Criteria

- {{observable outcome}}
- {{validation command exits 0}}
- {{review findings resolved or recorded with owner}}

<!--
- Save execution plans under `docs/exec-plans/active/` as `yyyy-MM-dd-<slug>.md`.
- Move completed plans to `docs/exec-plans/completed/` without renaming.
- Replace every `{{placeholder}}` before execution.
- Completed plans must not keep unchecked task lines.
-->
