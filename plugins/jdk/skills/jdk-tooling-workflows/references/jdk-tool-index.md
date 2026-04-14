---
title: JDK Tool Index
description: >-
  Reference index for common JDK tools and tool selection guidance.
---

Official documentation hub: <https://docs.oracle.com/en/java/>

## Common Tools

- `javac` - compile Java source
- `java` - launch the runtime
- `jshell` - interactive experimentation
- `javadoc` - generate API documentation
- `jdeps` - analyze package and module dependencies
- `jlink` - build custom runtime images
- `jpackage` - build native installers or application images
- `jcmd`, `jstack`, `jmap`, `jfr` - diagnostics and profiling tools
- Garbage-collection evidence still starts with standard JDK tooling, but collector-specific pause analysis, tradeoff discussion, and GC-log interpretation should stay in GC-focused guidance rather than this index.

## Selection Rule

Choose the smallest tool that directly solves the problem, then explain how later tools build on its output.
