---
title: JDK Tool Index
description: >-
  Reference index for common JDK tools and tool selection guidance.
---

Official documentation hub: [Oracle Java documentation](https://docs.oracle.com/en/java/).

## Common Tools

- `javac` - compile Java source
- `java` - launch the runtime
- `jshell` - interactive experimentation on JDK 9+
- `javadoc` - generate API documentation
- `jdeps` - analyze package and module dependencies
- `jlink` - build custom runtime images on JDK 9+
- `jpackage` - build native installers or application images on JDK 14+
- `jcmd`, `jstack`, `jmap`, `jfr` - diagnostics and profiling tools
- Garbage-collection evidence still starts with standard JDK tooling, but collector-specific pause analysis, tradeoff discussion, and GC-log interpretation should stay in GC-focused guidance rather than this index.

When the plugin is framed around the supported 8/11/17/21/25 LTS line, do not imply that every tool in this list exists on every listed release. Call out the JDK 9+ or JDK 14+ floor before recommending `jshell`, `jlink`, or `jpackage`.

## Selection Rule

Choose the smallest tool that directly solves the problem, then explain how later tools build on its output.
