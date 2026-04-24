---
name: jvm-tooling-workflows
description: >-
  Use this skill when the user asks to "use javac", "package a Java app", "compare jlink and jpackage", "generate Javadoc", "analyze dependencies with jdeps", "start jshell", "build a custom JRE", or needs guidance on official JDK toolchain workflows.
metadata:
  title: JVM Tooling Workflows
  official_project_url: "https://docs.oracle.com/en/java/"
  reference_doc_urls:
    - "https://docs.oracle.com/javase/8/docs/technotes/tools/"
    - "https://docs.oracle.com/en/java/javase/11/docs/specs/man/index.html"
    - "https://docs.oracle.com/en/java/javase/17/docs/specs/man/index.html"
    - "https://docs.oracle.com/en/java/javase/21/docs/specs/man/index.html"
    - "https://docs.oracle.com/en/java/javase/25/docs/specs/man/index.html"
  version: "LTS"
---

## Overview

Use this skill to guide tasks that depend on official JDK command-line tools and packaging flows. The common case is choosing the smallest standard tool sequence that produces a real deliverable without hiding behind build wrappers. Prefer direct JDK commands first, then layer repository-specific build glue on top only if needed.

Treat JDK 8, 11, 17, 21, and 25 as the supported LTS reference line for this skill, and call out when a tool or packaging behavior changes across those releases.

Do not describe the full tooling surface as uniformly available across that whole line. In this skill's framing, `javac`, `java`, `javadoc`, and `jdeps` span the supported LTS line, while `jshell` and `jlink` are JDK 9+ workflows and `jpackage` is a JDK 14+ workflow.

## Common-Case Workflow

1. Read the target outcome first: compile, run, `jshell` exploration, dependency analysis, documentation, runtime image, or installer.
2. Choose the smallest official JDK tool that directly moves that outcome forward.
3. Use `jdeps` before `jlink` when module boundaries or runtime dependencies are not yet explicit.
4. Use `jpackage` only after the launcher/runtime image strategy is already stable.

## Minimal Setup

Confirm the local JDK toolchain first:

```bash
java --version
javac --version
```

For module-aware slimming or packaging, also confirm the input artifact you will analyze or package exists before invoking `jdeps`, `jlink`, or `jpackage`.

Tool availability baseline:

- `javac`, `java`, `javadoc`, `jdeps`: available across the supported LTS line used by this plugin
- `jshell`, `jlink`: JDK 9+
- `jpackage`: JDK 14+

Confirm the target JDK version before recommending `jshell`, `jlink`, or `jpackage`.

## First Runnable Commands or Code Shape

Compile and run a single class directly:

```bash
javac -d out src/main/java/com/example/demo/App.java
java -cp out com.example.demo.App
```

Use when: you need the smallest direct JDK path before bringing in Maven or Gradle.

Start a `jshell` session against compiled classes:

```bash
jshell --class-path out
```

Use when: you want to probe APIs, evaluate expressions, or validate a small runtime behavior before writing a fuller harness.

Version note: `jshell` is not part of JDK 8. Use this path only on JDK 9 and later.

## Ready-to-Adapt Templates

Dependency and module inspection with `jdeps`:

```bash
jdeps --multi-release 21 --print-module-deps app.jar
```

Use when: you want to know whether `jlink` is even justified and which modules matter.

`jshell` startup with startup script and imports:

```bash
jshell --class-path out --startup DEFAULT --startup ./config/jshell-startup.jsh
```

Use when: the workflow depends on repeatable imports, helper methods, or a prepared REPL environment rather than one-off manual evaluation.

Custom runtime image with `jlink`:

```bash
jlink \
  --add-modules java.base,java.net.http \
  --output build/runtime
```

Use when: the module graph is already known and the deliverable is a trimmed runtime.

Version note: `jlink` is a JDK 9+ workflow because it depends on the module system.

Native packaging with `jpackage`:

```bash
jpackage \
  --name DemoApp \
  --input build/libs \
  --main-jar demo-app.jar \
  --main-class com.example.demo.App \
  --type app-image
```

Use when: you want to validate the packaged launch shape before choosing a platform-specific installer type.

Version note: `jpackage` is a JDK 14+ workflow. Do not present it as an option on the JDK 8 or JDK 11 baseline.

> [!NOTE]
> On JDK 25 and later (JDK-8345185), `jpackage` no longer adds `--bind-services` to its default `jlink` options. The default now resolves to `--strip-native-commands --strip-debug --no-man-pages --no-header-files`, so the generated runtime image drops providers that were previously included by service-loader binding. When the application uses `java.util.ServiceLoader`, restore the old behavior by passing a quoted `--jlink-options` string that re-includes `--bind-services`:
>
> ```bash
> jpackage \
>     --name DemoApp \
>     --input build/libs \
>     --main-jar demo-app.jar \
>     --main-class com.example.demo.App \
>     --type app-image \
>     --jlink-options "--strip-native-commands --strip-debug --no-man-pages --no-header-files --bind-services"
> ```
>
> If `--jlink-options` is provided, it replaces the default list entirely — include the four strip flags explicitly when the operational goal is still a lean image.

Javadoc generation:

```bash
javadoc -d build/docs src/main/java/com/example/demo/*.java
```

Use when: the deliverable is documentation rather than packaging or runtime analysis.

## Validate the Result

Use the smallest validation that matches the chosen tool:

```bash
java --list-modules | grep java.base
```

- After `javac`, confirm the class output exists and `java -cp` starts successfully.
- After `jshell`, confirm the target classes are loadable from `--class-path` and the first expression evaluates successfully.
- After `jdeps`, confirm the reported modules match the application expectations.
- After `jlink`, run `build/runtime/bin/java --version` to verify the image is usable.
- After `jpackage`, verify the generated app image or installer exists for the target platform.

## Format-Critical Output Shapes

### `javac` Error Output

```text
src/main/java/com/example/App.java:5: error: cannot find symbol
    System.out.prinln("hello");
                ^
  symbol:   method prinln(String)
  location: variable out of type PrintStream
1 error
```

Read: file:line:column → error type → caret points to exact token. The `symbol` line names what could not be resolved.

### `java --version` Output

```text
openjdk 25.0Internal 2026-04-20
OpenJDK Runtime Environment (build 25.0Internal+12-adhoc.ririnto)
OpenJDK 64-Bit Server VM (build 25.0Internal+12-adhoc.ririnto, mixed mode, sharing)
```

Read: feature version (25 = JDK 25), `Server VM` = server-class JVM, `mixed mode` = JIT + interpreter.

### `jdeps --print-module-deps` Output

```text
java.base
java.net.http
java.sql
```

Read: each line is a required module name. Feed this list directly to `jlink --add-modules`. Empty output means non-modular or classpath-only dependencies.

### `jlink` Runtime Image Structure

```text
build/runtime/
├── bin/java              ← launcher (verify with build/runtime/bin/java --version)
├── conf/
├── lib/
└── release               ← version metadata
```

### `jpackage` App Image Structure (platform-specific)

**macOS:** `DemoApp.app/Contents/MacOS/DemoApp` + bundled runtime in `Contents/runtime/`
**Linux:** `DemoApp/bin/DemoApp` + runtime in `DemoApp/lib/runtime/`
**Windows:** `DemoApp\DemoApp.exe` + runtime in `DemoApp\runtime\`

### `jshell` Session Shape

```jshell
jshell> int x = 42;
x ==> 42

jshell> /exit
```

Variable declarations show `name ==> value`. Expressions auto-assign `$N`. `/exit` ends session.

### `javadoc` Output

```bash
javadoc -d build/docs src/main/java/com/example/demo/*.java
```

Produces HTML documentation tree:

```text
build/docs/
├── index.html              ← entry point (overview frame)
├── allclasses-index.html   ← alphabetical class index
├── com/
│   └── example/
│       ├── App.html        ← per-class docs
│       └── package-summary.html
├── script/
│   └── ...
└── stylesheet.css
```

Entry point is `index.html`. Verify generation succeeded when this file exists and contains the expected package/class listing.

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| choosing among the wider JDK tool set before narrowing the command sequence, or reading tool output shapes | `./references/jdk-tool-index.md` |
| module-aware slimming or native packaging details | `./references/modules-packaging.md` |

## Invariants

- MUST prefer official JDK tools before extra wrappers.
- MUST verify Java version assumptions before using newer tool features.
- MUST explain prerequisites such as modules, runtime images, or platform packaging limits.
- SHOULD show the actual sequence of tools rather than isolated commands.
- SHOULD use `jdeps` before `jlink` when module boundaries are unclear.
- MUST use `jpackage` only after the launcher and runtime-image strategy is settled.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| treating every listed tool as available on every supported LTS release | users on JDK 8 or JDK 11 can be sent to tools that do not exist there | state the version gate first: `jshell`/`jlink` on JDK 9+, `jpackage` on JDK 14+ |
| starting `jshell` without the real class path | the REPL cannot load the project classes you actually want to inspect | build or point at the compiled output first, then launch `jshell --class-path ...` |
| starting with `jpackage` | packaging too early hides runtime and module problems | inspect with `jdeps` first, then build the runtime/image chain |
| using `jlink` without explicit module knowledge | the image can miss required modules or stay larger than necessary | run `jdeps --print-module-deps` first |
| treating build wrappers as the source of truth | you lose the standard-tool sequence underneath | describe the raw JDK command flow first |
| ignoring target-platform packaging limits | native packages are platform-specific | call out platform scope before recommending `jpackage` |

## Scope Boundaries

- Activate this skill for:
  - standard JDK compile, run, REPL, inspect, document, and packaging workflows
  - choosing among `javac`, `java`, `jshell`, `jdeps`, `jlink`, and `jpackage`
  - explaining direct JDK command sequences behind wrappers
- Do not use this skill as the primary source for:
  - runtime diagnostics starting from live JVM symptoms
  - GC evidence and collector choice
  - Java language design or test-structure guidance
