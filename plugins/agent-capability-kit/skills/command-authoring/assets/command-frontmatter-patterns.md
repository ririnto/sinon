# Command Frontmatter Patterns

Copy one of these patterns when the ordinary guidance in `../SKILL.md` is already clear and you only need a starter block.

## Minimal review command

```markdown
---
description: Review one Markdown file for structure, clarity, and missing sections. Use when a document needs a fast editorial quality pass.
argument-hint: [file-path]
allowed-tools: Read, Grep
---
```

## Manual command with arguments

```markdown
---
description: Review one pull request for risk, missing tests, and merge blockers. Use when preparing a merge recommendation.
argument-hint: [pr-number]
disable-model-invocation: true
allowed-tools: Read, Grep, Bash(git *)
---
```

## Hidden helper command

```markdown
---
description: Gather repository context for a later summarization step. Use when a hidden preparation command is needed before a visible review command runs.
user-invocable: false
allowed-tools: Read, Grep
---
```

## Path-scoped command

```markdown
---
description: Review database migration files for locking and rollback safety. Use when changes are limited to SQL migrations.
paths:
  - "db/migrations/**/*.sql"
allowed-tools: Read, Grep
---
```
