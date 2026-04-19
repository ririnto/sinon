# Context Budget and Coherent Scope

Open this file when the skill feels too broad, `SKILL.md` keeps growing, or you are unsure whether one skill should be split into several coherent units.

## Core rule

One skill should cover one job that can be described clearly in one sentence and executed from one ordinary-path `SKILL.md`.

## Signals that the scope is too broad

- the skill description needs several verbs joined with 'and'
- the main procedure branches early into unrelated workflows
- the examples serve different audiences or file types
- `SKILL.md` keeps gaining exception lists to handle nearby tasks
- the output contract changes depending on which branch the reader picked

## Signals that the scope is too narrow

- the split creates several tiny skills that share nearly the same common path
- most of the real differences are only host names or vendor wrappers
- references would be empty because every file repeats the same workflow

## Repair moves

### Narrow the job statement

Turn this:

```text
Write and validate API, CLI, and deployment skills.
```

Into this:

```text
Write or refactor one deployment-oriented skill for offline use.
```

### Move additive depth out of `SKILL.md`

Keep the common path in the main file, then move only these items out:

- extended examples
- compatibility branches
- large templates
- troubleshooting after the ordinary path fails

### Merge siblings when the job is still one unit

If two candidate skills differ only by host, vendor, or small command variants, keep one skill and move the deltas into references.

## Context-budget heuristics

Use these heuristics before adding more text:

1. If a rule applies on nearly every activation, keep it in `SKILL.md`.
2. If a block exists only for a named blocker, move it to `references/`.
3. If the block is mostly copy-paste material, move it to `assets/`.
4. If prose is less reliable than deterministic code, consider `scripts/`.
5. If the main file starts carrying several audiences or several deliverables, narrow the scope before writing more.

## Scope check prompts

Ask these before finalizing:

1. Can I explain the skill's job in one sentence without 'and/or' sprawl?
2. Would most activations follow the same first five steps?
3. Does the output contract stay stable across the normal workflow?
4. Could a user succeed from `SKILL.md` alone?

If any answer is no, fix the boundary first.
