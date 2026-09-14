# Repository Guidelines

Harness contains the shared implementation and review skills, a target-repository installer, and their canonical rules documents.

## Component Boundary

- `skills/implement/SKILL.md`, `skills/review/SKILL.md`, and `skills/harness-install/SKILL.md` are the skills.
  The plugin ships no agents, settings adapters, hooks, or asset bundles.
- `docs/rules.md` owns every implementation, design, frontend, and test-quality rule, and indexes the language and tool documents.
- `docs/languages/` owns per-language rules, and `docs/tools/` owns profile-specific tool and CI guidance.
- Each consumer skill owns only its procedure and references the canonical rules documents.
  The installer owns only target materialization and verification.
  A rule appears in exactly one file.
- Changing a rule updates `docs/rules.md` and both skill consumers in the same change.
- Skill bodies stay host-neutral: they discover the target repository's actual root instruction file per active host and never prescribe a universal filename.

## Change Discipline

Make the smallest change that satisfies the acceptance criteria and preserve unrelated work.
Start from the task, keep a self-contained plan, implement it, run appropriate native proof, review the proportional diff, and integrate with Git.
Keep the plan in agent context unless the target repository defines an approved planning surface.
Execution handoffs stay in agent context.
Do not add legacy compatibility surfaces, scratch plan files, or bundled target templates.

## Validation

Run `claude plugin validate plugins/harness` after changing this package.
Keep skill frontmatter valid and machine-consumable for the host skill loader.
