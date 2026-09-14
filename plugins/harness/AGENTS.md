# Repository Guidelines

Harness provides implementation, review, and target-repository installation guidance.

## Component Boundary

- `skills/implement/SKILL.md` and `skills/review/SKILL.md` route their tasks to the shared rules and relevant references.
- `skills/harness-install/SKILL.md` owns target materialization, path remapping, conflict handling, and installation proof.
- `docs/rules.md` owns common engineering rules, workflow boundaries, and the language and tool index.
- `docs/languages/` owns language-specific rules; `docs/tools/` owns profile-specific configuration, checks, CI guidance, and limitations.
- `tooling/` contains native configuration fragments and ruleset sources; `ci/` contains optional profile catalogs.

Keep each rule in its owning document.
Update consumers when their routing or contract changes, not merely because a referenced rule changes.
Skill bodies discover the instruction files selected by the active host rather than prescribing a universal filename.
The plugin ships no agents, settings adapters, hooks, or asset bundles.
Do not add legacy compatibility surfaces, scratch plan files, or bundled target templates.

## Validation

Run `claude plugin validate plugins/harness` after changing this package.
Keep skill frontmatter valid for the host loader.
For routing changes, check published links and the installer's remapped regular files.
Run native profile checks when their implementation or integration changes, not for unrelated guidance edits.
