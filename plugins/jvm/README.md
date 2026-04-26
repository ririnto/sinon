---
title: JVM
description: >-
  Overview of the JVM plugin, its included skills, and JVM tooling and diagnostics workflows.
---

JVM is a shared, skill-first plugin for standard JDK tooling, JVM runtime diagnostics, and garbage-collection workflows in the Sinon universal marketplace.

This plugin treats JDK 8, 11, 17, 21, and 25 as the supported LTS reference line.

## Purpose

- Provide reusable JVM workflows that remain portable across Claude Code and Codex-style plugin systems.
- Keep the portable value surface in `skills/` and avoid coupling the plugin to Java-language-server setup.
- Ground guidance in official JDK, JVM, and HotSpot tooling references instead of ad-hoc advice.

## Included Skills

- `jvm-tooling-workflows`: `javac`, `java`, `javadoc`, and `jdeps` across the supported LTS line, plus version-gated `jshell` (JDK 9+), `jlink` (JDK 9+), and `jpackage` workflows. Treat `jpackage` as standard from JDK 16; JDK 14-15 shipped it only as an incubating tool.
- `jvm-runtime-diagnostics`: stack traces, `jcmd`, `jstack`, `jmap`, `jfr`, and runtime incident triage.
- `jvm-gc-diagnostics`: GC symptom interpretation, collector tradeoffs, and LTS-boundary GC guidance.

## When to Use Which Skill

- Standard compile, packaging, module, and runtime-image questions belong in the tooling workflow guidance.
- Live JVM incident triage belongs in the runtime diagnostics guidance.
- Collector-specific pause analysis, GC-log reading, and GC tradeoff questions belong in the GC-focused guidance.

Typical workflow:

1. Start with tooling guidance for compile, packaging, module, and runtime-image questions.
2. Move to runtime diagnostics when there is already stack-trace, thread-dump, JFR, or heap-pressure evidence.
3. Escalate to GC-focused guidance only when the question is specifically about garbage collection, pause behavior, or collector choice.

## Runtime Model

This plugin uses one shared plugin root with two thin runtime manifests:

- `.claude-plugin/plugin.json`
- `.codex-plugin/plugin.json`

The actual reusable content lives beside those manifests at the plugin root.

## Plugin Layout

```text
plugins/jvm/
├── .claude-plugin/plugin.json
├── .codex-plugin/plugin.json
├── README.md
└── skills/
    ├── jvm-gc-diagnostics/
    │   ├── SKILL.md
    │   ├── agents/openai.yaml
    │   └── references/
    ├── jvm-runtime-diagnostics/
    │   ├── SKILL.md
    │   ├── agents/openai.yaml
    │   └── references/
    └── jvm-tooling-workflows/
        ├── SKILL.md
        ├── agents/openai.yaml
        └── references/
```

## Shipped Surfaces

- The plugin ships three reusable skills under `skills/`.
- Each skill ships focused `references/` for blocker-specific JVM details and an `agents/openai.yaml` runtime surface for OpenAI-style agent packaging.
- The plugin ships no plugin-root `agents/` directory.
- The plugin does not ship commands, hooks, MCP servers, LSP servers, or custom runtime data surfaces.

## Design Principles

- Prefer one job per skill.
- Keep `SKILL.md` concise and procedural.
- Move dense material into `references/`.
- Treat LTS boundaries as the default frame for version-specific JDK differences.
- Prefer standard JDK and HotSpot tools before wrappers.

## Installation

Install from Sinon:

```bash
/plugin install jvm@sinon
```

For local development:

```bash
claude --plugin-dir /path/to/sinon/plugins/jvm
```

## Scope Notes

This plugin intentionally focuses on standard JDK tooling, JVM runtime diagnostics, and garbage-collection guidance. It does not cover:

- Java language syntax or API design
- framework-specific Spring or application instrumentation workflows
- Java language-server setup
