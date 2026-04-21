---
title: RESEARCH Authoring Guide
description: >-
  Detailed instructions for writing RESEARCH.md, including section intent,
  timing in the workflow, and evidence-oriented writing practices.
---

## Purpose

Open this guide only when `../SKILL.md` is already clear and you need section-by-section `RESEARCH.md` authoring depth.
RESEARCH documents MUST capture investigation findings about an external framework, library, or topic, not implementation commitments.
Authors MAY start by copying `../assets/templates/RESEARCH.md` into the canonical research path.

## Table of Contents

- [Workflow Timing](#workflow-timing)
- [Frontmatter](#frontmatter)
- [Section-by-Section Authoring](#section-by-section-authoring)
- [Source Notes](#source-notes)
- [Writing Boundaries](#writing-boundaries)
- [Quality Gates](#quality-gates)
- [Refresh Rules](#refresh-rules)

## Workflow Timing

1. Start research before SPEC authoring when external framework, library, or topic behavior is unclear.
2. Update research during SPEC Setup if unknowns remain.
3. Finalize research before Spec Review when findings affect requirements.
4. Re-open research only when external versions or constraints change.

## Frontmatter

Required fields:

- `title`: Investigation title.
- `description`: Scope and question summary.
- `last_updated`: ISO 8601 calendar date (for example, `2026-03-02`).
- `subject.name`: Framework, library, or topic name.
- `subject.version`: Investigated version or release line.
- `tag`: Classification tag values.

Optional fields:

- `subject.url`: Primary source URL.

Guidance:

- Concrete version values SHOULD be used (for example, `1.15.2`, `2025-11`, or `LTS-3`).
- `subject.name` SHOULD identify the investigated external subject rather than the local project or repository.
- `subject.url` SHOULD be kept only when a stable, useful reference is available.
- Repository-specific implementation planning, migration sequencing, or task management MUST NOT be added in frontmatter.

## Section-by-Section Authoring

### Overview

When to write: At research start.

How to write:

- The section SHOULD state the investigation question and context.
- The section SHOULD identify the external subject being investigated.
- The section SHOULD explain why this research is needed now.
- The section SHOULD define scope boundaries and explicit non-goals.

### Questions

When to write: At research start.

How to write:

- Primary questions SHOULD be listed before findings accumulate.
- Questions SHOULD focus on investigated subject behavior, constraints, or version-sensitive facts.
- Questions that later become irrelevant SHOULD be removed or marked resolved.

### Findings

When to write: As evidence is collected.

How to write:

- Confirmed facts SHOULD be recorded first.
- Hypotheses, assumptions, and unknowns SHOULD be separated.
- Version-sensitive behavior and constraints SHOULD be included.
- Tradeoffs relevant to later SPEC requirements SHOULD be captured.
- Source notes SHOULD be attached when they clarify where a finding came from.

### Conclusion

When to write: After findings stabilize.

How to write:

- The section SHOULD summarize actionable conclusions.
- The section SHOULD note limitations and unresolved questions.
- The section SHOULD define triggers that require research refresh.

### Source Notes

When to write: As evidence is collected.

How to write:

- Record one source note for each critical finding.
- Source notes SHOULD identify which claim or constraint the source supports.
- Version numbers, URLs, or document identifiers SHOULD be included when they help a reviewer reproduce the research trail.

### Refresh Triggers

When to write: Before the document is considered review-ready.

How to write:

- Version bumps SHOULD be listed when they invalidate prior conclusions.
- External API or policy changes SHOULD be recorded as refresh triggers.
- Unknowns that block confident SPEC decisions SHOULD be called out directly.

## Writing Boundaries

RESEARCH documents MUST NOT include:

- Project comparison, repository audits, implementation planning, migration sequencing, or task management content.
- Rollout planning or delivery planning presented as research.

RESEARCH documents SHOULD include:

- Evidence-backed behavior notes about the investigated subject.
- Compatibility constraints.
- Source-of-truth references for external behavior.

## Quality Gates

Before using research in SPEC decisions:

- Frontmatter MUST be valid against `assets/schemas/research-frontmatter.schema.json`.
- TODO markers MUST NOT remain.
- Unresolved placeholders MUST NOT remain.
- `"${SKILL_ROOT}/scripts/verify-spec.sh" ./spec` MUST be run, and failures MUST be resolved.
- If the consuming repository already uses markdownlint, maintainers MAY run `npx -y markdownlint-cli2 <touched-markdown-files>`.

## Refresh Rules

`last_updated` SHOULD be updated and findings SHOULD be reviewed when:

- `subject.version` changes.
- External API behavior changes.
- New constraints invalidate prior conclusions.

If no material change occurred, the document SHOULD remain unchanged.
