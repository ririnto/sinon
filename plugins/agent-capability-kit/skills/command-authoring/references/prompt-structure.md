# Prompt Structure Reference

Open this file when a command body needs more than the ordinary structure in `../SKILL.md`, or when an existing command needs repair.

## Purpose

Use this reference for extended repair patterns and body-shape comparisons beyond the canonical shape shown in `../SKILL.md`.

## When to open this file

- The command combines multiple constraints and the body is getting hard to scan.
- You are refactoring an existing command with mixed prose and instructions.
- You need examples for stronger ordering, clearer guardrails, or more exact output sections.

## Repair checklist for an unclear command

1. Move user-facing explanation out of the body unless it is part of the instruction.
2. Put required inputs near the top.
3. Convert loose paragraphs into ordered steps.
4. Add an explicit ambiguity section if the command depends on missing files, scope limits, or runtime inputs.
5. Finish with a concrete output contract.

## Before and after example

### Too loose

```markdown
# Review Docs

This command helps with documentation reviews. It should probably look at the file, think about problems, and provide a useful answer.
```

### What changed

The repaired version adds these structural elements that were missing from the loose original:

- **Goal line**: `Review one documentation file and return a concise rewrite plan.` replaces the vague helper prose.
- **`## Inputs` section**: makes the `$1` argument explicit as `file-path`.
- **`## Constraints` section**: adds `Do not edit files` and `Do not infer repository policies that are not present in the file`.
- **`## Procedure` as ordered steps**: replaces the single vague sentence with numbered actions.
- **`## Ambiguity behavior`**: handles the missing-argument and out-of-scope cases explicitly.
- **`## Output contract`**: states exactly what the response must contain.

The full repaired body follows the canonical order documented in `../SKILL.md` under Command body order. Use that section as the template; this reference shows only the structural delta between broken and repaired shapes.

### Multi-step command variant

When a command has branching logic or multiple phases, extend the Procedure section with sub-steps:

```markdown
## Procedure

1. Read and classify the target.
   - If the target is a config file, run the config validation sub-procedure.
   - If the target is source code, run the code review sub-procedure.
2. Collect findings into a structured report.
3. Return the report in the output contract format.
```

Keep the ambiguity behavior and output contract stable regardless of which branch the procedure takes.
