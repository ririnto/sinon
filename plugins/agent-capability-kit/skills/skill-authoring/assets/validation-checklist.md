# Validation Checklist

Use this checklist as a final verification pass, not as a gate that blocks completion. Items marked with `[repo]` are specific to the Sinon publishing standard and may not apply to skills published elsewhere.

## Frontmatter

- [ ] Directory name matches `name`
- [ ] `name` is lowercase, hyphen-safe, and <= 64 characters
- [ ] `description` is non-empty and <= 1024 characters
- [ ] `description` states both what and when
- [ ] `description` is specific enough to trigger the right job and exclude nearby jobs
- [ ] `compatibility` is absent or <= 500 characters

## Scope and onboarding

- [ ] The skill covers one coherent job
- [ ] The quickstart or first actions are obvious from `SKILL.md`
- [ ] The skill stays in the flat `SKILL.md` / `references/` / `assets/` / `scripts/` layout unless a strong reason is stated
- [ ] The ordinary path is usable without web access or product-specific behavior
- [ ] No external CLI, validator, or hosted service is required for the common case
- [ ] No guidance about evaluating skill output quality appears in the workflow

## Main instructions

- [ ] `SKILL.md` is under 500 lines
- [ ] `SKILL.md` contains the always-needed guidance for the ordinary task
- [ ] Critical rules appear before examples
- [ ] The main procedure follows a plan-validate-revise loop or equivalent validation cycle
- [ ] Context-budget and coherent-unit guidance are explicit
- [ ] Edge cases are explicit
- [ ] Output requirements are explicit
- [ ] Any progress checklist is short and secondary to the main procedure
- [ ] The ordinary path is usable without opening `references/`

## Support files

- [ ] Every referenced file exists
- [ ] Assets are directly copyable
- [ ] Reference docs answer questions the main file intentionally omits
- [ ] Each reference states when to open it
- [ ] References hold additive depth only, not always-needed rules
- [ ] No reference file is required before `SKILL.md` can be used
- [ ] No hidden reference chain is needed to understand the ordinary path
- [ ] References are not used as prerequisites for other references

## Scripts

- [ ] If scripts exist, they are non-interactive
- [ ] If scripts exist, they support a deterministic task better expressed as code
- [ ] No script is a hidden prerequisite for understanding the ordinary path
- [ ] Script guidance does not assume host-specific wrappers or required external services

## Cross-platform [repo]

These items apply when publishing to the Sinon marketplace. Skip them if the skill targets a different publishing context.

- [ ] No Claude-only frontmatter guidance appears in the source
- [ ] No product-specific SDK code appears in the source
- [ ] Examples are raw files, shell commands, JSON, YAML, or Markdown
