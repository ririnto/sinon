# Description Patterns

Use these patterns when drafting or repairing a skill `description`.

## Recommended formula

```text
[Imperative capability]. Use when [distinct task, inputs, systems, file types, or user intent keywords not already covered].
```

Drop the trigger clause when the capability statement already contains enough routing vocabulary.

## Strong examples

| Pattern | Example |
| --- | --- |
| Artifact driven | Draft release automation runbooks and rollback notes. Use when preparing deployment procedures, CI release steps, or operational handoff docs. |
| File-type driven | Review Markdown documents for structure, headings, and missing sections. |
| Tooling driven | Build strict JSON schemas and validation notes for tool-calling workflows. Use when creating agent-facing APIs or config-driven automation. |
| Workflow driven | Prepare release candidates by updating versions, drafting notes, and validating deploy prerequisites. Use when cutting release branches or preparing tags. |

## Weak examples

| Weak text | Problem |
| --- | --- |
| Helps with SQL. | No concrete capability or trigger conditions. |
| API helper. | Too broad and not imperative. |
| Review skill. | Missing artifact type, quality dimension, and target workflow. |
| Draft API and product documentation. | Several adjacent jobs unless the skill truly covers both ordinary paths. |

## Repair checklist

1. Start with the actual job in imperative form.
2. Add concrete nouns users are likely to mention.
3. Add `Use when ...` only if it introduces new trigger vocabulary.
4. Remove host names, team jargon, and adjacent jobs unless they define the skill.
5. Test the description against three intended prompts and three nearby prompts that should not match.
