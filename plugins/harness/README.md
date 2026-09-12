---
description: >-
  Overview of the Harness plugin for shared implementation and review guidance.
---

# Harness

Harness provides two portable skills — `implement` and `review` — that give agents concrete engineering rules for implementation, design, language, testing, and review work.
Both skills reference one canonical rules document, `docs/rules.md`, which owns every rule.
The skills own their procedures.

## Skills

| Skill | Use |
| --- | --- |
| `implement` | Implement a feature, bugfix, refactor, or design change. |
| `review` | Review a diff or completed change for correctness, drift, and rule compliance. |

The skills are host-neutral: they never prescribe a universal instruction filename and instead read the root instruction file the active host actually loads.
Plans belong to GitHub or GitLab issues.
Execution state stays in agent context.

## Package Inventory

- `.claude-plugin/plugin.json`: plugin metadata.
- `skills/implement/SKILL.md`: implementation procedure.
- `skills/review/SKILL.md`: review procedure.
- `docs/rules.md`: canonical implementation, design, frontend, language, and test-quality rules shared by both skills.

## Ownership And Safety

`docs/rules.md` is the single rule source.
A rule stated there is not restated in a skill.
Changing a rule updates the rule and both skill consumers together.
The skills never disable hooks, fake validation success, or treat a skipped gate as a pass: a gate that cannot run stays a named gap.
