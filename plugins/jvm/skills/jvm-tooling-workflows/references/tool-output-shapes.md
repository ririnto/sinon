# JDK tool output shapes

## `javac` Error Output

```text
src/main/java/com/example/App.java:5: error: cannot find symbol
    System.out.prinln("hello");
                ^
  symbol:   method prinln(String)
  location: variable out of type PrintStream
1 error
```

Read: file:line:column → error type → caret points to exact token.
The `symbol` line names what could not be resolved.

## `java --version` Output

```text
openjdk 25.0.3 2026-04-21
OpenJDK Runtime Environment (build 25.0.3+9)
OpenJDK 64-Bit Server VM (build 25.0.3+9, mixed mode, sharing)
```

Read: feature version (25 = JDK 25), `Server VM` = server-class JVM, `mixed mode` = JIT + interpreter.

## `jdeps --print-module-deps` Output

```text
java.base
java.net.http
java.sql
```

Read: each line is a required module name.
Feed this list directly to `jlink --add-modules`.
Empty output means non-modular or classpath-only dependencies.

## `jlink` Runtime Image Structure

```text
build/runtime/
+-- bin/java              ← launcher (verify with build/runtime/bin/java --version)
+-- conf/
+-- lib/
+-- release               ← version metadata
```

## `jpackage` App Image Structure (platform-specific)

macOS: `DemoApp.app/Contents/MacOS/DemoApp` + bundled runtime in `Contents/runtime/`
Linux: `DemoApp/bin/DemoApp` + runtime in `DemoApp/lib/runtime/`
Windows: `DemoApp\DemoApp.exe` + runtime in `DemoApp\runtime\`

## `jshell` Session Shape

```jshell
jshell> int x = 42;
x ==> 42

jshell> /exit
```

Variable declarations show `name ==> value`.
Expressions auto-assign `$N`.
`/exit` ends session.

## `javadoc` Output

```sh
javadoc -d build/docs src/main/java/com/example/demo/*.java
```

Produces HTML documentation tree:

```text
build/docs/
+-- index.html              ← entry point (overview frame)
+-- allclasses-index.html   ← alphabetical class index
+-- com/
|   +-- example/
|       +-- App.html        ← per-class docs
|       +-- package-summary.html
+-- script/
|   +-- ...
+-- stylesheet.css
```

Entry point is `index.html`.
Verify generation succeeded when this file exists and contains the expected package/class listing.
