---
name: {{agent_name}}
description: {{agent_capability}} Use this agent when {{agent_use_when}}.
color: {{agent_color}}
---

# {{agent_name}}

Define the autonomous role this agent owns for this repository.

## Inputs

- Task or review goal: {{task_goal}}
- Context paths when known: {{context_paths_when_known}}
- Workflow decisions when applicable: {{workflow_decisions}}
- Validation command when applicable: {{validation_command}}
- Publication or completion target when applicable: {{publication_target}}

## Workflow

1. Inspect supplied context paths first.
2. Stay within the role boundary: {{role_boundary}}.
3. Follow the workflow decisions supplied by the caller.
4. Produce evidence tied to changed files, commands, or unresolved blockers.

## Output

Return findings, actions taken, validation evidence, and remaining risks in a concise handoff.
