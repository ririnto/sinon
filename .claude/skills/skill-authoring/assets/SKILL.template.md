---
name: __SKILL_NAME__
description: REPLACE WITH 1-2 SENTENCES STATING WHAT THE SKILL DOES AND WHEN TO USE IT. Start with an imperative "Use this skill when...". Enumerate triggering contexts, including paraphrases that do not mention the domain explicitly. Close with a "Do NOT use..." clause if adjacent skills or tools could be confused with this one. Stay under 1024 characters.
---

# __SKILL_NAME__

## Overview

One paragraph summarizing the skill's purpose and the kind of task it handles. Ground the summary in the real artifact this skill is replacing (a runbook, prompt, workflow, etc.), not in generic LLM knowledge.

## When to use this skill

- Positive trigger 1: concrete user intent or artifact.
- Positive trigger 2: paraphrase that should still activate the skill.
- Negative case 1: adjacent task that should NOT activate this skill (name the correct alternative if one exists).

## Workflow

Describe the steps the agent should take in order. Be prescriptive when consistency or correctness matters; give the agent freedom when multiple approaches are valid and explain *why* rather than dictating.

1. First step — state the command, script, or decision.
2. Second step — reference any bundled artifact by relative path.
3. Third step — validate the result before moving on.

## Gotchas

Environment-specific facts that defy reasonable assumptions. These are often the highest-value content in a skill. Examples:

- The `X` endpoint returns 200 even on auth failure — check `Y` for real status.
- The `foo` field is called `bar` in the database and `Baz` in the public API.
- Retries must wait at least 5 seconds or the rate limiter will lock the account for an hour.

Add a new bullet whenever you have to correct the agent's behavior while iterating.

## Examples

Minimal input/output pairs that illustrate the common cases. Short examples in the body; longer examples in `assets/` or `references/` with an explicit load trigger.

## References

Load these on demand, not upfront:

- `references/DETAILS.md` — detailed technical reference. Load when the workflow above is insufficient.
- `assets/template.md` — output template. Load when producing formatted deliverables.

## Output contract

State what the agent should return to the user at the end: format, required fields, verification steps.
