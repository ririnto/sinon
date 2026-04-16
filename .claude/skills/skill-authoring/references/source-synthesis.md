---
title: Skill Source Synthesis Reference
description: >-
  Reference for applying agentskills.io as the structural basis and treating secondary runtime or vendor ecosystems as compatibility deltas.
---

Use this reference when the blocker is how to reconcile multiple skill ecosystems without weakening the repository's agentskills.io-first hierarchy. This file is sufficient on its own for that normalization.

## Hierarchy

Use sources in this order:

1. agentskills.io loading model as the structural basis
2. repository-local rules and nearby repository examples as the local implementation of that basis
3. runtime-specific documentation such as Claude Code, OpenCode, OpenAI, GitHub Copilot, and Gemini as compatibility inputs only when they materially affect packaging or delivery

The repository decides the final shape, but it does so under an agentskills.io-first hierarchy. Runtime or vendor ecosystems are compatibility inputs, not co-equal structural authorities.

## Stable Cross-Ecosystem Convergence

These points remain stable after normalization:

- one skill is packaged as one directory with a `SKILL.md` entrypoint
- YAML frontmatter with `name` and `description` is the common anchor
- the description acts as the routing signal
- the main body should stay compact and action-oriented
- deeper material should load only when needed
- self-contained skills outperform bundles that require hidden prerequisites
- one coherent user job should stay in one skill unless the common path materially diverges

## What agentskills.io Governs Most Clearly

Agentskills.io supplies the governing structural model for:

- progressive disclosure across metadata, instructions, and optional resources
- the requirement that `description` says what the skill does and when to use it
- coherent unit sizing for skills
- merging sibling host/platform variants when they share the same common path
- grounding skills in project-specific or domain-specific expertise instead of generic language-model knowledge
- keeping the main entrypoint concise and example-heavy

## Repository Normalization Rules

When external docs differ in style, normalize them to this repository by:

- keeping the agentskills.io loading hierarchy intact
- keeping `SKILL.md` self-sufficient for the common case
- merging sibling host/vendor/platform skills when the user job and common path are the same
- using the repository's canonical sections and review heuristics
- preferring blocker-based `Deep References` tables over generic “see also” links
- expressing adjacent-domain exclusions in domain terms
- putting stable templates and validation checks in `SKILL.md`

## Secondary Runtime and Vendor Notes

### Claude Code and OpenCode

- Both fit naturally with directory-based skills and YAML frontmatter.
- Use them as confirmation that repo-local filesystem skills are a valid first-class surface, not as a reason to change the repository's top-level hierarchy.

### OpenAI

- Treat OpenAI skill bundles as support for the same packaging idea: one manifest plus optional support files.
- Keep OpenAI-specific packaging details out of the common path unless this repository actually ships to that environment.

### GitHub Copilot

- Copilot's skill material reinforces concise descriptions and compact, task-shaped instruction bodies.
- If portability matters, keep descriptions short and trigger-rich enough to remain useful under tighter limits.

### Gemini

- Gemini guidance is useful for instruction clarity and iterative testing, but it is less prescriptive about the exact `SKILL.md` package shape.
- Use Gemini material as a writing-quality input rather than a structural override.

## Practical Synthesis Checklist

- Did the final skill keep agentskills.io as the explicit structural basis?
- Did the final skill keep the repository's section contract?
- Did agentskills.io meaningfully influence routing, progressive disclosure, and coherent unit sizing?
- Were host/vendor/platform deltas pushed into focused references instead of separate sibling skills when the common path was the same?
- Did vendor differences stay in references unless they materially affect the common path?
- Would the skill still make sense if read only by a maintainer of this repository?
