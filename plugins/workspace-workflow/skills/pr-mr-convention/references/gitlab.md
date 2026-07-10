# GitLab MR Publication

Open this reference only after GitLab is selected as the publication host and GitLab-specific template discovery or `glab` commands are needed.

## Template Discovery

Search the repository default branch for Markdown files under `.gitlab/merge_request_templates/`:

```sh
git ls-tree -r --name-only <default-branch> |
  rg '^\.gitlab/merge_request_templates/[^/]+\.md$'
git show <default-branch>:<template-path>
```

Run `git show` only after replacing `<template-path>` with a discovered path.
Project, group, or instance default templates may remain unconfirmed when the CLI or authorization cannot inspect them.

## Read-Only Metadata

```sh
glab auth status
glab mr view <iid>
glab mr list --source-branch "$(git branch --show-current)"
glab label list
```

Use existing MR metadata before remote defaults when resolving the target branch.

## Create or Update

Use a body file and pass its content only after authorization:

```sh
glab mr create --draft --title "<title>" --description "$(cat <body-file>)" --source-branch <source-branch> --target-branch <target-branch> --yes
glab mr update <iid> --title "<title>" --description "$(cat <body-file>)" --yes
```

Add only confirmed metadata:

```sh
glab mr update <iid> --label "<label>" --reviewer "+<login>" --yes
glab mr update <iid> --ready --yes
```

Do not run create, update, ready, merge, or metadata commands without publication authorization.

## GitLab Notes

- Scoped labels use `scope::value` when the repository already follows that taxonomy.
- GitLab quick actions may appear in MR descriptions only when the project uses them and the intended accounts or labels are confirmed.
- Reviewer and assignee update prefixes add or remove accounts; a bare list can replace existing assignments.
- Merge commands belong to the selected merge strategy and top-level publication workflow, not this drafting skill.
