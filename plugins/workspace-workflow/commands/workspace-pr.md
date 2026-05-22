---
description: >-
  Compose a Git-contained change description from the current branch's commits with structured Summary, Why, Changes, Validation, Review Focus, and Integration Handoff sections.
  Use when preparing review context or a merge handoff without publishing remote metadata.
argument-hint: Optional change title or summary hint
allowed-tools:
  - Bash
  - Read
  - AskUserQuestion
---

# Compose Change Description

Generate a structured Git-contained change description from repository evidence.
Analyze the current branch's commits, diff, validation evidence, and task context.
Return a truthful draft for local review and integration handoff.

Initial context: $ARGUMENTS

## Workflow

### Step 1: Resolve Branch Context

Determine the target branch and comparison base from local Git state.

```sh
git branch --show-current
git rev-parse --abbrev-ref @{u}
gn=1
```

If the upstream or target is not clear, ask the user for the target branch.
Do not fetch or infer a comparison base from a remote URL.

### Step 2: Analyze Commits

List commits between the comparison base and `HEAD`:

```sh
git log --oneline <base>..HEAD
git diff <base>...HEAD --stat
git diff <base>...HEAD
```

Collect commit subjects, affected areas, implementation facts, and any breaking-change footer.

### Step 3: Extract The Change Narrative

Determine from evidence:

1. Intended result and affected behavior.
2. Rationale from task context or the diff.
3. Scope and meaningful implementation boundaries.
4. Breaking changes and compatibility effects.
5. Validation that actually ran.

Do not invent a work-item identifier, review URL, or private local path.

### Step 4: Draft The Change Description

Use this structure unless repository guidance requires another shape:

```markdown
## Summary

- <evidence-backed intended result>

## Why

- <requirement or reason shown by the task and diff>

## Changes

- <grouped implementation facts and repository-relative paths>

## Validation

- <checks that actually ran, with results>

## Review Focus

- <behavior, boundary, or risk areas>

## Integration Handoff

- <comparison base, target branch, selected Git strategy, and cleanup>

## Risks

- <specific risk or None>

## Unverified Items

- <missing evidence or None>
```

If motivation is not evident, say so instead of inventing it.
Mark missing validation as pending or unverified.

### Step 5: Present The Draft

Return the complete description and material blockers.
Do not publish, push, edit remote metadata, or mutate Git state.
