---
name: start-worktree
description: >-
  Create an isolated worktree for a new issue or scoped change. Prefer built-in worktree tools when available; otherwise use git worktree commands from origin/main.
---

# Start Worktree

Create an isolated worktree for a scoped change.
Keep the issue, branch, worktree, and review request aligned.

## First Safe Checks

1. Read `AGENTS.md` and `WORKFLOW.md`.
2. Identify the issue number or local task that owns the change.
3. Choose a branch name using `WORKFLOW.md`.
4. Confirm whether the active agent runtime has a built-in worktree tool.

## Procedure

1. Prefer the built-in worktree tool when the active agent runtime provides one.
2. If no built-in tool is available, fetch the current remote base.

    ```sh
    git fetch origin
    ```

3. Create the worktree from `origin/main`.

    ```sh
    git worktree add <worktree-path> -b <type>/<short-description> origin/main
    ```

4. Verify the worktree and branch.

    ```sh
    git worktree list
    git -C <worktree-path> branch --show-current
    ```

5. Run all later git commands inside the worktree.

    ```sh
    git -C <worktree-path> status
    ```

## Worktree Rules

- Branch from `origin/main`, not a stale local branch.
- Keep main on main; perform scoped work in the worktree.
- Use one issue, one branch, one worktree, and one review request for one logical change.
- Use `git -C <worktree-path> ...` or enter the worktree before running git commands.
- Keep worktree paths local and ignored.
- Do not bypass repository hooks with `--no-verify`.

## Output Contract

Report:

- `worktree`: path created or selected
- `branch`: branch created or selected
- `base`: remote base used
- `verification`: command output or reason verification was not run
