## GitHub Addendum

When the base policy selects GitHub for this work item, check `gh auth status` before GitHub operations.
Write issue and pull-request bodies to files before using them.

```sh
gh issue create --title "<title>" --body-file <issue-body>
gh pr create --draft --title "<title>" --body-file <pr-body> --base <target-branch> --head <source-branch>
```

Use `gh issue edit` or `gh pr edit` for an existing record.
Record the resulting GitHub URL in the workflow evidence.
Do not merge or mark ready unless the base completion gate permits the selected GitHub action.
