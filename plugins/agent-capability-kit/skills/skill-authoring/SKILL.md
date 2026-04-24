---
name: skill-authoring
description: Write or refactor a cross-platform Agent Skill. Use this skill when the skill must stay self-sufficient in SKILL.md, easy to trigger from its description, and usable offline without mandatory external tooling.
---

# Skill Authoring

Write or refactor one cross-platform Agent Skill so it is self-sufficient on activation, easy to trigger, and usable offline.

## Quickstart

Use this path for the ordinary case:

1. Read the existing skill directory, or start from `assets/skill-template.md` for a new skill.
2. Name the one coherent job the skill covers, then remove adjacent jobs from the draft before writing.
3. Draft a short plan for the main file, support files, and validation pass.
4. Write `SKILL.md` first so the common path works without `references/`, `assets/`, or `scripts/`.
5. Plan your validation approach using `assets/validation-checklist.md` as a final verification pass, not a required gate.

If the scope feels broad, the description feels vague, or `SKILL.md` keeps growing, fix that before adding more content.

## First safe checks

Before you edit anything:

1. Confirm the target is the current skill directory.
2. Read `SKILL.md`, `assets/skill-template.md`, and `assets/validation-checklist.md` together.
3. Check that the skill still reads as one flat, self-contained skill directory.
4. Verify that no support file is required to understand the ordinary path.
5. Keep any added guidance local to this skill and avoid hidden reference chains.

## Operating rules

- Keep the skill scope unchanged unless the task explicitly changes it.
- Treat the skill directory itself as the complete source for the job it covers.
- Keep the ordinary path in `SKILL.md`; do not require `references/`, `assets/`, or `scripts/` for the common case.
- Use support files only for named blockers, deeper examples, templates, or deterministic checks.
- Keep repository-facing and agent-facing rules in English.
- Prefer direct instructions, explicit file paths, and copy-adaptable examples over background prose.
- Do not rely on web pages or external documentation as required inputs for ordinary authoring.
- Do not make external CLIs, hosted validators, or host-specific behaviors part of the required workflow.
- Do not add guidance about evaluating skill output quality; that is outside this skill's scope.

## Local loading model

Use this local loading model as the default basis when authoring or reviewing a skill:

1. Frontmatter metadata helps discovery.
2. `SKILL.md` is the always-loaded instruction file on activation.
3. `references/`, `assets/`, and `scripts/` are optional additive depth opened only for a named need.

The ordinary authoring path MUST remain usable from the skill directory alone, even when offline.

## Placement rules

Use this flat layout unless the skill domain has a strong reason to do otherwise:

```text
skill-name/
├── SKILL.md
├── references/
├── assets/
└── scripts/
```

- `SKILL.md`: activation-time instructions, common-case workflow, key decisions, edge cases, and output contract
- `references/`: additive depth for a named blocker or branch
- `assets/`: copyable templates, starter files, schemas, and examples
- `scripts/`: deterministic, non-interactive helper code only when code is the clearest expression

## Frontmatter rules

Required fields:

- `name`
- `description`

Optional fields:

- `license`
- `compatibility`
- `metadata`

### `name`

- 1 to 64 characters
- lowercase letters, numbers, and hyphens only
- no leading or trailing hyphen
- no consecutive hyphens
- must match the skill directory basename exactly

### `description`

- 1 to 1024 characters
- state both capability and trigger condition
- include likely user-intent keywords, nouns, file types, systems, or goals
- remain valid outside one host product
- be specific enough to activate the right skill without locking to one host or vendor

Default formula:

```text
[Primary capability]. Use when [task, inputs, systems, file types, or user intent].
```

Strong example:

```text
Draft release automation runbooks and rollback notes. Use when preparing deployment procedures, CI release steps, or operational handoff docs.
```

Weak example:

```text
Helps with releases.
```

Run an offline trigger test before you keep a description:

1. Hide the skill name.
2. Read only the `description`.
3. Ask whether another engineer would load it for the intended prompts and avoid it for nearby prompts.
4. Tighten or widen the wording until both answers are yes.

Use `assets/description-patterns.md` for quick rewrites and `references/description-design.md` only when the description still feels vague, too broad, or hard to trigger.

### `compatibility`

- use only for real environment requirements
- 500 characters maximum
- reserve it for shell/runtime, packages, network access, or platform expectations that materially affect use

## Ordinary authoring loop

Use a plan-validate-revise loop instead of writing the whole skill in one pass.

1. Onboard the current state.
   - Read the skill directory.
   - For a refactor, mark what already works and what is missing.
   - For a new skill, copy the skeleton from `assets/skill-template.md`.
2. Define the coherent unit.
   - Write one sentence for the job this skill owns.
   - Remove adjacent jobs, host-specific branches, and optional depth from that sentence.
   - If the sentence needs 'and', 'or', or a long exception list, the scope is probably too broad.
3. Plan the file split.
   - Put always-needed guidance in `SKILL.md`.
   - Put copyable artifacts in `assets/`.
   - Put blocker-specific depth in `references/`.
   - Put deterministic helper code in `scripts/` only when prose is less safe than code.
4. Draft the main file first.
   - Start with outcome, rules, numbered procedure, edge cases, and output contract.
   - Keep defaults and first actions in the main file.
   - Add short copyable examples instead of long explanation.
5. Validate the draft.
   - Check whether the ordinary path works from `SKILL.md` alone.
   - Check whether the description triggers correctly.
   - Check whether each support file is optional and blocker-oriented.
   - Check whether the file still fits one coherent unit and a manageable context budget.
6. Revise and repeat.
   - Trim repeated prose.
   - Move additive depth out of `SKILL.md`.
   - Fold any always-needed reference content back into `SKILL.md`.
7. Finish with the checklist.
   - Run `assets/validation-checklist.md` as a final verification pass.
   - Fix every failing item, but do not treat the checklist as a gate that blocks completion when all ordinary-path guidance is correct.

## Coherent unit and content placement

Keep a skill small enough that activation loads the common path without dragging in unrelated decisions.

### Scope rules

- One skill SHOULD cover one job that can be named in one sentence.
- `SKILL.md` SHOULD contain only material needed on most activations.
- Examples SHOULD be short and representative; move catalogs and long variants out of the main file.
- References SHOULD answer named blockers, not act as overflow storage for ordinary guidance.
- If the main file starts reading like a handbook for several adjacent jobs, narrow the scope instead of adding more structure.

Open `references/context-budget-and-scope.md` only when you need help deciding whether the skill is too broad, too crowded, or split at the wrong boundary.

### Placement rules

Keep content in `SKILL.md` when the agent needs it immediately after activation:

- the skill's main workflow
- required validation steps
- the default file layout
- the output shape
- always-on guardrails

Move content to `references/` only when it is additive rather than mandatory:

- host-specific deviations
- extended examples for a special branch
- troubleshooting after the main workflow fails
- compatibility details that do not apply to every run

Move content to `assets/` when the best form is a copyable artifact:

- starter frontmatter blocks
- Markdown skeletons
- sample JSON or YAML files
- reusable checklists

Move content to `scripts/` when a repeated step is safer as code than prose:

- validation helpers
- packaging helpers
- static checks
- report generation

Open `references/progressive-disclosure.md` only when the placement decision is unclear or `SKILL.md` is getting crowded.

## `SKILL.md` body contract

The ordinary-path `SKILL.md` should usually contain, in this order:

1. Goal or outcome
2. Operating rules or invariants
3. Numbered procedure
4. Edge cases and ambiguity handling
5. Output contract
6. Optional support-file pointers for named blockers only

Prefer imperative wording. Show defaults instead of large option menus. Keep deeper material one level down from `SKILL.md`.

## Reusable inline patterns

Use these patterns directly in `SKILL.md` when they help the ordinary path.

### Output contract template

Keep the expected response shape inline so the skill can be used immediately after activation.

```markdown
## Output contract

Return:

1. The main artifact
2. Any changed files or paths
3. Validation results
4. Explicit remaining risks or blockers
```

### Progress checklist pattern

Use a short progress checklist when the workflow has several phases that are easy to miss. Keep it brief and action-focused.

```markdown
## Progress checklist

- [ ] Scope is one coherent job
- [ ] `SKILL.md` covers the ordinary path on its own
- [ ] Support files are additive only
- [ ] Description states both what and when
- [ ] Final checklist passed
```

Do not turn the checklist into a second full procedure. It is a tracking aid, not the main workflow.

### Gotchas pattern

Use a short `## Gotchas` section when the domain has recurring traps that are cheaper to prevent than to debug later.

```markdown
## Gotchas

- Do not move always-needed guidance into `references/`.
- Do not make a helper script mandatory for the ordinary path.
- Do not widen the description until unrelated prompts start matching.
```

If the list grows long, split the deeper troubleshooting into a reference and keep only the recurring traps inline.

## Scripts guidance

Scripts are optional. Add `scripts/` only when code is safer than prose for a repeated deterministic step.

- Good candidates: validation helpers, static checks, file generation, report formatting, deterministic transforms
- Bad candidates: interactive workflows, host-specific wrappers, web-required setup, one-off convenience commands
- A script MUST stay non-interactive and SHOULD use tooling that is already common for the target environment
- The ordinary path MUST still be understandable without reading the script source first

Open `references/scripts-guidance.md` only when you are unsure whether a script belongs in the skill or how to document it without making it mandatory.

## Minimal example

Use this as a smallest useful starting point:

```markdown
---
name: markdown-review
description: Review a Markdown document for structure, headings, and missing sections. Use when a document needs a fast quality pass before review or release.
---

# Markdown Review

Review one Markdown file and return concrete improvements.

## Operating rules

- Keep the review focused on the target file and its immediate structure.
- Prefer concrete fixes over general commentary.
- Use support files only if a named blocker or deeper example is required.

## Procedure

1. Read the target document.
2. Check heading order and section coverage.
3. Identify missing or inconsistent structure.
4. Report concrete fixes.

## Output contract

Return:

1. The main issues
2. Recommended fixes
3. Any remaining ambiguity
```

## Edge cases

- If the scope is actually multiple adjacent jobs, narrow the skill to one coherent unit and move variants to references only when they are genuinely additive.
- If `SKILL.md` is growing because of long templates, move the templates into `assets/` and keep only the usage rule in `SKILL.md`.
- If the workflow depends on product-specific behavior, rewrite it so the common path still makes sense as a portable skill.
- If a reference is required on every run, fold its durable guidance back into `SKILL.md`.
- If the description matches both the intended job and unrelated nearby jobs, tighten the trigger wording before shipping.

## Pitfalls

- Do not move common-path guidance into `references/` just to shrink `SKILL.md`.
- Do not turn `SKILL.md` into a background essay.
- Do not make external web content a required part of the main workflow.
- Do not use references as hidden prerequisites for ordinary authoring.
- Do not split one coherent job across sibling skills only because hosts or vendors differ.
- Do not add scoring rubrics or output-quality evaluation loops to this skill.

## Output contract

Return:

1. The skill file tree
2. The full `SKILL.md`
3. Every referenced support file needed for the requested deliverable
4. Validation results and any explicit remaining risks

## Optional support files

- `references/context-budget-and-scope.md` - open when the skill feels too broad, too long, or split at the wrong boundary
- `references/description-design.md` - open when the scope is correct but the `description` is still weak or hard to trigger
- `references/progressive-disclosure.md` - open when `SKILL.md` is crowded and you need to move additive material without breaking the ordinary path
- `references/scripts-guidance.md` - open when you are deciding whether a helper script belongs in the skill or how to document it safely
- `assets/skill-template.md` - copy when creating a new skill from scratch
- `assets/description-patterns.md` - copy when rewriting or comparing `description` text patterns
- `assets/validation-checklist.md` - use when performing the final pass before returning the skill
