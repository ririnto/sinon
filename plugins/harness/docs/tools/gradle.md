# Gradle

Use this profile for an existing Gradle project with Java sources.
Detect the tool at the root that owns `build.gradle`, `build.gradle.kts`, `settings.gradle`, or `settings.gradle.kts`.
In a monorepo, repeat the profile for each independent Gradle root.

## Native setup

Apply the Gradle `java` or `java-library` plugin already used by the target before enabling Java checks.
Add the Spotless Gradle plugin only when the target has no existing formatter with overlapping Java ownership.
Use `com.diffplug.spotless` version `8.10.2` and pin `palantir-java-format` version `2.98.0` in the target's existing version catalog or plugin convention.
Use the existing repository and plugin-management policy; add `mavenCentral()` only when the target has no equivalent repository.

Merge the Gradle fragment supplied by this profile into the existing Kotlin DSL build, or translate the same blocks to Groovy DSL.
Apply the external plugin in the target's existing `plugins` block.
Apply the core `checkstyle` plugin there when the target does not already apply it.
Preserve its project identity and tasks.

The fragment contains this Java formatter and Checkstyle setup:

```kotlin
spotless {
    java {
        palantirJavaFormat("2.98.0")
            .style("PALANTIR")
            .formatJavadoc(true)
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

checkstyle {
    toolVersion = "14.1.0"
    configFile = file("tooling/java/checkstyle.xml")
}
```

Spotless targets Java sources supplied by the Java source sets when the Java plugin is applied.
If the target uses a custom source set, set its existing Java source directory explicitly instead of widening the target.

Add Checkstyle only when the target has no existing Java static checker with overlapping ownership.
Create the target's `tooling/java/checkstyle.xml` only when it is missing.
Keep it when it is identical, and review or merge target-owned rules when it differs.
Do not overwrite target customizations.
The fragment's `configFile` path must remain aligned with the installed target path.
Set the Checkstyle engine to `14.1.0` through the target's existing Gradle configuration.
Apply the `checkstyle` plugin in the same target build before using the `checkstyle` block.
Gradle's Java plugin wires `checkstyleMain` and `checkstyleTest` into `check`.

The native source fragment enforces braces, import hygiene, public Javadoc, Java naming, line length, and prohibited direct standard streams.
It does not invent package names, coordinates, source roots, or toolchain versions.

## Existing target merge

Inspect `settings.gradle*`, `build.gradle*`, `gradle/libs.versions.toml`, `buildSrc`, and `build-logic` before editing.
Prefer the target's version catalog or convention plugin for plugin and dependency versions.
If the target already owns Spotless, Checkstyle, or an equivalent formatter, keep that owner and report the overlap.
Merge only the `spotless` block, Checkstyle configuration, and required plugin or catalog entries.
Do not overwrite a whole build script, version catalog, `buildSrc`, or `build-logic` directory.
Keep existing `check`, test, publication, repository, module, and toolchain configuration.

A Kotlin Gradle target may also select the Kotlin profile and the canonical Kotlin ruleset at `tooling/kotlin-ktlint/`.
The Kotlin ruleset remains a separate native integration and must not be replaced by Java formatting.

## Kotlin Ruleset Integration

Use this profile only when the target has Kotlin sources and a Gradle Kotlin integration.
Copy the complete `tooling/kotlin-ktlint/` module into a target-owned tooling directory, or include that directory as a Gradle composite build.
Do not copy only its JAR or place its source under `buildSrc`.

The module coordinates are `com.ririnto.sinon.harness:harness-kotlin-ktlint:1.0.0`.
It uses Kotlin `2.4.20`, ktlint engine `1.8.0`, ktlint Gradle plugin `14.2.0`, and JVM toolchain `25`.
The six rules are exposed by `com.ririnto.sinon.harness.ktlint.RuleSetProvider` through the `RuleSetProviderV3` service descriptor.

For a target-owned copy, add the produced JAR to the target's ktlint ruleset configuration after the target's existing Kotlin and ktlint plugins are applied:

```kotlin
dependencies {
    ktlintRuleset(files("$rootDir/tooling/kotlin-ktlint/build/libs/harness-kotlin-ktlint-1.0.0.jar"))
}
```

Make the consumer's `ktlintCheck` depend on the ruleset module's `jar` task when both projects are in one build.
A composite build can expose the module project, but the consumer still needs the explicit `ktlintRuleset(...)` dependency because buildSrc classes are not a ktlint runtime ruleset.
For an included build, use the module's coordinates in `ktlintRuleset("com.ririnto.sinon.harness:harness-kotlin-ktlint:1.0.0")` and add `includeBuild("tooling/kotlin-ktlint")` in `settings.gradle.kts`.
The included module's `jar` task is then part of dependency resolution and runs before the consumer's ktlint task.
Dependabot can scan a target-owned copy through the Gradle ecosystem when its `directory` points to the module root containing `gradle/libs.versions.toml`.
Keep Kotlin, the ktlint Gradle plugin, and ktlint engine updates in one reviewed change because the module's compiler and runtime API contracts are coupled.
For a target that keeps a direct local JAR path instead, add an explicit task dependency from the consumer lint task to the module `jar` task through the target's existing Gradle orchestration.
When the target cannot express a local project or task dependency, report the integration gap instead of documenting a permanently stale JAR path.

Build the module before the consumer check with the target's normal Gradle task graph.
The local JAR is target-owned build output, not a Maven publication or registry dependency.
The standalone module's tests cover six rules and the provider registration.
Keep Kotlin, the ktlint Gradle plugin, and ktlint engine updates in one reviewed change because the module's compiler and runtime API contracts are coupled.
The consumer's native `ktlintCheck` must also report a violation from a disposable Kotlin fixture before the integration is accepted.
The module's test count is reported from the Gradle test results rather than inferred from task names.

Kotlin Maven support is conditional and separate from this Gradle path.
The existing Maven profile does not claim arbitrary Maven Kotlin support.
Select Kotlin Maven only when the target already has a supported Kotlin Maven setup and a verified local ruleset classpath path, such as KtLint CLI through `exec-maven-plugin`.
Do not use a machine-specific `systemPath` or require a registry publication for local installation.

## Commands

Run `./gradlew spotlessCheck checkstyleMain checkstyleTest` for the focused Java gate when those tasks exist.
Run `./gradlew check` for the target's normal full gate.
Run `./gradlew spotlessApply` only after reviewing the resulting Java diff.

Spotless `8.10.2` requires Gradle `7.3` or newer and a Java 17 or newer runtime.
Checkstyle `14.1.0` is the selected engine version for this profile.
If the target's Java or Gradle baseline is older, retain its compatible existing tools or obtain explicit approval for an upgrade.
The GitLab catalog uses the official `gradle:9.7.1-jdk25-ubi10` image and does not install a separate Gradle distribution.
The CI catalog assumes that the target contains a checked-in `./gradlew` wrapper.
For a non-root Gradle root, add `working-directory: <existing-root>` to the GitHub run step and run `cd <existing-root> && ./gradlew check` in GitLab.
Replace `<existing-root>` with a real target-owned path before activating the catalog.

## Sources

The Spotless Gradle configuration is adapted from the official [Gradle plugin README](https://github.com/diffplug/spotless/blob/main/plugin-gradle/README.md) and [JavaExtension source](https://raw.githubusercontent.com/diffplug/spotless/main/plugin-gradle/src/main/java/com/diffplug/gradle/spotless/JavaExtension.java), read 2026-09-13.
The selected Spotless Gradle plugin version `8.10.2` is from the official [Gradle Plugin Portal](https://plugins.gradle.org/plugin/com.diffplug.spotless), read 2026-09-13.
The selected Palantir Java Format version `2.98.0` is from the official [release page](https://github.com/palantir/palantir-java-format/releases/latest), read 2026-09-13.
The Checkstyle configuration follows the official [Gradle Checkstyle plugin documentation](https://docs.gradle.org/current/userguide/checkstyle_plugin.html) and [Checkstyle checks documentation](https://checkstyle.org/checks.html), read 2026-09-13.
The Dependabot Gradle ecosystem and version catalog dependency names follow the official [Dependabot configuration options](https://docs.github.com/en/code-security/dependabot/dependabot-version-updates/configuration-options-for-the-dependabot.yml-file) and [Gradle file parser](https://github.com/dependabot/dependabot-core/blob/main/gradle/lib/dependabot/gradle/file_parser.rb), read 2026-09-14.
