# Workflow

Use this file for Git flow, review records, validation gates, and local publication policy.
Repository contract stays in `AGENTS.md`, architecture in `ARCHITECTURE.md`, and durable evidence in `docs/**`.
Use task prompts for implementation-only decisions.

## Work Loop

`WORKFLOW.md` is the source of truth for validation command selection and local publication policy.

| Phase | Action |
| --- | --- |
| Intake | Identify the local task, review record, or execution plan that owns the change. |
| Explore | Inspect the contract, relevant docs, code surface, validation command, and local review record. |
| Plan | Define the files, acceptance gate, manual QA, validation, and publication target. |
| Implement | Make the smallest target-owned change that satisfies the plan. |
| Review | Check correctness, security, contract drift, and missing evidence. |
| Validate | Run the selected stack command and active Git hooks. |
| Publish | Use the repository-approved local review or merge policy. |

## Execution Discipline

- Track non-trivial work with a visible task or todo list before editing.
- Keep exactly one task in progress, and mark completed tasks as soon as their validation passes.
- When scope changes, update the task list before continuing.
- Ask only for owner decisions that repository evidence cannot resolve.
- Delegate with a scoped prompt that names deliverable, files or domain, constraints, and verification.
- Do not declare completion until review evidence, validation output, and manual QA are recorded or explicitly marked not applicable.
- When validation fails, fix in the worktree and rerun the same gate before closing local review.

## Records

A milestone groups planned work.
Record milestone, issue, and review-request relationships in the execution plan while local policy applies.
Keep the planned closing relationship explicit.

```text
Closes #00
```

## Git

Branch: `<type>/<short-description>`.
Worktree: use the agent runtime's built-in worktree tool when one is available.
If no built-in tool is available, use Git directly:

```sh
git fetch origin
git worktree add <worktree-path> -b <type>/<short-description> origin/main
```

Commit subject:

```text
feat(auth): add session refresh endpoint
feat: add session refresh endpoint
```

Use one logical intent per commit.
Commit bodies may use a list.
Respect included work from other contributors with a cherry-pick or `Co-authored-by:` trailer.

## Local Review

Draft temporary local issue and review bodies in `.tmp/`.
Move durable decisions to `docs/exec-plans/` or the project tracker.

## Evidence

Record evidence in the execution plan or review note:

- validation command and result
- test names or CI job names
- manual QA action and observed output
- review findings or approval record
- unresolved blockers with owner
