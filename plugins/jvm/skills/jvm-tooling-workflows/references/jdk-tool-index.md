---
title: JDK Tool Index
description: >-
  Reference index for common JDK tools, version availability, selection guidance,
  and output shape reference.
---

Use this reference when the blocker is choosing among the wider JDK tool set before narrowing down to a specific command sequence, or when you need to confirm tool availability on a specific LTS baseline.

## Common Tools

| Tool | Available from | Purpose |
| --- | --- | --- |
| `javac` | All LTS (8, 11, 17, 21, 25) | Compile Java source to class files |
| `java` | All LTS | Launch JVM runtime |
| `jshell` | JDK 9+ | Interactive REPL for Java snippets |
| `javadoc` | All LTS | Generate API documentation from source |
| `jdeps` | All LTS | Analyze package and module dependencies |
| `jlink` | JDK 9+ | Build custom runtime images from modules |
| `jpackage` | JDK 14-15 incubator (`jdk.incubator.jpackage`); standard from JDK 16 (JEP 392) | Build native installers or app images |
| `jcmd` | All LTS | Send diagnostic commands to running JVMs |
| `jstack` | All LTS (deprecated) | Print thread dumps (legacy; prefer `jcmd Thread.print`) |
| `jmap` | All LTS (deprecated) | Heap inspection and dump (legacy; prefer `jcmd GC.*`) |
| `jfr` | JDK 8+ (Oracle), JDK 11+ (OpenJDK) | Flight Recorder capture and analysis |

## Selection Rule

Choose the smallest tool that directly solves the problem, then explain how later tools build on its output.

## Output Shape Reference

Use these minimal output shapes to identify the common tool results before narrowing the workflow.

`java --version`:

```text
openjdk 25.0Internal 2026-04-20
OpenJDK Runtime Environment (build 25.0Internal+12-adhoc.ririnto)
OpenJDK 64-Bit Server VM (build 25.0Internal+12-adhoc.ririnto, mixed mode, sharing)
```

`jdeps --print-module-deps app.jar`:

```text
java.base
java.net.http
java.sql
```

`jpackage --type app-image ...` app-image outputs are platform-specific: macOS creates `DemoApp.app/`, Linux creates `DemoApp/`, and Windows creates `DemoApp\` with a native launcher and bundled runtime.

## Version Gate Quick Reference

| I want to... | Minimum JDK | Tool |
| --- | --- | --- |
| Compile and run | 8 | `javac`, `java` |
| Interactive REPL | 9 | `jshell` |
| Analyze module deps | 9 | `jdeps --print-module-deps` |
| Build custom runtime image | 9 | `jlink` |
| Generate API docs | 8 | `javadoc` |
| Build native installer | 16 (standard) / 14 (incubator; not production-grade) | `jpackage` |
| Low-overhead flight recording | 11 (OpenJDK) / 8 (Oracle commercial) | `JFR.start` via `jcmd` |
