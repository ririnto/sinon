# GitHub PR Publication

Open this reference only after GitHub is selected as the publication host and GitHub-specific template discovery or `gh` commands are needed.

## Template Discovery

Search the repository default branch for:

- `pull_request_template.md` at root, `docs/`, or `.github/`
- Markdown files under root, `docs/`, or `.github/PULL_REQUEST_TEMPLATE/`

```sh
git ls-tree -r --name-only <default-branch> |
  rg -i '^(((docs|\.github)/)?pull_request_template\.md|((docs|\.github)/)?PULL_REQUEST_TEMPLATE/[^/]+\.md)$'
git show <default-branch>:<template-path>
```

Run `git show` only after replacing `<template-path>` with a discovered path.
GitHub organization-level community health defaults may remain unconfirmed when the CLI or authorization cannot inspect them.

## Read-Only Metadata

```sh
gh auth status
gh pr view <number> --json baseRefName,headRefName,isDraft,title,url
gh pr list --head "$(git branch --show-current)" --json number,baseRefName,isDraft,title,url
gh label list
```

Use existing PR metadata before remote defaults when resolving the target branch.

## Create or Update

Write the body to a file so shell quoting cannot alter Markdown:

```sh
gh pr create --draft --title "<title>" --body-file <body-file> --base <target-branch> --head <source-branch>
gh pr edit <number> --title "<title>" --body-file <body-file>
```

Add only confirmed metadata:

```sh
gh pr edit <number> --add-label "<label>" --add-reviewer <login>
gh pr ready <number>
```

Do not run create, edit, ready, merge, or metadata commands without publication authorization.

## GitHub Notes

- GitHub-flavored alerts are allowed when they materially improve risk visibility.
- Reviewer and assignee logins are bare names; `@me` is supported for self-assignment.
- Use repository labels exactly as returned by `gh label list`.
- Merge commands belong to the selected merge strategy and top-level publication workflow, not this drafting skill.
