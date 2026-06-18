---
name: agent-frontmatter
description: >-
  Decisions for tools and extra fields.
  Description upgrade patterns when the baseline feels weak.
---

# Agent Frontmatter

Open this file when the ordinary `SKILL.md` rules are not enough to choose between multiple reasonable frontmatter shapes.

## When to open this reference

- the role needs a decision about whether to omit or include `tools`
- the existing file already uses extra supported fields and you need to decide whether to keep them
- the description trigger is still too weak to route reliably

## Field decision notes

### `tools`

Prefer omission when the default environment is already safe and sufficient.

Add `tools` when the role benefits from a narrower explicit boundary, especially for review-only agents or tightly bounded editors.

### Additional supported fields

Keep non-core fields only when they change runtime behavior in a meaningful way.
If a field is decorative, redundant, or host-specific without a clear benefit, leave it out.

## Description upgrade pattern

If the description still feels weak, tighten it in this order:

1. Name the asset or system
2. Name the job to perform
3. Add realistic user wording only if the trigger still needs disambiguation

Upgrade pattern:

Weak:

```markdown
description: Use this agent when docs need work.
```

Stronger:

```markdown
description: Rewrite Markdown guides, READMEs, and handoff notes for structure and clarity. Use this agent when a specific documentation file needs bounded editing before release, review, or handoff.
```
