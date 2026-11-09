---
metadata:
  reference:
    Spotless Gradle Plugin:
      version: 8.10.4
      url:
        - https://raw.githubusercontent.com/diffplug/spotless/gradle/8.10.4/plugin-gradle/README.md
        - https://raw.githubusercontent.com/diffplug/spotless/gradle/8.10.4/plugin-gradle/src/main/java/com/diffplug/gradle/spotless/JavaExtension.java
    Gradle Plugin Portal:
      url: https://plugins.gradle.org/plugin/com.diffplug.spotless
    Gradle Version Catalogs:
      url: https://docs.gradle.org/current/userguide/version_catalogs.html
    Gradle Wrapper:
      url: https://docs.gradle.org/current/userguide/gradle_wrapper.html
    Gradle CI:
      tag: v6.4.1
      url: https://raw.githubusercontent.com/gradle/actions/v6.4.1/setup-gradle/README.md
    ktlint:
      version: 1.8.0
      url:
        - https://raw.githubusercontent.com/ktlint/ktlint/1.8.0/documentation/release-latest/docs/api/custom-rule-set.md
        - https://raw.githubusercontent.com/ktlint/ktlint/1.8.0/ktlint-cli-ruleset-core/src/main/kotlin/com/pinterest/ktlint/cli/ruleset/core/api/RuleSetProviderV3.kt
    ktlint Gradle:
      version: 14.2.0
      url: https://raw.githubusercontent.com/JLLeitschuh/ktlint-gradle/v14.2.0/README.md
    Kotlin Releases:
      url: https://kotlinlang.org/docs/releases.html
    Kotlin Gradle Compatibility:
      url: https://kotlinlang.org/docs/gradle-configure-project.html
    Kotest:
      version: 6.2.5
      url:
        - https://kotest.io/docs/framework/project-setup.html
        - https://kotest.io/docs/framework/testing-styles.html
    Palantir Java Format:
      version: 2.103.0
      url: https://github.com/palantir/palantir-java-format/releases/tag/2.103.0
    Checkstyle:
      version: 14.3.0
      url:
        - https://github.com/checkstyle/checkstyle/releases/tag/checkstyle-14.3.0
        - https://raw.githubusercontent.com/checkstyle/checkstyle/checkstyle-14.3.0/pom.xml
    Gradle Checkstyle Plugin:
      url: https://docs.gradle.org/current/userguide/checkstyle_plugin.html
    Checkstyle Checks:
      url: https://checkstyle.org/checks.html
    Dokka Gradle:
      version: 2.2.0
      url:
        - https://kotlinlang.org/docs/dokka-gradle.html
        - https://kotlinlang.org/docs/dokka-gradle-configuration-options.html
    Javadoc:
      url: https://docs.oracle.com/en/java/javase/21/docs/specs/man/javadoc.html
    Dependabot:
      tag: v0.399.0
      url: https://raw.githubusercontent.com/dependabot/dependabot-core/v0.399.0/gradle/lib/dependabot/gradle/file_parser.rb
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
The source profile selects `com.diffplug.spotless` `8.10.4`, `palantir-java-format` `2.103.0`, and Checkstyle `14.3.0`.
Before merging any of these tools into a target, check the Gradle Plugin Portal and Maven Central for the latest stable compatible versions against the target's Gradle, Java, and version catalog constraints.
Keep compatible target-managed versions unless the task authorizes changing them.
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

The native source fragment enforces braces, unused-import hygiene, public type and callable Javadoc, Java naming, line length, and prohibited direct standard streams.
It does not invent package names, coordinates, source roots, or toolchain versions.

Merge `javadoc.gradle.kts.fragment` into the Java project build and run `./gradlew javadoc` to generate Java API documentation in `build/docs/javadoc`.
DocLint validates documentation comments and reports missing comments or tags as warnings as well as malformed markup and references.
Review and address the warnings in the context of the public contract.
Gradle's task fails on Javadoc errors, while warnings alone do not fail the build.
DocLint also warns about undocumented trivial public fields, so assess those warnings rather than adding text that merely repeats a field name.

## Existing target merge

Inspect `settings.gradle*`, `build.gradle*`, `gradle/libs.versions.toml`, `buildSrc`, and `build-logic` before editing.
Prefer the target's version catalog or convention plugin for plugin and dependency versions.
If the target already owns Spotless, Checkstyle, or an equivalent formatter, keep that owner and report the overlap.
Merge the selected formatter, checker, and documentation configuration and required plugin or catalog entries.
Do not overwrite a whole build script, version catalog, `buildSrc`, or `build-logic` directory.
Keep existing `check`, test, publication, repository, module, and toolchain configuration.

A Kotlin Gradle target may also select the Kotlin profile and the canonical Kotlin ruleset at `tooling/kotlin/`.
The Kotlin ruleset remains a separate native integration and must not be replaced by Java formatting.

## Kotlin API documentation

For a Kotlin Gradle project, add `alias(libs.plugins.dokka)` to its existing `plugins` block and merge `dokka.gradle.kts.fragment` into the project build.
The catalog fragment supplies Dokka `2.2.0`.
Use a compatible existing catalog alias or version when the target already manages Dokka.
Dokka requires Gradle 7.6 or newer and Kotlin Gradle plugin 1.9 or newer.
Apply Dokka to each project whose API should be documented.
Run `./gradlew dokkaGenerate` to generate HTML in `build/dokka/html`.
The supplied configuration fails generation on Dokka warnings.
Dokka does not report every undocumented declaration by default, so review KDoc for useful contract descriptions alongside generation.

## Kotlin Ruleset Integration

Use this profile only when the target has Kotlin sources and a Gradle Kotlin integration.
Copy `tooling/kotlin/src/` into the target's `buildSrc/src/`, including the tests.
Merge the provider line into `buildSrc/src/main/resources/META-INF/services/com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3`.
Preserve existing entries and add `com.ririnto.sinon.harness.ktlint.RuleSetProvider` only if it is absent.
Keep the descriptor target-owned and preserve its existing content and file mode.
Merge the module's dependencies, Kotlin and ktlint plugin versions, test configuration, and toolchain into the existing `buildSrc/build.gradle.kts`.
Merge its `.editorconfig` policy into `buildSrc/.editorconfig` and preserve the target's existing settings.
The Gradle sample demonstrates this integration and pins Kotlin `2.4.21`, ktlint engine `1.8.0`, ktlint Gradle plugin `14.2.0`, and JVM toolchain `25`.
Kotlin `2.4.21` is fully supported with Gradle `7.6.3` through `9.7.0`.
Keep the target's wrapper within that range.

The buildSrc JAR and its service descriptor make the rules available to the root build and Kotlin subprojects.
Apply the ktlint plugin to each Kotlin project and select the compatible ktlint engine.
Run the ruleset tests and buildSrc's own lint through `:buildSrc:check`.
The buildSrc ktlint runtime does not load its own output automatically.
To check buildSrc Kotlin sources with the custom rules, configure root ktlint to include `buildSrc/src/**/*.kt` as shown in the sample.
Retain buildSrc's own ktlint checks for its scripts and standard formatting.

The qualified root task `:check` does not select subproject checks by name.
Ensure it aggregates the existing check-bearing subprojects while retaining all existing dependencies.
The sample connects each Kotlin subproject's check to root check when its plugin is applied.
Verify the actual task graph and test a disposable custom-rule violation in both a subproject and buildSrc.
Run `./gradlew :check :buildSrc:check` and verify that a clean run passes every required check.
Keep Kotlin, the ktlint Gradle plugin, and ktlint engine updates in one reviewed change because their compiler and runtime API contracts are coupled.
The copied tests cover each registered rule and provider registration.
The tests compare native ktlint rule-engine results with Kotest matchers and run on the JUnit Platform.
Report the ruleset test count from the Gradle test results rather than inferring it from task names.

Kotlin Maven support is conditional and separate from this Gradle path.
The existing Maven profile does not claim arbitrary Maven Kotlin support.
Select Kotlin Maven only when the target already has a supported Kotlin Maven setup and a verified local ruleset classpath path, such as KtLint CLI through `exec-maven-plugin`.
Do not use a machine-specific `systemPath` or require a registry publication for local installation.

## Commands

### Node-Side Markdown And Hook Scripts

Use the target's package manager and dependency conventions for Node-side tooling at each selected Gradle root.
The following pnpm-specific merge and hook instructions apply when the target uses pnpm.
For another package manager, adapt the dependencies and scripts to its conventions while preserving the target's existing configuration.
`tooling/gradle/package.json` is script and dependency merge guidance, following the pnpm profile's `package.fragment.json` convention.
Merge its entries into the target manifest.
Preserve existing identity, commands, dependencies, workspaces, and package-manager settings.
Create a minimal private target manifest only when none exists, keeping project identity target-owned.
For an existing script name, retain its command and report the required composition before changing that owner.

The supplied script surface for a target without `buildSrc` is:

| Script | Command |
| --- | --- |
| `check` | `run-p check:*` |
| `check:markdownlint-cli2` | `markdownlint-cli2` |
| `fix` | `run-p fix:*` |
| `fix:markdownlint-cli2` | `markdownlint-cli2 --fix` |
| `gradle:check` | `./gradlew check` |
| `gradle:ktlintCheck` | `./gradlew ktlintCheck` |
| `pre-commit` | `run-s check gradle:ktlintCheck` |
| `pre-push` | `run-s check gradle:check` |
| `prepare` | `husky` |

The package scripts finish Node-side checks successfully before invoking Gradle.
For pnpm targets, install `tooling/gradle/.husky/` through the shared [Husky integration](pnpm.md#git-hook-integration) so Git hooks delegate to the matching pnpm scripts.
For other package managers, adapt hook commands to invoke the corresponding target scripts.
Keep the checked-in wrapper at the selected root and preserve its task graph.

For a target with `buildSrc`, replace the Gradle scripts and hook commands with the following:

| Script | Command |
| --- | --- |
| `gradle:check` | `./gradlew :check :buildSrc:check` |
| `pre-commit` | `run-s check gradle:check` |
| `pre-push` | `run-s check gradle:check` |

Omit the separate lint script from this setup.
Ensure that both root and `buildSrc` checks include their existing ktlint gates.
Verify that both hooks retain the Node checks and run the exact two-task Gradle command.
Retain existing target script commands when composing these gates.
The generic manifest remains suitable for targets without `buildSrc`, where the explicit `:buildSrc` tasks do not exist.

Install the canonical Markdownlint configuration and `rules/` from `tooling/pnpm/`, omitting `*.test.ts` files.
For pnpm targets, use the [bounded manifest and catalog merge](pnpm.md#bounded-manifest-merge), selecting only the development dependencies in this manifest.
For pnpm targets, resolve catalog references in the target's `pnpm-workspace.yaml` before running `pnpm install`.
For pnpm targets, use missing entries from `pnpm-workspace.fragment.yaml` as merge guidance, preserving compatible target versions and unrelated workspace settings.
Verify supported dependency versions through the npm registry before adding or upgrading them in a target.
Do not select the Oxc or TypeScript profile solely to obtain Markdownlint.
Use supported Node LTS with native TypeScript loading and compatible dependency engines.
The [pnpm profile](pnpm.md#bounded-manifest-merge) owns the shared custom-rule runtime requirements.

### Native Gradle Commands

Run `./gradlew spotlessCheck checkstyleMain checkstyleTest` for the focused Java gate when those tasks exist.
For a target without `buildSrc`, run `./gradlew check` for the full gate.
For a target with `buildSrc` configured above, run `./gradlew :check :buildSrc:check` for the full gate.
Run `./gradlew spotlessApply` only after reviewing the resulting Java diff.

Spotless `8.10.4` requires Gradle `7.3` or newer and a Java 17 or newer runtime.
Checkstyle `14.3.0` is the selected engine version for this profile.
Its lint process requires Java 21 or newer, even when the target compiles for an older Java release.
Use the target's supported lint runtime or retain a compatible Checkstyle engine.
If the target's Java or Gradle baseline is older, retain its compatible existing tools or obtain explicit approval for an upgrade.
The GitLab catalog uses the `gradle:9.8.2-jdk25-ubi10` image for its Java runtime and invokes the target's `./gradlew check`.
The target's checked-in wrapper selects the executed Gradle version and downloads that distribution when necessary.
The image's bundled Gradle version does not select the wrapper's version.
Gradle has no official LTS line.
Gradle `9.8.2` falls outside Kotlin `2.4.21`'s fully supported range.
Before adopting that image or the GitHub action versions, check their official releases and the target's Gradle wrapper and Java policy.
Keep compatible target pins.
The CI catalog assumes that the target contains a checked-in `./gradlew` wrapper.
For a target with `buildSrc`, change the catalog's Gradle command to `./gradlew :check :buildSrc:check` after wiring the root and subproject checks as described above.
For a non-root Gradle root, add `working-directory: <existing-root>` to the GitHub run step and prefix the selected GitLab command with `cd <existing-root> &&`.
Replace `<existing-root>` with a real target-owned path before activating the catalog.
