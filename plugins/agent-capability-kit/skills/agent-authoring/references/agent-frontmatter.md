# Agent Frontmatter

Open this file when the ordinary `SKILL.md` rules are not enough to choose between multiple reasonable frontmatter shapes.

## When to open this reference

- the role needs a decision about whether to omit or include `tools`
- `model: inherit` might not be enough for the role
- the existing file already uses extra supported fields and you need to decide whether to keep them
- the description examples are structurally correct but still too weak to trigger reliably

## Field decision notes

### `tools`

Prefer omission when the default environment is already safe and sufficient.

Add `tools` when the role benefits from a narrower explicit boundary, especially for review-only agents or tightly bounded editors.

### `model`

Keep `model: inherit` unless the role has a durable reason to use a different model every time. Avoid changing `model` for one temporary task.

### Additional supported fields

Keep non-core fields only when they change runtime behavior in a meaningful way. If a field is decorative, redundant, or host-specific without a clear benefit, leave it out.

## Description upgrade pattern

If the description still feels weak, tighten it in this order:

1. name the asset or system
2. name the job to perform
3. add realistic user wording
4. add commentary that explains why the role fits

Example upgrade:

Weak:

```markdown
description: Use this agent when docs need work.
```

Stronger:

```markdown
description: Use this agent when a Markdown guide, README, or handoff note needs direct rewriting for structure and clarity. Examples:

  <example>
  Context: release documentation cleanup
  user: "Rewrite this README so the setup steps are easier to follow"
  assistant: "I'll use the docs-refiner agent to rewrite the file directly."
  <commentary>
  The request needs bounded editing with a clear documentation outcome.
  </commentary>
  </example>
```
