# Repository Guidelines

Harness contains the shared implementation and review skills and their canonical rules document.

## Component Boundary

- `skills/implement/SKILL.md` and `skills/review/SKILL.md` are the only skills.
  The plugin ships no agents, settings adapters, hooks, or asset bundles.
- `docs/rules.md` owns every implementation, design, frontend, language, and test-quality rule.
- Each skill owns only its procedure and references `docs/rules.md` for the rules.
  A rule appears in exactly one file.
- Changing a rule updates `docs/rules.md` and both skill consumers in the same change.
- Skill bodies stay host-neutral: they discover the target repository's actual root instruction file per active host and never prescribe a universal filename.

## Change Discipline

Make the smallest change that satisfies the acceptance criteria and preserve unrelated work.
Plans belong to GitHub or GitLab issues.
Execution handoffs stay in agent context.
Do not add legacy compatibility surfaces, scratch plan files, or bundled target templates.

## Validation

Run `claude plugin validate plugins/harness` after changing this package.
Keep skill frontmatter valid and machine-consumable for the host skill loader.
