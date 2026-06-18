# Workflow

Use this file for Git flow, review records, validation gates, and Git-contained integration.
Repository contract stays in `AGENTS.md`, architecture in `ARCHITECTURE.md`, and durable evidence in `docs/**`.
Use task prompts for implementation-only decisions.

## Work Loop

1. Explore the contract, relevant docs, code surface, validation command, and task context.
2. Plan the files, acceptance gate, manual QA, validation, and publication target.
3. Implement the smallest target-owned change.
4. Review correctness, security, contract drift, and missing evidence.
5. Validate with the selected stack command and active Git hooks.
6. Integrate with the repository-approved Git method after complete gates.

## Records

Record the task context, acceptance criteria, and intended integration in the execution plan.
Keep the change relationship explicit in Git history.

## Git

Branch: `<type>/<short-description>`.
Worktree:

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
