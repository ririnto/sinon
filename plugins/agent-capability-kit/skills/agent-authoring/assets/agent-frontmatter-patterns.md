# Agent Frontmatter Patterns

Use these patterns when you need copyable frontmatter examples for common agent shapes. Adapt the role wording, examples, and tool boundary to the actual job.

## Read-only analysis agent

See the Minimal example in `SKILL.md` for a complete read-only analysis shape (`schema-reviewer`). The pattern below shows only the frontmatter differences for a read-only role:

```yaml
model: inherit
color: cyan
tools: ["Read", "Grep"]
```

Key traits: narrow inspection scope, no mutation tools, `description` starts with `Use this agent when...` and includes concrete `<example>` blocks showing trigger conditions.

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

## Multi-file refactor agent

```markdown
---
name: dependency-updater
description: Use this agent when dependency versions, import paths, or API signatures need coordinated updates across multiple files. Examples:

  <example>
  Context: library version bump after security advisory
  user: "Update all lodash imports from v3 to v4 across the codebase"
  assistant: "I'll use the dependency-updater agent to trace and update every import path."
  <commentary>
  The task spans multiple files but the changes are mechanical and bounded to import statements.
  </commentary>
  </example>

  <example>
  Context: module rename after package restructure
  user: "Rename all references from utils/helpers to utils/internal in src/"
  assistant: "I'll use the dependency-updater agent to find and update every reference."
  <commentary>
  The role requires both broad read access (to find all references) and targeted mutation (to update each one).
  </commentary>
  </example>
model: inherit
color: yellow
tools: ["Read", "Write", "Edit", "Grep"]
---
```

