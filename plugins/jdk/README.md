---
title: JDK
description: >-
  Overview of the JDK plugin, its included skills, and JVM tooling and diagnostics workflows.
---

JDK is a shared, skill-first plugin for standard JDK tooling, JVM runtime diagnostics, and garbage-collection workflows in the Sinon universal marketplace.

## Purpose

- Provide reusable JDK workflows that remain portable across Claude Code and Codex-style plugin systems.
- Keep the portable value surface in `skills/` and avoid coupling the plugin to Java-language-server setup.
- Ground guidance in official JDK, JVM, and HotSpot tooling references instead of ad-hoc advice.

## Included Skills

- `jdk-tooling-workflows`: `javac`, `java`, `jshell`, `javadoc`, `jdeps`, `jlink`, and `jpackage` workflows.
- `jdk-runtime-diagnostics`: stack traces, `jcmd`, `jstack`, `jmap`, `jfr`, and runtime incident triage.
- `jdk-gc-diagnostics`: GC symptom interpretation, collector tradeoffs, and LTS-boundary GC guidance.

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

## Design Principles

- Prefer one job per skill.
- Keep `SKILL.md` concise and procedural.
- Move dense material into `references/`.
- Treat LTS boundaries as the default frame for version-specific JDK differences.
- Prefer standard JDK and HotSpot tools before wrappers.

## Installation

Install from Sinon:

```bash
/plugin install jdk@sinon
```

For local development:

```bash
cc --plugin-dir /path/to/sinon/plugins/jdk
```
