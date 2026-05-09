# Bootstrap Apply Flow

Open this reference when the target repository is empty, partially scaffolded, or has conflicts that make the common inspect-configure-install path too terse.

## Flow

The OpenAI article starts from an empty git repository and uses an agent to create the initial scaffold: repo structure, CI config, formatting rules, package manager setup, app framework, and a root agent-instruction map. Use that as the knowledge-base bootstrap basis, then add this plugin's local conventions: `docs/harness-engineering/harness-engineering.json` is the docs-adjacent machine-readable config, root `CLAUDE.md` is canonical, and `AGENTS.md -> CLAUDE.md` is the compatibility surface.

1. Run Stage 0 discovery and create or read `docs/harness-engineering/harness-engineering.json` before copying files.
2. Inventory target paths from config: docs, scripts, CI, hooks, and agent directories. Inventory source roots and project-specific requirement rules from `ARCHITECTURE.md` or guardrail docs.
3. Produce a dry-run table with `create`, `update`, `skip`, and `conflict` outcomes.
4. Apply only `create` and accepted `update` entries for the current stage.
5. Run `sh scripts/harness/validate_harness.sh`.
6. Record the applied harness stage and exit gate in `docs/harness-engineering/updates.md` or the configured execution plan.

## Dry-run table

```markdown
| Target | Source | Action | Reason |
| --- | --- | --- | --- |
| docs/harness-engineering/harness-engineering.json | assets/config.json.template | create | No config exists |
| CLAUDE.md | assets/CLAUDE.md.template | create | No root instruction map exists |
| AGENTS.md | symlink | conflict | Existing AGENTS.md has project guidance |
| docs/harness-engineering/guardrails.md | assets/docs-directory-scaffold.md.template | create | Stage 1 visibility docs |
| docs/harness-engineering/readiness.md | assets/docs-directory-scaffold.md.template | create | Stage 1 readiness score |
| .github/workflows/harness-checks.yml | assets/github-actions.yml.template | create | gates.ci.provider is github-actions |
| .githooks/pre-push | assets/git-hooks.md.template | create | gates.hooks.enabled is true |
```

## Empty repository prompt

```text
Apply the harness-engineering scaffold to this repository.

Use `docs/harness-engineering/harness-engineering.json` as this plugin's config source of truth. First inspect the repo,
then create a dry-run table. Apply only files that do not conflict with existing
content. Use root `CLAUDE.md` as the canonical map, add `AGENTS.md -> CLAUDE.md`
for AGENTS.md readers, and preserve `.agents/skills -> ../.claude/skills`
when those surfaces are present. Install docs, scripts, CI, and hooks according to the config. Run the offline validator and report remaining conflicts.
```

## Conflict rules

- Existing files win until explicitly replaced.
- A symlink is created only when the link path is absent.
- CI provider changes update config first, then create the matching provider file.
- Build-tool-specific integration is outside this core scaffold unless a separate integration pack owns its files and trusted commands.
- Project-specific agents go under `.claude/agents/` and must read `docs/harness-engineering/harness-engineering.json` before applying rules.
- Blocking gates are skipped until `readiness.maturityLevel` reaches `readiness.minimumBlockingLevel`.
- Known violations are recorded in the configured ledger before any `warn`-to-`error` ratchet starts.

## Completion checks

- `docs/harness-engineering/harness-engineering.json` exists and validates.
- `CLAUDE.md` points to configured docs paths rather than embedding all rules.
- `AGENTS.md` resolves to `CLAUDE.md`.
- `.agents/skills` resolves to `.claude/skills` when present.
- Docs, scripts, CI, hooks, and project-specific requirement docs match their configured or documented paths.
- `guardrails.md`, `readiness.md`, `known-violations.md`, and `updates.md` exist under `docs/harness-engineering/`.
- The validator output lists no `error` missing paths or malformed config fields.
