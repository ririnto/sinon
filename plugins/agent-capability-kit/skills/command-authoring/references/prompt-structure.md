# Prompt Structure Reference

Open this file when a command body needs more than the ordinary structure in `../SKILL.md`, or when an existing command needs repair.

## Purpose

Use this reference for extended examples, repair patterns, and body-shape comparisons.

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

### Repaired structure

```markdown
# Review Docs

Review one documentation file and return a concise rewrite plan.

## Inputs

- `file-path`: target file from `$1`

## Constraints

- Do not edit files.
- Do not infer repository policies that are not present in the file.

## Procedure

1. Read the target file.
2. Identify structural, clarity, and missing-content issues.
3. Summarize fixes in priority order.

## Ambiguity behavior

- If `$1` is missing, stop and request the file path.
- If the file is outside the documented scope, report that blocker.

## Output contract

Return:

1. The main issues
2. Recommended fixes
3. Any remaining blocker or risk
```
