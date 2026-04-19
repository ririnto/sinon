# Agent Frontmatter Patterns

Use these patterns when you need copyable frontmatter examples for common agent shapes. Adapt the role wording, examples, and tool boundary to the actual job.

## Read-only analysis agent

```markdown
---
name: schema-reviewer
description: Use this agent when a schema, contract, or config file needs focused review. Examples:

  <example>
  Context: API contract review before release
  user: "Review this OpenAPI file for consistency and missing fields"
  assistant: "I'll use the schema-reviewer agent to inspect the file closely."
  <commentary>
  The task is narrow, analytical, and does not require edits.
  </commentary>
  </example>

  <example>
  Context: deployment configuration audit
  user: "Check this config file for risky defaults and missing required values"
  assistant: "I'll use the schema-reviewer agent to review the configuration file."
  <commentary>
  The request is still a bounded review with a read-only outcome.
  </commentary>
  </example>
model: inherit
color: cyan
tools: ["Read", "Grep"]
---
```

## Editing agent

```markdown
---
name: docs-refiner
description: Use this agent when a documentation file needs direct rewriting with consistent structure and tone. Examples:

  <example>
  Context: markdown cleanup before release
  user: "Rewrite this README to make the structure clearer"
  assistant: "I'll use the docs-refiner agent to rewrite the file directly."
  <commentary>
  The task needs direct editing and a bounded writing workflow.
  </commentary>
  </example>

  <example>
  Context: handoff note cleanup after implementation
  user: "Refactor this operations note so the steps are easier to follow"
  assistant: "I'll use the docs-refiner agent to revise the document directly."
  <commentary>
  The role is still a focused documentation rewrite rather than a general research task.
  </commentary>
  </example>
model: inherit
color: green
tools: ["Read", "Write"]
---
```
