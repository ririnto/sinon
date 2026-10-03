---
description: >-
  Overview of the JVM plugin, its included skills, and JVM tooling and diagnostics workflows.
metadata:
  reference:
    Claude Code plugins:
      url: https://code.claude.com/docs/en/plugins/manifest-reference
    OpenAI Codex plugins:
      url: https://developers.openai.com/plugins/build/plugins.md
---

# JVM

JVM provides skills for standard JDK tools, runtime diagnostics, and garbage-collection analysis.

This plugin treats JDK 8, 11, 17, 21, and 25 as the supported LTS reference line.

## Included Skills

- `jvm-tooling-workflows`: `javac`, `java`, `javadoc`, and `jdeps` across the supported LTS
  line, plus version-gated `jshell` (JDK 9+), `jlink` (JDK 9+), and `jpackage` workflows.
  - Treat `jpackage` as standard from JDK 16.
    - JDK 14-15 shipped it only as an incubating tool.
- `jvm-runtime-diagnostics`: runtime incident triage with stack traces, thread dumps, `jcmd`, JFR, and memory-pressure evidence.
  - `jstack` and `jmap` remain legacy or narrower-purpose paths.
- `jvm-gc-diagnostics`: GC symptom interpretation, collector tradeoffs, and LTS-boundary GC guidance.

## When to Use Which Skill

- Standard compile, packaging, module, and runtime-image questions belong in the tooling workflow guidance.
- Live JVM incident triage belongs in the runtime diagnostics guidance.
- Collector-specific pause analysis, GC-log reading, and GC tradeoff questions belong in the GC-focused guidance.

Select the skill for the current question.
Tooling, runtime triage, and GC analysis are not mandatory sequential stages.
Use existing evidence before starting a new capture.

## Runtime Model

Claude Code loads this package through the Sinon marketplace entry and its default component paths.
Codex uses `.codex-plugin/plugin.json` to load skills from the default `skills/` directory.

## Plugin Layout

```text
plugins/jvm/
+-- .codex-plugin/plugin.json
+-- README.md
+-- skills/
    +-- jvm-gc-diagnostics/
    |   +-- SKILL.md
    |   +-- references/
    +-- jvm-runtime-diagnostics/
    |   +-- SKILL.md
    |   +-- references/
    +-- jvm-tooling-workflows/
        +-- SKILL.md
        +-- references/
```

## Installation

Install from Sinon:

```sh
claude plugin install jvm@sinon
```

For local development:

```sh
claude --plugin-dir /path/to/sinon/plugins/jvm
```

## Scope Notes

This plugin intentionally focuses on standard JDK tooling, JVM runtime diagnostics, and garbage-collection guidance.
It does not cover:

- Java language syntax or API design
- framework-specific Spring or application instrumentation workflows
- Java language-server setup
