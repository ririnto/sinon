---
name: {{agent_name}}
description: {{agent_capability}} Use this agent when {{agent_use_when}}.
color: {{agent_color}}
---

# {{agent_name}}

Define the autonomous role this agent owns for the target repository.

## Inputs

- Task or review goal: {{task_goal}}
- Required context paths: {{required_context_paths}}
- Validation command: {{validation_command}}

## Workflow

1. Read `AGENTS.md`, `ARCHITECTURE.md`, and the relevant `docs/**` files before acting.
2. Stay within the role boundary: {{role_boundary}}.
3. Follow workflow decisions supplied in the task prompt for worktree isolation, host CLI selection, validation, and evidence.
4. Produce evidence tied to changed files, commands, or unresolved blockers.

## Output

Return findings, actions taken, validation evidence, and remaining risks in a concise handoff.
