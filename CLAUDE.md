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

## Skill Routing and Loading Model

- A skill `description` MUST act as a routing key for activation and SHOULD be written in terms of user intent.
- Skill descriptions SHOULD prefer "Use this skill when..." framing over internal implementation summaries.
- `SKILL.md` MUST be written on the assumption that it is the primary document loaded at activation time.
- `references/`, `assets/`, and similar auxiliary material MUST be treated as on-demand depth rather than always-read context.
- A reference file SHOULD be opened only when `SKILL.md` gives a clear blocker-based or task-based trigger for doing so.
- Skill structures SHOULD follow progressive disclosure: routing metadata first, common-path instructions in `SKILL.md`, then optional depth in auxiliary files.

## `SKILL.md` Contract

- `SKILL.md` MUST be the canonical entrypoint for a skill.
- `SKILL.md` `name` MUST exactly match the skill directory basename.
- `SKILL.md` MUST contain the activation surface, common-case workflow, representative templates or commands, invariants, pitfalls, scope boundaries, and brief reference pointers.
- `SKILL.md` MUST stay self-sufficient for the common case.
- `SKILL.md` MUST retain material that belongs to the skill's primary purpose, even when that material is detailed or example-heavy.
- Common-case guidance MUST NOT be moved to `references/` only to reduce the size of `SKILL.md`.
- Primary authoring conventions that users are expected to apply during ordinary use of the skill MUST appear in `SKILL.md`.
- `SKILL.md` SHOULD route to deeper material by concrete blocker or job, not by broad topic labels alone.
- `SKILL.md` SHOULD include the short, copy-adaptable examples that demonstrate the common path or primary output shape.
- `SKILL.md` SHOULD favor direct, imperative guidance over tutorial-style narration.
- `SKILL.md` MUST NOT become a generic essay or background article.

## `references/` Contract

- `references/` MUST contain additive depth only.
- `references/` MUST NOT repeat the same canonical templates, workflow steps, invariants, or pitfalls already owned by `SKILL.md`.
- Each reference file MUST be a purpose-complete unit that helps the reader complete one specific job or resolve one specific blocker.
- A reference file MUST state its purpose and the condition for opening it.
- A reference file MUST be usable as a standalone answer for its stated blocker once opened.
- `references/` SHOULD hold extended examples, deeper comparisons, operational caveats, version boundaries, and edge-case decision material.
- `references/` MAY assume the reader has already activated and understood the parent `SKILL.md`.
- `references/` MUST support progressive disclosure rather than act as duplicate standalone skill files.
- `references/` MUST NOT hold material that is required to execute the skill's common case or primary purpose.
- `references/` SHOULD avoid chains where one reference regularly sends the reader to a second reference to finish the same job.
- If two reference documents are commonly read together to resolve the same blocker, and the split does not materially reduce scanning cost, they SHOULD be merged.
- If a skill keeps only one reference file and that reference is likely to be read in the common path, its durable content SHOULD be folded back into `SKILL.md` instead of staying separate.
- A coding-skill reference file SHOULD include at least one concrete additive example, command, config snippet, diff pattern, or output shape tied to its blocker; references SHOULD NOT degrade into prose-only checklists.

## Coding-Skill Rules

- Coding-related skills MUST give more weight to code, commands, templates, and concrete examples than to explanatory prose.
- In coding-related skills, every important rule SHOULD be anchored by runnable or readily adaptable code or command examples.
- In coding-related skills, default code-organization and authoring conventions that are central to the skill's purpose MUST appear in `SKILL.md` with concrete examples.
- In coding-related skills, short common-path examples SHOULD live in `SKILL.md`, while longer or conditional examples SHOULD move to purpose-complete references.
- Explanatory prose around templates SHOULD be compressed to the minimum needed for safe use.
- Broken-versus-correct examples SHOULD be preferred over abstract warnings when they communicate the rule more clearly.
- Format-critical output shapes MUST appear in `SKILL.md`, not only in references.
- References for coding skills SHOULD expand coverage with additional concrete examples rather than longer conceptual narration.
- When a skill documents multiple valid workflows for the same asset class, each workflow MUST keep its own commands, paths, and output tree internally consistent.
- If a skill distinguishes direct-source assets from generated or rendered assets, the documentation MUST name that boundary explicitly and keep validation commands, render commands, and provisioning paths aligned to the correct side of the boundary.

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
- Reviewers MUST verify that `SKILL.md` alone can handle the skill's common case and primary purpose.
- Reviewers MUST verify that each reference file corresponds to one concrete user goal and can be used independently for that goal.
- Reviewers MUST verify that the skill description routes by user intent rather than by internal implementation wording.
- Reviewers MUST verify that `SKILL.md` tells the reader when to open each referenced document.
- If a reference and `SKILL.md` both need to be read to complete one ordinary task, the split SHOULD be revised.
- If a reference can be deleted without losing additive depth, it SHOULD be deleted or reduced.
- If a coding skill explains a pattern at length without showing the pattern concretely, it SHOULD be revised toward code-first guidance.
- If `SKILL.md` cannot handle the common case without opening references, it MUST be strengthened.
- Reviewers MUST check that example commands and documented repository layouts refer to the same paths for the same workflow.
- Reviewers MUST check that direct-asset workflows and rendered-asset workflows are distinguishable in examples, so copy-paste commands do not silently cross those boundaries.
