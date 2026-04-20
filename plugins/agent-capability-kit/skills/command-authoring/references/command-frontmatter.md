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

Copyable frontmatter examples for all four common patterns (minimal review, manual with arguments, hidden helper, path-scoped) are in `assets/command-frontmatter-patterns.md`. Use that file when you need a ready-to-copy starter block.

This reference covers decision guidance and one pattern not included in the asset file:

### Shell-sensitive command

```markdown
---
description: Generate a shell snippet that assumes zsh array handling. Use when the command output must match zsh semantics.
shell: zsh
---
```

The `shell` field is needed only when the command semantics depend on a specific shell implementation. Omit it for commands that work correctly under the default host shell.
