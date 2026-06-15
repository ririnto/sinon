# Validation Checklist

Use this checklist as a final verification pass.
Items marked with `[repo]` are specific to the Sinon publishing standard and may not apply to skills published elsewhere.

## Frontmatter

- Directory basename matches `name`.
- `name` is lowercase, hyphen-safe, and at most 64 characters.
- `description` is non-empty and at most 1024 characters.
- `description` opens with an imperative capability statement.
- `description` trigger wording adds distinct task, artifact, system, timing, or user-intent vocabulary.
- No verb-synonym duplication appears between the capability and trigger clause.
- Frontmatter contains no `title`, `metadata`, `owner`, `license`, `version`, `source`, documentation URL, argument hint, or tool allowlist fields.
- First body heading is the display title.
- YAML uses plain or double-quoted scalars for short readable values, `>-` for long wrapped single-value strings, and `|-` only when line breaks are semantic.

## Scope

- The skill covers one coherent job.
- The ordinary workflow is obvious from `SKILL.md`.
- The skill remains usable without web access, hosted validators, or product-specific wrappers.
- Official-source or version facts that affect ordinary use are stated in the body near the rule they constrain.

## `SKILL.md`

- `SKILL.md` contains all always-needed guidance for ordinary use.
- Critical rules appear before examples.
- The main procedure follows a plan, draft, validate, revise flow.
- First safe checks are explicit.
- Edge cases are explicit.
- Output requirements are explicit.
- The ordinary path works without opening `references/`, `assets/`, or `scripts/`.

## Support files

- Every referenced support file exists.
- Assets are directly copyable.
- Each reference states when to open it.
- References hold additive depth only, not always-needed rules.
- No reference file is required before `SKILL.md` can be used.
- No hidden reference chain is needed for the ordinary path.
- References are not used as prerequisites for other references.

## Scripts

- Scripts are non-interactive when they exist.
- Scripts support deterministic tasks that are safer as code than prose.
- No script is a hidden prerequisite for the ordinary path.
- Script guidance does not assume host-specific wrappers or required external services.
- Runtime dependency guidance is explicit when a script needs external packages.

## Cross-platform [repo]

- No Claude-only frontmatter guidance appears in skill source unless the skill is intentionally Claude-specific.
- No product-specific SDK code appears in cross-platform skill source.
- Examples are raw files, shell commands, JSON, YAML, Markdown, or another directly relevant artifact format.
