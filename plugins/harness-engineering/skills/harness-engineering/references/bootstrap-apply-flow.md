# Bootstrap Apply Flow

Open this reference when the target repository is empty, partially scaffolded, or has conflicts that make the common inspect-configure-install path too terse.

## Flow

The OpenAI article starts from an empty git repository and asks Codex to create the initial scaffold: repo structure, CI config, formatting rules, package manager setup, app framework, and a root agent-instruction map. Use that as the default bootstrap basis, then adapt this plugin's local convention: root `CLAUDE.md` is canonical and `AGENTS.md -> CLAUDE.md` is the compatibility surface.

1. Run Stage 0 discovery and create or read `docs/harness-engineering/harness-engineering.json` before copying files.
2. Inventory target paths from config: docs, scripts, CI, hooks, build integrations, source roots, user requirement rules, and agent directories.
3. Produce a dry-run table with `create`, `update`, `skip`, and `conflict` outcomes.
4. Apply only `create` and accepted `update` entries for the current stage.
5. Run `sh scripts/harness/validate_harness.sh`.
6. Record the applied harness stage and exit gate in `docs/harness-engineering/updates.md` or the configured execution plan.

## Dry-run table

```markdown
| Target | Source | Action | Reason |
| --- | --- | --- | --- |
| docs/harness-engineering/harness-engineering.json | assets/config-template.json | create | No config exists |
| CLAUDE.md | assets/claude-md-template.md | create | No root instruction map exists |
| AGENTS.md | symlink | conflict | Existing AGENTS.md has project guidance |
| docs/harness-engineering/guardrails.md | assets/docs-directory-scaffold.md | create | Stage 1 visibility docs |
| docs/harness-engineering/readiness.md | assets/docs-directory-scaffold.md | create | Stage 1 readiness score |
| .github/workflows/harness-checks.yml | assets/github-actions.yml | create | ci.provider is github-actions |
| .githooks/pre-push | assets/git-hooks.md | create | hooks.enabled is true |
```

## Empty repository prompt

```text
Apply the harness-engineering scaffold to this repository.

Use `docs/harness-engineering/harness-engineering.json` as the source of truth. First inspect the repo,
then create a dry-run table. Apply only files that do not conflict with existing
content. Use root `CLAUDE.md` as the canonical map, add `AGENTS.md -> CLAUDE.md`
for AGENTS.md readers, and preserve `.agents/skills -> ../.claude/skills`
when those surfaces are present. Install docs, scripts, CI, hooks, and optional
build integration according to the config. Run the offline validator and report
remaining conflicts.
```

## Conflict rules

- Existing files win until explicitly replaced.
- A symlink is created only when the link path is absent.
- CI provider changes update config first, then create the matching provider file.
- Gradle integration is skipped unless `gradle.enabled` is true, and Node package-script integration is skipped unless `node.enabled` is true.
- Project-specific agents go under `.claude/agents/` and must read `docs/harness-engineering/harness-engineering.json` before applying rules.
- Blocking gates are skipped until `readiness.maturityLevel` reaches `readiness.minimumBlockingLevel`.
- Known violations are recorded in the configured ledger before any `warn`-to-`error` ratchet starts.

## Completion checks

- `docs/harness-engineering/harness-engineering.json` exists and validates.
- `CLAUDE.md` points to configured docs paths rather than embedding all rules.
- `AGENTS.md` resolves to `CLAUDE.md`.
- `.agents/skills` resolves to `.claude/skills` when present.
- Docs, scripts, CI, hooks, build-system files, and user requirement rules match the configured paths.
- `guardrails.md`, `readiness.md`, `known-violations.md`, and `updates.md` exist under `docs/harness-engineering/`.
- The validator output lists no `error` missing paths or malformed config fields.
