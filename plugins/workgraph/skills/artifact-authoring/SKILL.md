---
name: artifact-authoring
description: Author non-agent Markdown, YAML, configuration, documentation, and visual deliverables. Use when creating or editing an artifact whose format, metadata, portability, or rendered quality matters.
---

# Artifact Authoring

## Preserve the Requested Artifact

- Follow the requested type, language, length, structure, genre, and repository convention before applying a general preference.
- Preserve factual claims and do not invent names, metrics, dates, capabilities, outcomes, or supporting evidence.
- Keep code, commands, paths, URLs, quoted output, required punctuation, and non-ASCII text unchanged.
- Match document length to the task and remove boilerplate, repeated summaries, unsupported superlatives, and meta-commentary.

## Dependencies and Versions

- Name an exact version or commit when adopting a dependency, changing a lock or module file, reproducing a result, or making a compatibility-dependent recommendation.
- Verify an unfixed version from an authoritative source.
- Do not add an arbitrary version to a conceptual reference.

## YAML and Metadata

- Follow the repository's current extension convention and prefer `.yaml` only for a new unconstrained file.
- Use `|-` when a block scalar's trailing newline is immaterial and `|` when the consumer requires it.
- Add frontmatter only for metadata consumed by the surrounding system.
- Keep metadata fields minimal and stable and omit timestamps unless a consumer requires them.

## Markdown

- Write one prose sentence per physical line.
- Place a blank line after headings and before lists or fenced code blocks.
- Use sequential heading levels and add a language identifier to every fenced code block.
- Use headings and plain prose for structure and reserve bold or italics for a real scanning or semantic benefit.

## Scratch and Visual Validation

- Keep temporary plans, review notes, generated helpers, and intermediate specifications outside the repository unless they are intentional deliverables.
- Keep mutable scratch state isolated across worktrees and remove temporary helpers after their final owner releases them.
- Render or open visual artifacts before finalizing them.
- Inspect clipping, overflow, spacing, missing content, legibility, responsive behavior, required states, and consistency with existing design tokens.
- Revise defects revealed by rendering rather than relying on source inspection alone.
