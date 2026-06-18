---
name: {{skill_name}}
description: {{skill_capability}} Use this skill when {{skill_use_when}}.
---

# {{skill_name}}

Execute the focused repository procedure for {{skill_scope}}.

## Inputs

- Goal: {{task_goal}}
- Required context paths: {{required_context_paths}}
- Validation command: {{validation_command}}

## Workflow

1. Inspect `AGENTS.md`, `ARCHITECTURE.md`, and the required context before editing.
2. Apply the smallest target-owned change that satisfies the goal.
3. Follow the repository workflow for worktree isolation, host CLI selection, validation, and evidence.
4. Run the validation command and capture evidence.

## Output

Report changed files, validation results, assumptions, and remaining risks.
