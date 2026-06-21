# Workflow

Use this file for Git flow, review records, validation gates, and host publication.
Repository contract stays in `AGENTS.md`, architecture in `ARCHITECTURE.md`, and durable evidence in `docs/**`.
Use task prompts for implementation-only decisions.

## Work Loop

`WORKFLOW.md` is the source of truth for validation command selection and host publication policy.

| Phase | Action |
| --- | --- |
| Intake | Identify the issue, review request, local task, or execution plan that owns the change. |
| Explore | Inspect the contract, relevant docs, code surface, validation command, and host record. |
| Plan | Define the files, acceptance gate, manual QA, validation, and publication target. |
| Implement | Make the smallest target-owned change that satisfies the plan. |
| Review | Check correctness, security, contract drift, and missing evidence. |
| Validate | Run the selected stack command and active Git hooks. |
| Publish | Use the selected host CLI when a review request is needed. |

## Execution Discipline

- Track non-trivial work with a visible task or todo list before editing.
- Keep exactly one task in progress, and mark completed tasks as soon as their validation passes.
- When scope changes, update the task list before continuing.
- Ask only for owner decisions that repository evidence cannot resolve.
- Delegate with a scoped prompt that names deliverable, files or domain, constraints, and verification.
- Do not declare completion until review evidence, validation output, and manual QA are recorded or explicitly marked not applicable.
- When validation fails, fix in the worktree and rerun the same gate before publishing.

## Records

A milestone groups planned work.
Keep the issue and completing review request on the same milestone when the host supports it.
Use supporting review requests for preparatory or follow-up work.
Put the closing keyword in the completing review request body.

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

## Host CLI

Use the CLI that matches the Git host selected during harness installation.

| Installed CI host | CLI |
| --- | --- |
| GitHub | `gh` |
| GitLab | `glab` |
| Both | Use the CLI for the host that owns the issue or review. |
| None | Use the repository-approved local review or merge policy until project policy adds a Git host. |

Draft issue, pull request, and merge request bodies in `.tmp/`.
Pass the draft to the host CLI.

GitHub:

```sh
mkdir -p .tmp
$EDITOR .tmp/issue.md
gh issue create --title "<title>" --milestone "<milestone>" --body-file .tmp/issue.md
$EDITOR .tmp/review.md
gh pr create --draft --title "<title>" --milestone "<milestone>" --body-file .tmp/review.md --base main --head "$(git branch --show-current)"
```

GitLab:

```sh
mkdir -p .tmp
$EDITOR .tmp/issue.md
glab api --method POST projects/:fullpath/issues --field title="<title>" --field milestone_id="<milestone-id>" --field description=@.tmp/issue.md
$EDITOR .tmp/review.md
glab api --method POST projects/:fullpath/merge_requests --field title="Draft: <title>" --field source_branch="$(git branch --show-current)" --field target_branch=main --field milestone_id="<milestone-id>" --field description=@.tmp/review.md
```

Merge after approval and final validation.

```sh
gh pr merge --squash <pr-number>
glab mr merge <mr-iid> --squash --yes
```

## Evidence

Record evidence in the issue, review request body, execution plan, or review note:

- validation command and result
- test names or CI job names
- manual QA action and observed output
- review findings or approval record
- unresolved blockers with owner
