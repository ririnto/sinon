# Workflow

Use this file for Git flow, review records, validation gates, and GitLab publication.
Repository contract stays in `AGENTS.md`, architecture in `ARCHITECTURE.md`, and durable evidence in `docs/**`.
Use task prompts for implementation-only decisions.

## Work Loop

1. Explore the contract, relevant docs, code surface, validation command, and issue record.
2. Plan the files, acceptance gate, manual QA, validation, and publication target.
3. Implement the smallest target-owned change.
4. Review correctness, security, contract drift, and missing evidence.
5. Validate with the selected stack command and active Git hooks.
6. Publish with `glab` when a merge request is needed.

## Records

A milestone groups planned work.
Keep the issue and completing merge request on the same milestone.
Use supporting merge requests for preparatory or follow-up work.
Put the closing keyword in the completing merge request body.

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

## GitLab CLI

Draft issue and merge request bodies in `.tmp/`.
Pass body files through `glab api --field description=@<file>`.
Use the milestone ID for API publication.

```sh
mkdir -p .tmp
$EDITOR .tmp/issue.md
glab api --method POST projects/:fullpath/issues --field title="<title>" --field milestone_id="<milestone-id>" --field description=@.tmp/issue.md
$EDITOR .tmp/review.md
glab api --method POST projects/:fullpath/merge_requests --field title="Draft: <title>" --field source_branch="$(git branch --show-current)" --field target_branch=main --field milestone_id="<milestone-id>" --field description=@.tmp/review.md
```

Merge after approval and final validation.

```sh
glab mr merge <mr-iid> --squash --yes
```

## Evidence

Record evidence in the issue, merge request body, execution plan, or review note:

- validation command and result
- test names or CI job names
- manual QA action and observed output
- review findings or approval record
- unresolved blockers with owner
