# JDK tool workflow templates

Dependency and module inspection with `jdeps`:

```sh
jdeps --multi-release 21 --print-module-deps app.jar
```

Use when: you want to know whether `jlink` is even justified and which modules matter.

`jshell` startup with startup script and imports:

```sh
jshell --class-path out --startup DEFAULT --startup ./config/jshell-startup.jsh
```

Use when: the workflow depends on repeatable imports, helper methods, or a prepared REPL environment rather than one-off manual evaluation.

Custom runtime image with `jlink`:

```sh
jlink \
  --add-modules java.base,java.net.http \
  --output build/runtime
```

Use when: the module graph is already known and the deliverable is a trimmed runtime.

Version note: `jlink` is a JDK 9+ workflow because it depends on the module system.

Native packaging with `jpackage`:

```sh
jpackage \
  --name DemoApp \
  --input build/libs \
  --main-jar demo-app.jar \
  --main-class com.example.demo.App \
  --type app-image
```

Use when: you want to validate the packaged launch shape before choosing a platform-specific installer type.

Version note: `jpackage` is a standard JDK 16+ workflow.
JDK 14-15 shipped an incubating `jpackage` module (`jdk.incubator.jpackage`).
Do not present that incubator form as the normal production baseline, and do not present `jpackage` as an option on the JDK 8 or JDK 11 baseline.

> [!NOTE]
>
> On JDK 25, the default `jpackage` options do not include `--bind-services`.
> The default now resolves to `--strip-native-commands --strip-debug --no-man-pages --no-header-files`, so the generated runtime image drops providers that were previously included by service-loader binding.
> When the application uses `java.util.ServiceLoader`, restore the old behavior by passing a quoted `--jlink-options` string that re-includes `--bind-services`:
>
> ```sh
> jpackage \
>     --name DemoApp \
>     --input build/libs \
>     --main-jar demo-app.jar \
>     --main-class com.example.demo.App \
>     --type app-image \
>     --jlink-options "--strip-native-commands --strip-debug --no-man-pages --no-header-files --bind-services"
> ```
>
> If `--jlink-options` is provided, it replaces the default list entirely - include the four strip flags explicitly when the operational goal is still a lean image.

Javadoc generation:

```sh
javadoc -d build/docs src/main/java/com/example/demo/*.java
```

Use when: the deliverable is documentation rather than packaging or runtime analysis.
