---
title: Sinon Project Rules
description: >-
  Stable repository rules for plugin packaging and skill authoring in the Sinon marketplace.
---

# Sinon Project Rules

## Purpose

This repository publishes installable AI plugins for multiple runtimes from one shared source tree.

Rules in this file describe stable repository expectations that SHOULD remain valid even as individual plugins, skills, and manifests evolve.

## Repository Model

- Plugins MUST live under `plugins/`.
- Each plugin MAY expose multiple runtime manifests from the same plugin root.
- Runtime-specific marketplace metadata MUST stay aligned with the plugin content they publish.
- Root-level documentation MUST describe repository-wide structure and rules, not fast-changing plugin details.

## Skill Asset Model

- A skill MUST remain usable when installed by itself.
- A skill MUST NOT depend on another skill as a required prerequisite for basic execution.
- A skill MUST NOT require direct cross-skill routing in order to explain its common case or scope boundaries.
- `SKILL.md` MUST describe adjacent-domain exclusions in domain terms, not as instructions to jump to another skill by name.
- Plugin-level inventories or catalog documentation MAY list bundled skills, but a skill entrypoint itself MUST remain self-sufficient without cross-skill handoff text.
- Skill directories SHOULD keep a shallow structure so agents can discover relevant material quickly.

## `SKILL.md` Contract

- `SKILL.md` MUST be the canonical entrypoint for a skill.
- `SKILL.md` `name` MUST exactly match the skill directory basename.
- `SKILL.md` MUST contain the activation surface, common-case workflow, representative templates or commands, invariants, pitfalls, scope boundaries, and brief reference pointers.
- `SKILL.md` MUST stay self-sufficient for the common case.
- `SKILL.md` SHOULD favor direct, imperative guidance over tutorial-style narration.
- `SKILL.md` MUST NOT become a generic essay or background article.

## `references/` Contract

- `references/` MUST contain additive depth only.
- `references/` MUST NOT repeat the same canonical templates, workflow steps, invariants, or pitfalls already owned by `SKILL.md`.
- `references/` SHOULD hold extended examples, deeper comparisons, operational caveats, version boundaries, and edge-case decision material.
- `references/` MAY assume the reader has already activated and understood the parent `SKILL.md`.
- `references/` MUST support progressive disclosure rather than act as duplicate standalone skill files.
- If two reference documents are commonly read together to resolve the same blocker, and the split does not materially reduce scanning cost, they SHOULD be merged.
- If a skill keeps only one reference file and that reference is likely to be read in the common path, its durable content SHOULD be folded back into `SKILL.md` instead of staying separate.

## Coding-Skill Rules

- Coding-related skills MUST give more weight to code, commands, templates, and concrete examples than to explanatory prose.
- In coding-related skills, every important rule SHOULD be anchored by runnable or readily adaptable code or command examples.
- Explanatory prose around templates SHOULD be compressed to the minimum needed for safe use.
- Broken-versus-correct examples SHOULD be preferred over abstract warnings when they communicate the rule more clearly.
- Format-critical output shapes MUST appear in `SKILL.md`, not only in references.
- References for coding skills SHOULD expand coverage with additional concrete examples rather than longer conceptual narration.

## Command-Heavy Skill Rules

- Command-heavy skills MUST present the primary decision path and first safe commands in `SKILL.md`.
- Command syntax shown in `SKILL.md` MUST be copyable and explicit.
- Long command catalogs, compatibility matrices, and secondary option tables SHOULD live in `references/`.
- Operational cautions MUST stay close to the commands they constrain.

## Documentation Style

- Repository-level and agent-facing rules documents MUST be written in English.
- Normative statements in stable rules documents MUST use BCP 14 language.
- Markdown documents MUST prefer headings, lists, and code blocks over dense prose paragraphs.
- Documentation comments and examples SHOULD use the native language or tool syntax of the subject being documented.

## Review Heuristics

- When a skill changes, reviewers MUST check both self-sufficiency and progressive disclosure.
- If a reference can be deleted without losing additive depth, it SHOULD be deleted or reduced.
- If a coding skill explains a pattern at length without showing the pattern concretely, it SHOULD be revised toward code-first guidance.
- If `SKILL.md` cannot handle the common case without opening references, it MUST be strengthened.
