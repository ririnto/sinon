---
name: work
description: >-
  Execute a scoped work cycle in an isolated worktree: plan, implement, verify, review, publish, and clean up. Use when acceptance criteria are clear and the change should move through the full branch-to-review lifecycle.
---

# Work

Execute a scoped change from planning through review using the repository contract and an isolated worktree.

## Operating Rules

- Use one issue, one branch, one worktree, and one review request for one logical change.
- Prefer a built-in worktree tool when the active agent runtime provides one; otherwise use `git worktree`.
- Branch from `origin/main`.
- Keep main on main; perform work in the worktree.
- Run the validation command selected by `WORKFLOW.md`.
- Do not bypass repository hooks with `--no-verify`.
- Keep execution plans temporary unless the repository contract says they are durable.

## First Safe Checks

1. Read `AGENTS.md`, `ARCHITECTURE.md`, `WORKFLOW.md`, and relevant `docs/**`.
2. Confirm the owning issue, task, or review request.
3. Confirm the selected validation command.
4. Inspect the current diff against `origin/main`.

    ```sh
    git -C <worktree-path> diff origin/main
    ```

## Procedure

1. Define scope.
   List files to change, files not to change, acceptance criteria, validation commands, and manual QA.
2. Create or confirm the worktree.
   Use a built-in worktree tool when available; otherwise use `git worktree add <worktree-path> -b <branch> origin/main`.
3. Implement the smallest change that satisfies the acceptance criteria.
4. Verify through automated checks.

    ```sh
    <selected-stack-validation-command>
    ```

5. Verify through the user-facing surface when behavior is user-visible.
6. Review the diff for correctness, security, contract drift, and missing evidence.
7. Rebase onto current `origin/main` before merge when other changes may have landed.

    ```sh
    git -C <worktree-path> fetch origin
    git -C <worktree-path> rebase origin/main
    ```

8. Re-run validation after rebase.
9. Publish the branch and create the review request with the host CLI from `WORKFLOW.md`.
10. After merge, remove the worktree and delete the branch when project policy allows.

## Evidence

Record:

- validation command and result
- tests or CI jobs checked
- manual QA action and observed result
- review findings or approval
- unresolved blockers

## Output Contract

Report:

- `scope`: changed behavior and files
- `validation`: commands run and results
- `manual QA`: action and result, or not applicable
- `review`: findings or approval status
- `publication`: branch and review request, or reason not published
