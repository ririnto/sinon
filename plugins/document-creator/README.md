---
description: >-
  Plugin hosting skills for authoring AI-consumable engineering documents with structured requirements, scenarios, and acceptance criteria.
  Currently provides `SPEC.md` authoring.
---

# Document Creator

Document Creator is a skill-first plugin for authoring AI-consumable engineering documents in the Sinon Claude marketplace.

## Purpose

Provide reusable skills for authoring engineering documents that are structured, bounded, and testable.
Focus on documents that capture requirements, design intent, and acceptance criteria in formats that agents and engineers can use without ambiguity or guessing.

## Included Skills

- `spec-creator`: Turn rough ideas, feature requests, interviews, or planning notes into one standalone `SPEC.md` with requirements, scenarios, and acceptance criteria.

## How the Skill Branches

Use `spec-creator` when the requested deliverable is the specification document itself: "create `SPEC.md`", "write a spec", "draft a product spec", "create structured requirements", "write acceptance criteria", "make an RFC-style spec", or "make an SRS-style spec".

- `references/spec-template.md` - canonical `SPEC.md` skeleton and section rules.
- `references/requirements-style.md` - requirement IDs, RFC 2119 language, and EARS patterns.
- `references/scenarios-and-acceptance.md` - scenario and acceptance-criteria guidance.
- `references/quality-checklist.md` - review checklist for spec completeness and quality.

## Runtime Model

This plugin uses `.claude-plugin/plugin.json` at the plugin root.

## Plugin Layout

```text
plugins/document-creator/
+-- .claude-plugin/plugin.json
+-- README.md
+-- skills/
    +-- spec-creator/
        +-- SKILL.md
        +-- references/
            +-- quality-checklist.md
            +-- requirements-style.md
            +-- scenarios-and-acceptance.md
            +-- spec-template.md
```

## Shipped Surfaces

- The plugin ships reusable skills under `skills/`.
- Each skill is self-contained with a `SKILL.md` entrypoint and `references/` for additive depth.

## Design Principles

- Prefer one coherent user job per skill.
- Keep the common path self-sufficient inside `SKILL.md` and move only additive depth into `references/`.

## Installation

When this plugin is published in the Sinon marketplace, install it with:

```sh
claude plugin install document-creator@sinon
```

For current local development:

```sh
claude --plugin-dir /path/to/sinon/plugins/document-creator
```

## Scope Notes

This plugin focuses on authoring an individual AI-consumable specification document well.
It does not run:

- gated research, approval, implementation, implementation review, or verification lifecycles
- Git branch management, CI/CD pipeline design, or project management.
- Document templates for non-engineering contexts (e.g., marketing, legal, general communication).
