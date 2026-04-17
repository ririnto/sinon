---
title: Sinon Project Rules
description: Stable repository rules for plugin packaging and skill authoring in the Sinon marketplace.
---

Sinon is a marketplace repository for Claude Code plugins and Agent Skills. These rules govern repository layout, skill authoring, and documentation posture. Normative keywords (MUST, MUST NOT, SHOULD, SHOULD NOT, MAY) follow BCP 14. All repository-level and agent-facing rules documents MUST be written in English.

## Canonical files and symlinks

- `CLAUDE.md` is the canonical root rules document. `AGENTS.md` is a symlink to it; treat them as one document, not parallel copies.
- `.claude/skills/` is the canonical repository-local skills directory. `.agents/skills/` is a symlink to it; treat them as one inventory.
- Root-level documentation MUST describe repository-wide structure and rules, not fast-changing plugin details.

## Repository layout

- Plugins MUST live under `plugins/`.
- Each plugin MAY expose multiple runtime manifests from the same plugin root.
- Runtime-specific marketplace metadata MUST stay aligned with the plugin content it publishes.

## Authoring Agent Skills

When the task is to create, edit, review, refactor, validate, or package an Agent Skill for this repository, **YOU MUST** load the local `skill-authoring` skill from `.claude/skills/skill-authoring/`. The mirrored path `.agents/skills/skill-authoring/` resolves to the same content via symlink.

Sinon treats the **agentskills.io loading model** as the top-level basis for local skill rules. Progressive disclosure applies at three levels:

1. Skill `description` metadata is the activation trigger and SHOULD use user-intent phrasing such as "Use this skill when…".
2. `SKILL.md` is the common-path entrypoint loaded at activation and MUST be self-sufficient for the ordinary task.
3. `references/`, `assets/`, and `scripts/` hold on-demand additive depth and MUST NOT be treated as always-loaded context.

A skill SHOULD cover one coherent unit of work. When sibling skills share a common path and differ only by host, vendor, or platform, they SHOULD be merged and the deltas SHOULD move to focused references.

## `SKILL.md` contract

A skill MUST remain usable when installed by itself and MUST NOT require another skill as a prerequisite or routing handoff. A single `SKILL.md` MAY cover multiple hosts or platforms when the user job is the same and the common path stays in `SKILL.md`. Plugin-level inventories MAY list bundled skills, but each skill entrypoint MUST remain self-sufficient.

`SKILL.md` MUST contain:

- A `name` field exactly matching the skill directory basename.
- The activation surface, common-case workflow, decision points, and first safe commands.
- Representative templates, copy-adaptable examples, invariants, pitfalls, and scope boundaries.
- Primary authoring conventions users apply during ordinary use of the skill.
- The shared workflow and first safe commands when the skill spans multiple hosts or platforms.
- Format-critical output shapes.
- Brief pointers to `references/` indexed by concrete blocker or job.

`SKILL.md` MUST NOT:

- Move common-case guidance to `references/` solely to shrink `SKILL.md`.
- Describe adjacent-domain exclusions as "jump to skill X" — state them in domain terms.
- Degrade into a generic essay or background article.

`SKILL.md` SHOULD favor direct, imperative guidance over tutorial narration and SHOULD keep a shallow directory structure so agents can discover material quickly.

## `references/` contract

`references/` MUST contain additive depth only: extended examples, host-specific template paths and command variants, operational caveats, version boundaries, and edge-case decision material. Each reference file MUST be a purpose-complete unit that states its purpose and the condition for opening it, and MUST stand alone for one specific blocker or job. `references/` MAY assume the reader has already activated `SKILL.md`.

`references/` MUST NOT:

- Hold material required for the common case or primary purpose.
- Repeat canonical templates, workflow steps, invariants, or pitfalls owned by `SKILL.md`.
- Act as duplicate standalone skill files.

`references/` SHOULD avoid chains; references commonly read together for the same blocker SHOULD be merged when splitting does not materially reduce scanning cost. If a skill keeps only one reference file and that reference sits on the common path, its durable content SHOULD be folded back into `SKILL.md`.

## Coding-related skills

Coding-related skills MUST weight code, commands, templates, and concrete examples over explanatory prose.

- Every important rule SHOULD be anchored by runnable or readily adaptable code or commands.
- Default code-organization and authoring conventions central to the skill MUST appear in `SKILL.md` with concrete examples.
- Short common-path examples belong in `SKILL.md`; longer or conditional examples belong in purpose-complete references.
- Broken-versus-correct examples SHOULD be preferred over abstract warnings.
- Prose around templates SHOULD be compressed to the minimum needed for safe use.
- Each reference file SHOULD include at least one concrete additive example, command, config snippet, diff, or output shape tied to its blocker, and MUST NOT degrade into a prose-only checklist.

Command-heavy skills MUST present the primary decision path and first safe commands in `SKILL.md`. Command syntax in `SKILL.md` MUST be copyable and explicit. Operational cautions MUST stay adjacent to the commands they constrain. Long command catalogs, compatibility matrices, and secondary option tables SHOULD live in `references/`.

When a skill documents multiple valid workflows for the same asset class, each workflow MUST keep its own commands, paths, and output tree internally consistent. If a skill distinguishes direct-source assets from generated or rendered assets, the documentation MUST name that boundary explicitly and keep validation, render, and provisioning paths aligned to the correct side.

## Documentation style

- Markdown documents MUST prefer headings, lists, and code blocks over dense prose.
- Normative statements in stable rules documents MUST use BCP 14 language.
- Documentation examples SHOULD use the native language or tool syntax of the subject being documented.

## Review checklist

Reviewers MUST verify: skill self-sufficiency, coherent-unit sizing, progressive disclosure, blocker-based (not topic-label) references, example and path consistency across workflows, and strict separation of `SKILL.md` common-case content from `references/` additive depth.