# Command Frontmatter Reference

Open this file when the command needs a less common frontmatter choice or when several optional fields may overlap.

## Purpose

Use this reference to compare frontmatter patterns after the ordinary rules in `../SKILL.md` are no longer enough.

## When to open this file

- You are deciding between several optional fields.
- You need a path-scoped or hidden-helper command pattern.
- You need to justify a runtime-specific field such as `model` or `shell`.

## Comparison notes

| Field | Open this reference when | Default if not needed |
| --- | --- | --- |
| `argument-hint` | the argument shape is non-trivial or partly optional | omit it |
| `allowed-tools` | tool scope needs explicit narrowing | omit it |
| `disable-model-invocation` | the command should not run through the normal invocation path | omit it |
| `user-invocable` | the command should stay hidden from direct user invocation | leave default behavior |
| `paths` | activation should be limited to file globs or directories | omit it |
| `model` | behavior materially depends on a model choice | omit it |
| `shell` | shell semantics change how the command should be interpreted | omit it |

## Less common examples

### Hidden helper command

```markdown
---
description: Gather repository context for a later summarization step. Use when a hidden preparation command is needed before a visible review command runs.
user-invocable: false
allowed-tools: Read, Grep
---
```

### Path-scoped command

```markdown
---
description: Review database migration files for locking and rollback safety. Use when changes are limited to SQL migrations.
paths:
  - "db/migrations/**/*.sql"
allowed-tools: Read, Grep
---
```

### Shell-sensitive command

```markdown
---
description: Generate a shell snippet that assumes zsh array handling. Use when the command output must match zsh semantics.
shell: zsh
---
```
