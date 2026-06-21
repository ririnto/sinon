# Workflow

Use this file for Git flow, review records, validation gates, and Git-contained integration.
Repository contract stays in `AGENTS.md`, architecture in `ARCHITECTURE.md`, and durable evidence in `docs/**`.
Use task prompts for implementation-only decisions.

## Work Loop

`WORKFLOW.md` is the source of truth for validation command selection and Git-contained integration policy.

| Phase | Action |
| --- | --- |
| Intake | Identify the local task, change description, or execution plan that owns the change. |
| Explore | Inspect the contract, relevant docs, code surface, validation command, and local Git context. |
| Plan | Define the files, acceptance gate, manual QA, validation, and integration target. |
| Implement | Make the smallest target-owned change that satisfies the plan. |
| Review | Check correctness, security, contract drift, and missing evidence. |
| Validate | Run the selected stack command and active Git hooks. |
| Integrate | Apply the repository-approved Git integration method after complete gates. |

## Execution Discipline

- Track non-trivial work with a visible task or todo list before editing.
- Keep exactly one task in progress, and mark completed tasks as soon as their validation passes.
- When scope changes, update the task list before continuing.
- Ask only for owner decisions that repository evidence cannot resolve.
- Delegate with a scoped prompt that names deliverable, files or domain, constraints, and verification.
- Do not declare completion until review evidence, validation output, and manual QA are recorded or explicitly marked not applicable.
- When validation fails, fix in the worktree and rerun the same gate before publishing.

## Records

Record the task context, acceptance criteria, and intended integration in the execution plan.
Keep the change relationship explicit in Git history.

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

## Git-Contained Review

Keep the task context and change description in versioned project files.
Record the branch or commit range, review findings, validation, manual QA, and integration decision.
Integrate only after approval and final validation.

## Evidence

Record evidence in the execution plan, change description, or review note:

- validation command and result
- test names or CI job names
- manual QA action and observed output
- review findings or approval record
- unresolved blockers with owner
