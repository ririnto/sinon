---
metadata:
  reference:
    Spotless Gradle Plugin:
      version: 8.10.3
      url:
        - https://raw.githubusercontent.com/diffplug/spotless/gradle/8.10.3/plugin-gradle/README.md
        - https://raw.githubusercontent.com/diffplug/spotless/gradle/8.10.3/plugin-gradle/src/main/java/com/diffplug/gradle/spotless/JavaExtension.java
    Gradle Plugin Portal:
      url: https://plugins.gradle.org/plugin/com.diffplug.spotless
    Gradle Version Catalogs:
      url: https://docs.gradle.org/current/userguide/version_catalogs.html
    Gradle CI:
      tag: v6.4.0
      url: https://raw.githubusercontent.com/gradle/actions/v6.4.0/setup-gradle/README.md
    ktlint:
      version: 1.8.0
      url:
        - https://raw.githubusercontent.com/ktlint/ktlint/1.8.0/documentation/release-latest/docs/api/custom-rule-set.md
        - https://raw.githubusercontent.com/ktlint/ktlint/1.8.0/ktlint-cli-ruleset-core/src/main/kotlin/com/pinterest/ktlint/cli/ruleset/core/api/RuleSetProviderV3.kt
    ktlint Gradle:
      version: 14.2.0
      url: https://raw.githubusercontent.com/JLLeitschuh/ktlint-gradle/v14.2.0/README.md
    Kotest:
      version: 6.2.5
      url:
        - https://kotest.io/docs/framework/project-setup.html
        - https://kotest.io/docs/framework/testing-styles.html
    Palantir Java Format:
      version: 2.101.0
      url: https://github.com/palantir/palantir-java-format/releases/tag/2.101.0
    Checkstyle:
      version: 14.3.0
      url:
        - https://github.com/checkstyle/checkstyle/releases/tag/checkstyle-14.3.0
        - https://raw.githubusercontent.com/checkstyle/checkstyle/checkstyle-14.3.0/pom.xml
    Gradle Checkstyle Plugin:
      url: https://docs.gradle.org/current/userguide/checkstyle_plugin.html
    Checkstyle Checks:
      url: https://checkstyle.org/checks.html
    Dependabot:
      tag: v0.397.0
      url: https://raw.githubusercontent.com/dependabot/dependabot-core/v0.397.0/gradle/lib/dependabot/gradle/file_parser.rb
    Dependabot Configuration:
      url: https://docs.github.com/en/code-security/dependabot/dependabot-version-updates/configuration-options-for-the-dependabot.yml-file
---

# Gradle

Use this profile for an existing Gradle project with Java sources.
Detect the tool at the root that owns `build.gradle`, `build.gradle.kts`, `settings.gradle`, or `settings.gradle.kts`.
In a monorepo, repeat the profile for each independent Gradle root.

## Native setup

Apply the Gradle `java` or `java-library` plugin already used by the target before enabling Java checks.
Add the Spotless Gradle plugin only when the target has no existing formatter with overlapping Java ownership.
The fragment selects `com.diffplug.spotless` `8.10.3` and `palantir-java-format` `2.101.0` as profile baselines.
They do not establish current releases.
Before adding or upgrading Spotless, Palantir Java Format, or Checkstyle, check the Gradle Plugin Portal and Maven Central for the latest stable compatible versions against the target's Gradle, Java, and version catalog constraints.
Use the checked versions for a new integration, and keep compatible target-managed versions unless the task authorizes changing them.
Use the existing repository and plugin-management policy.
Add `mavenCentral()` only when the target has no equivalent repository.

Merge `libs.versions.toml.fragment` into the target's catalog before merging the Gradle build fragment.
Use existing aliases when they already own these dependencies, and adjust the fragment's accessors to match.
If no catalog exists, create `gradle/libs.versions.toml` with only the selected entries.
Merge the build fragment into the existing Kotlin DSL build, or translate the same blocks to Groovy DSL.
Apply `alias(libs.plugins.spotless)` in the target's existing `plugins` block when using the supplied catalog alias.
Apply the core `checkstyle` plugin there when the target does not already apply it.
Preserve its project identity and tasks.

The fragment contains this Java formatter and Checkstyle setup:

```kotlin
spotless {
    java {
        palantirJavaFormat(libs.versions.palantirJavaFormat.get())
            .style("PALANTIR")
            .formatJavadoc(true)
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

checkstyle {
    toolVersion = libs.versions.checkstyle.get()
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
Set the Checkstyle engine to `14.3.0` through the target's existing Gradle configuration.
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
The registered rules are exposed by `com.ririnto.sinon.harness.ktlint.RuleSetProvider` through the `RuleSetProviderV3` service descriptor.

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
The standalone module tests each registered rule and provider registration.
Its Kotest specs use constructor declarations and typed companion properties.
The tests compare native ktlint rule-engine results with Kotest matchers.
Kotest uses the JUnit Platform on the JVM.
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

Spotless `8.10.3` requires Gradle `7.3` or newer and a Java 17 or newer runtime.
Checkstyle `14.3.0` is the selected engine version for this profile.
Its lint process requires Java 21 or newer, even when the target compiles for an older Java release.
Use the target's supported lint runtime or retain a compatible Checkstyle engine.
If the target's Java or Gradle baseline is older, retain its compatible existing tools or obtain explicit approval for an upgrade.
The GitLab catalog uses the `gradle:9.8.0-jdk25-ubi10` image and does not install a separate Gradle distribution.
Before adopting that image or the GitHub action versions, check their official releases and the target's Gradle wrapper and Java policy.
Keep compatible target pins.
The CI catalog assumes that the target contains a checked-in `./gradlew` wrapper.
For a non-root Gradle root, add `working-directory: <existing-root>` to the GitHub run step and run `cd <existing-root> && ./gradlew check` in GitLab.
Replace `<existing-root>` with a real target-owned path before activating the catalog.
