## GitLab Addendum

When the base policy selects GitLab for this work item, check `glab auth status` before GitLab operations.
Write issue and merge-request bodies to files before using them.

```sh
glab api --method POST projects/:fullpath/issues --field title="<title>" --field description=@<issue-body>
glab mr create --draft --title "<title>" --description "$(cat <mr-body>)" --source-branch <source-branch> --target-branch <target-branch> --yes
```

Use `glab api --method PUT` or `glab mr update` for an existing record.
Record the resulting GitLab URL in the workflow evidence.
Do not merge or mark ready unless the base completion gate permits the selected GitLab action.
