---
title: Sinon Project Rules
description: >-
  Stable repository rules for plugin packaging and skill authoring in the Sinon marketplace.
---

# Sinon Project Rules

## Repository Model

- Plugins MUST live under `plugins/`.
- Each plugin MAY expose multiple runtime manifests from the same plugin root.
- Runtime-specific marketplace metadata MUST stay aligned with the plugin content they publish.
- Root-level documentation MUST describe repository-wide structure and rules, not fast-changing plugin details.
- `CLAUDE.md` MUST remain the canonical root rules document. `AGENTS.md` is a symlink to `CLAUDE.md` and MUST be treated as the same document rather than an independently maintained copy.
- `.claude/skills/` MUST remain the canonical repository-local skills directory. `.agents/skills/` is a symlink to `.claude/skills/` and MUST be treated as the same skill inventory rather than a separate parallel tree.

## Skill Loading Model

- This repository MUST treat the agentskills.io loading model as the top-level basis for local skill rules.
- When the task is to create, edit, review, refactor, validate, or package an Agent Skill for this repository, agents SHOULD load the local `skill-authoring` skill from `.claude/skills/skill-authoring/`. The mirrored path `.agents/skills/skill-authoring/` resolves to the same content via symlink.
- Skill metadata, especially `description`, MUST act as the routing trigger for activation and SHOULD use user-intent language such as "Use this skill when...".
- `SKILL.md` MUST be the canonical document loaded at activation time and MUST carry the common path for the ordinary task.
- `references/`, `assets/`, `scripts/`, and similar support material MUST be treated as focused on-demand depth rather than always-loaded context.
- Skill structures SHOULD follow progressive disclosure: routing metadata first, common-path instructions in `SKILL.md`, then optional depth in auxiliary files.
- A skill SHOULD cover one coherent unit of work. When sibling skills share the same common path and differ mainly by host, vendor, or platform details, they SHOULD be merged and the deltas SHOULD move to focused references.

## Skill Structure Contracts

- A skill MUST remain usable when installed by itself.
- A skill MUST NOT depend on another skill as a required prerequisite for basic execution.
- A skill MUST NOT require direct cross-skill routing or handoff text in order to explain its common case or scope boundaries.
- A single skill MAY cover multiple hosts or platforms when the user job is the same and the common path remains in `SKILL.md`.
- Plugin-level inventories or catalog documentation MAY list bundled skills, but a skill entrypoint itself MUST remain self-sufficient.
- Skill directories SHOULD keep a shallow structure so agents can discover relevant material quickly.
- `SKILL.md` `name` MUST exactly match the skill directory basename.
- `SKILL.md` MUST contain the activation surface, common-case workflow, representative templates or commands, invariants, pitfalls, scope boundaries, and brief reference pointers.
- `SKILL.md` MUST stay self-sufficient for the common case.
- `SKILL.md` MUST keep the shared workflow, decision point, and first safe commands when a skill spans multiple hosts or platforms.
- `SKILL.md` MUST retain material that belongs to the skill's primary purpose, even when that material is detailed or example-heavy.
- Common-case guidance MUST NOT be moved to `references/` only to reduce the size of `SKILL.md`.
- Primary authoring conventions that users are expected to apply during ordinary use of the skill MUST appear in `SKILL.md`.
- `SKILL.md` MUST describe adjacent-domain exclusions in domain terms, not as instructions to jump to another skill by name.
- `SKILL.md` SHOULD route to deeper material by concrete blocker or job, not by broad topic labels alone.
- `SKILL.md` SHOULD include the short, copy-adaptable examples that demonstrate the common path or primary output shape.
- `SKILL.md` SHOULD favor direct, imperative guidance over tutorial-style narration.
- `SKILL.md` MUST NOT become a generic essay or background article.

## `references/` Contract

- `references/` MUST contain additive depth only and MUST NOT hold material required for the skill's common case or primary purpose.
- `references/` MUST NOT repeat the same canonical templates, workflow steps, invariants, or pitfalls already owned by `SKILL.md`.
- Platform-specific references MAY hold host-specific template paths, command variants, fallback shapes, and operational caveats once the shared path is already covered in `SKILL.md`.
- Each reference file MUST be a purpose-complete unit that states its purpose, the condition for opening it, and stands alone for one specific blocker or job.
- `references/` SHOULD hold extended examples, deeper comparisons, operational caveats, version boundaries, and edge-case decision material.
- `references/` MAY assume the reader has already activated and understood the parent `SKILL.md`.
- `references/` MUST support progressive disclosure rather than act as duplicate standalone skill files.
- `references/` SHOULD avoid chains, and references commonly read together for the same blocker SHOULD be merged when the split does not materially reduce scanning cost.
- If a skill keeps only one reference file and that reference is likely to be read in the common path, its durable content SHOULD be folded back into `SKILL.md` instead of staying separate.
- A coding-skill reference file SHOULD include at least one concrete additive example, command, config snippet, diff pattern, or output shape tied to its blocker; references SHOULD NOT degrade into prose-only checklists.

## Coding and Command Rules

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
- Command-heavy skills MUST present the primary decision path and first safe commands in `SKILL.md`.
- Command syntax shown in `SKILL.md` MUST be copyable and explicit.
- Long command catalogs, compatibility matrices, and secondary option tables SHOULD live in `references/`.
- Operational cautions MUST stay close to the commands they constrain.

## Documentation Style

- Repository-level and agent-facing rules documents MUST be written in English.
- Normative statements in stable rules documents MUST use BCP 14 language.
- Markdown documents MUST prefer headings, lists, and code blocks over dense prose paragraphs.
- Documentation comments and examples SHOULD use the native language or tool syntax of the subject being documented.

## Review Enforcement

- Reviewers MUST verify that all rules above are met, especially self-sufficiency, coherent-unit sizing, progressive disclosure, blocker-based references, and example/path consistency.
