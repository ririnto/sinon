# Workflow

Use this file for Git flow, review records, validation gates, and local publication policy.
Repository contract stays in `AGENTS.md`, architecture in `ARCHITECTURE.md`, and durable evidence in `docs/**`.
Use task prompts for implementation-only decisions.

## Work Loop

1. Explore the contract, relevant docs, code surface, validation command, and local review record.
2. Plan the files, acceptance gate, manual QA, validation, and publication target.
3. Implement the smallest target-owned change.
4. Review correctness, security, contract drift, and missing evidence.
5. Validate with the selected stack command and active Git hooks.
6. Publish with the repository-approved local review or merge policy.

## Records

A milestone groups planned work.
Record milestone, issue, and review-request relationships in the execution plan while local policy applies.
Keep the planned closing relationship explicit.

```text
Closes #00
```

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

## Local Review

Draft issue, review, and release notes in `.tmp/`.
Move durable decisions to `docs/exec-plans/` or the project tracker.

## Evidence

Record evidence in the execution plan or review note:

- validation command and result
- test names or CI job names
- manual QA action and observed output
- review findings or approval record
- unresolved blockers with owner
