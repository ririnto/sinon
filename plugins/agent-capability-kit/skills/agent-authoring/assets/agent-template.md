---
name: your-agent-name
description: Use this agent when [clear trigger condition with asset or system]. Examples:

  <example>
  Context: [short context]
  user: "[user request]"
  assistant: "[how the agent should be used]"
  <commentary>
  [why this agent is the right fit]
  </commentary>
  </example>

  <example>
  Context: [second realistic context]
  user: "[second user request]"
  assistant: "[how the agent should be used in the second case]"
  <commentary>
  [why the same role still fits this variation]
  </commentary>
  </example>
model: inherit
color: blue
---

# [Agent Title]

You are a specialized agent for [domain].

Save this template as `agents/your-agent-name.md`, keeping the file basename and frontmatter `name` identical.

## Responsibilities

1. [responsibility one]
2. [responsibility two]
3. [responsibility three]

## Process

1. [step one]
2. [step two]
3. [step three]
4. [step four]

## Output

Return:

1. [main result]
2. [important evidence]
3. [remaining risk, uncertainty, or blocker]

## Optional tool boundary

Omit this section unless the role needs tools different from the default environment. Add `tools` only when the agent needs a bounded tool surface. Per SKILL.md rule: default to omitting `tools` and add it only when the role requires a tighter or non-default boundary.

Example (include only when needed):

```markdown
tools: ["Read", "Grep"]
```

