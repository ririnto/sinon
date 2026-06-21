---
status: active
created: "{{yyyy-MM-dd}}"
updated: "{{yyyy-MM-dd}}"
completed: "{{yyyy-MM-dd or empty while active}}"
author: "{{author}}"
assignee: "{{assignee}}"
---

# {{yyyy-MM-dd}}-{{plan-slug}} Work Plan

## TL;DR (For humans)

What you get: {{observable outcome in one sentence}}

Why this approach: {{repo evidence and constraint summary}}

What it will not do: {{explicit out-of-scope behavior, files, or contracts}}

Effort: {{small|medium|large, with the reason}}

Risk: {{low|medium|high, with the reason}}

Decisions: {{defaults and owner decisions already resolved}}

## Scope

Goal: {{single-sentence-outcome}}

Global constraints:

- {{version floor, dependency limit, naming rule, platform target, or policy copied from the source spec}}

In scope:

- {{exact path, behavior, or contract}}

Out of scope:

- {{exact path, behavior, or contract}}

References:

- `{{path/to/source}}`: {{why it matters}}

Open questions: {{none, or owner decisions that block execution}}

## File Structure

| Path | Action | Responsibility |
| --- | --- | --- |
| `{{exact/path/to/file}}` | create | {{one clear responsibility}} |
| `{{exact/path/to/existing}}` | modify | {{what changes and why this file owns it}} |
| `{{exact/path/to/test}}` | test | {{behavior pinned by the test}} |

Boundary decisions:

- {{why these files change together}}
- {{interface or ownership rule that prevents cross-task drift}}

## Verification Strategy

Test strategy: {{TDD|tests-after|no new tests, with reason}}

Manual QA gate: {{agent-executable action and expected observable result}}

Validation command:

```sh
{{stack-validation-command}}
```

Failure condition: {{specific command output, assertion, or artifact state that blocks completion}}

## Execution Strategy

Worktree: use the agent runtime's built-in worktree tool when one is available.
If no built-in tool is available, use Git directly.

Parallelization: {{serial|parallel tasks and why they do not conflict}}

Implementation order:

1. {{first dependency or task group}}
2. {{second dependency or task group}}
3. {{final integration or verification}}

## Todos

### Task 1: {{component-name}}

References:

- `{{exact/path/to/reference}}`: {{specific rule or pattern}}

Interfaces:

- Consumes: {{exact symbol, command, schema, or contract from earlier work}}
- Produces: {{exact symbol, command, schema, or contract later work relies on}}

Files:

- Create: `{{exact/path/to/file}}`
- Modify: `{{exact/path/to/existing}}`
- Test: `{{exact/path/to/test}}`

Acceptance:

- {{agent-verifiable assertion}}

QA:

- Happy path: run `{{command}}`; evidence: `{{expected output or artifact}}`.
- Failure path: run `{{command}}`; evidence: `{{expected failure or guardrail}}`.

Commit:

```text
{{conventional-commit-message}}
```

Steps:

- [ ] Write the failing test or record why no new test is needed.
- [ ] Run the focused test and record the expected failure.
- [ ] Make the smallest implementation change.
- [ ] Run the focused test and record the pass.
- [ ] Run the validation command.
- [ ] Update docs or generated evidence if required.
- [ ] Commit the task.

### Task 2: {{component-name}}

References:

- `{{exact/path/to/reference}}`: {{specific rule or pattern}}

Interfaces:

- Consumes: {{exact symbol, command, schema, or contract from earlier work}}
- Produces: {{exact symbol, command, schema, or contract later work relies on}}

Files:

- Create: `{{exact/path/to/file}}`
- Modify: `{{exact/path/to/existing}}`
- Test: `{{exact/path/to/test}}`

Acceptance:

- {{agent-verifiable assertion}}

QA:

- Happy path: run `{{command}}`; evidence: `{{expected output or artifact}}`.
- Failure path: run `{{command}}`; evidence: `{{expected failure or guardrail}}`.

Commit:

```text
{{conventional-commit-message}}
```

Steps:

- [ ] Write the failing test or record why no new test is needed.
- [ ] Run the focused test and record the expected failure.
- [ ] Make the smallest implementation change.
- [ ] Run the focused test and record the pass.
- [ ] Run the validation command.
- [ ] Update docs or generated evidence if required.
- [ ] Commit the task.

## Final Verification Wave

- [ ] Plan compliance audit: every task has references, acceptance, QA, and commit evidence.
- [ ] Placeholder scan: no `TBD`, `TODO`, vague error handling, or `write tests` without concrete test content remains.
- [ ] Type and interface consistency: names, signatures, commands, and schemas match across tasks.
- [ ] Code quality review: changed files match project conventions and avoid scope creep.
- [ ] Manual QA: drive the changed surface and record the observed result.
- [ ] Scope fidelity: confirm out-of-scope files and contracts did not change.
- [ ] Repository validation: run the stack validation command and record the result.

## Commit Strategy

- Use one logical commit per task unless tasks are inseparable.
- Keep implementation, tests, and directly required docs in the same commit.
- Do not include unrelated worktree changes.

## Success Criteria

- {{observable outcome}}
- {{validation command exits 0}}
- {{manual QA evidence exists}}
- {{review findings are resolved or recorded with owner}}

## Rollback Criteria

{{specific rollback criteria}}

<!--
Template rules:

- Save execution plans under `docs/exec-plans/active/` as `yyyy-MM-dd-<slug>.md`.
- Move completed plans to `docs/exec-plans/completed/` without renaming.
- Replace every `{{placeholder}}` before execution.
- Make the plan decision-complete: a worker should not need another interview.
- Keep the first `##` heading as `## TL;DR (For humans)`.
- Every task needs references, acceptance criteria, happy-path QA, failure-path QA, and a commit line.
- Each task should be small enough to carry its own test cycle and reviewer gate.
- Code-changing steps should include concrete code, commands, and expected output.
- Do not add hash metadata, branch metadata, or external planning-tool paths to this template.
-->
