---
title: Skill Source Synthesis Reference
description: >-
  Reference for normalizing agentskills.io and major vendor skill ecosystems into one repository-local authoring shape.
---

Use this reference when the blocker is how to reconcile multiple skill ecosystems without weakening the local repository standard. This file should be sufficient on its own to finish that normalization.

## Trust Order

Use sources in this order:

1. repository-local rules and nearby repository examples
2. agentskills.io as the highest-trust external skill specification
3. runtime-specific documentation such as Claude Code, OpenCode, OpenAI, GitHub Copilot, and Gemini

The repository decides the final shape. External ecosystems are compatibility inputs, not reasons to flatten local conventions.

## Stable Cross-Ecosystem Convergence

These points are stable across the surveyed ecosystems:

- one skill is packaged as one directory with a `SKILL.md` entrypoint
- YAML frontmatter with `name` and `description` is the common anchor
- the description acts as the routing signal
- the main body should stay compact and action-oriented
- deeper material should load only when needed
- self-contained skills outperform bundles that require hidden prerequisites

## What agentskills.io Adds Most Clearly

Agentskills.io contributes the strongest explicit model for:

- progressive disclosure across metadata, instructions, and optional resources
- the requirement that `description` says what the skill does and when to use it
- coherent unit sizing for skills
- grounding skills in project-specific or domain-specific expertise instead of generic language-model knowledge
- keeping the main entrypoint concise and example-heavy

## Repository Normalization Rules

When external docs differ in style, normalize them to this repository by:

- keeping `SKILL.md` self-sufficient for the common case
- using the repository's canonical sections and review heuristics
- preferring blocker-based `Deep References` tables over generic “see also” links
- expressing adjacent-domain exclusions in domain terms
- putting stable templates and validation checks in `SKILL.md`

## Vendor-Specific Notes

### Claude Code and OpenCode

- Both fit naturally with directory-based skills and YAML frontmatter.
- Use them as confirmation that repo-local filesystem skills are a valid first-class surface.

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

- Did the final skill keep the repository's section contract?
- Did agentskills.io meaningfully influence routing, progressive disclosure, and coherent unit sizing?
- Did vendor differences stay in references unless they materially affect the common path?
- Would the skill still make sense if read only by a maintainer of this repository?
