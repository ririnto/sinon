# Workflow

Use this file for Git flow, review records, validation gates, and GitHub publication.
Repository contract stays in `AGENTS.md`, architecture in `ARCHITECTURE.md`, and durable evidence in `docs/**`.
Use task prompts for implementation-only decisions.

## Work Loop

1. Explore the contract, relevant docs, code surface, validation command, and issue record.
2. Plan the files, acceptance gate, manual QA, validation, and publication target.
3. Implement the smallest target-owned change.
4. Review correctness, security, contract drift, and missing evidence.
5. Validate with the selected stack command and active Git hooks.
6. Publish with `gh` when a pull request is needed.

## Records

A milestone groups planned work.
Keep the issue and completing pull request on the same milestone.
Use supporting pull requests for preparatory or follow-up work.
Put the closing keyword in the completing pull request body.

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

## GitHub CLI

Draft issue and pull request bodies in `.tmp/`.
Pass the draft file with `--body-file`.

```sh
mkdir -p .tmp
$EDITOR .tmp/issue.md
gh issue create --title "<title>" --milestone "<milestone>" --body-file .tmp/issue.md
$EDITOR .tmp/review.md
gh pr create --draft --title "<title>" --milestone "<milestone>" --body-file .tmp/review.md --base main --head "$(git branch --show-current)"
```

Merge after approval and final validation.

```sh
gh pr merge --squash <pr-number>
```

## Evidence

Record evidence in the issue, pull request body, execution plan, or review note:

- validation command and result
- test names or CI job names
- manual QA action and observed output
- review findings or approval record
- unresolved blockers with owner
