---
description: >-
  Overview of the Harness plugin for shared implementation, review, and target-repository installation guidance.
---

# Harness

Harness provides three skills: `implement`, `review`, and `harness-install`.
The first two give agents concrete engineering rules for implementation, design, language, testing, and review work.
The installer materializes the implementation and review skills and their canonical documentation in a target repository.
The skills own their procedures, and the package-local documents own their rules.
The installed skills are project-local copies derived from the current canonical files.

## Skills

| Skill | Use |
| --- | --- |
| `implement` | Implement a feature, bugfix, refactor, or design change. |
| `review` | Review a diff or completed change for correctness, drift, and rule compliance. |
| `harness-install` | Install the implement and review skills and their current documentation into a target repository. |

The skills are host-neutral: they never prescribe a universal instruction filename and instead read the root instruction file the active host actually loads.
Plans are self-contained and do not depend on an external tracker.
Execution state stays in agent context.

## Package Inventory

- `.claude-plugin/plugin.json`: plugin metadata.
- `skills/implement/SKILL.md`: implementation procedure.
- `skills/review/SKILL.md`: review procedure.
- `skills/harness-install/SKILL.md`: target-repository installation procedure.
- `docs/rules.md`: canonical implementation, design, frontend, and test-quality rules shared by both skills, plus the index of language and tool documents.
- `docs/languages/`: per-language rules for Java, Kotlin, TypeScript, JavaScript, Python, Go, Rust, and shell.
- `docs/tools/`: profile-specific detection, native configuration, merge, command, CI, and limitation guidance.
- `ci/`: optional GitHub Actions and GitLab CI catalogs for the seven supported profiles.
- `tooling/`: native configuration fragments, the standalone Kotlin ruleset module, and profile-local rule plugins.

## Ownership And Safety

`docs/rules.md` is the common rule source.
Language documents own language-specific rules, and tool documents own profile-specific integration guidance.
A rule stated in a canonical document is not restated in a skill.
Changing a rule updates its document and both consumer skills together.
The installer reads the current canonical files, materializes selected language and tool guidance, and writes only explicitly mapped target paths.
For Kotlin, it also materializes the complete target-owned `tooling/kotlin-ktlint/` Gradle module when the Kotlin profile is selected.
The target must attach its produced JAR to the actual ktlint runtime through `ktlintRuleset(...)`; buildSrc classes alone are not sufficient.
The skills never disable hooks, fake validation success, or treat a skipped gate as a pass: a gate that cannot run stays a named gap.
