---
metadata:
  reference:
    Spotless Maven Plugin:
      version: 3.10.2
      url: https://github.com/diffplug/spotless/blob/maven/3.10.2/plugin-maven/README.md
    Spotless Releases:
      url: https://github.com/diffplug/spotless/releases
    Palantir Java Format:
      version: 2.98.0
      url: https://github.com/palantir/palantir-java-format/releases/tag/2.98.0
    Checkstyle:
      version: 14.1.0
      url: https://github.com/checkstyle/checkstyle/releases/tag/checkstyle-14.1.0
    Maven Checkstyle Plugin:
      url: https://maven.apache.org/plugins/maven-checkstyle-plugin/
    Checkstyle Checks:
      url: https://checkstyle.org/checks.html
---

# Maven

Use this profile for an existing Maven project with Java sources.
Detect the tool at the root that owns `pom.xml`.
In a monorepo, repeat the profile for each independent Maven root.

## Native setup

Add the Spotless Maven plugin only when the target has no existing formatter with overlapping Java ownership.
The fragment selects `com.diffplug.spotless:spotless-maven-plugin` `3.10.2` and `palantir-java-format` `2.98.0` as profile baselines.
They do not establish current releases.
Before adding or upgrading Spotless, Palantir Java Format, or Checkstyle, check Maven Central for the latest stable compatible versions against the target's Java baseline, parent POM, BOM, and dependency management.
Use the checked versions for a new integration, and keep compatible target-managed versions unless the task authorizes changing them.
Use the existing repositories and plugin-management policy.

Add the following plugin configuration to the existing `<build><plugins>` section, preserving all coordinates and existing plugins:

```xml
<plugin>
  <groupId>com.diffplug.spotless</groupId>
  <artifactId>spotless-maven-plugin</artifactId>
  <version>3.10.2</version>
  <configuration>
    <java>
      <palantirJavaFormat>
        <version>2.98.0</version>
        <style>PALANTIR</style>
        <formatJavadoc>true</formatJavadoc>
      </palantirJavaFormat>
      <removeUnusedImports />
      <trimTrailingWhitespace />
      <endWithNewline />
    </java>
  </configuration>
  <executions>
    <execution>
      <id>spotless-check</id>
      <phase>verify</phase>
      <goals>
        <goal>check</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

Spotless uses the target's Java source roots and does not require a generated Java package or project identity.
Use an explicit target only when the target has custom source layout that its existing build already defines.

Add the Maven Checkstyle plugin only when the target has no existing Java static checker with overlapping ownership.
Create the target's `tooling/java/checkstyle.xml` only when it is missing.
Keep it when it is identical, and review or merge target-owned rules when it differs.
Do not overwrite target customizations.
The fragment's `configLocation` path must remain aligned with the installed target path.
Set the Checkstyle engine to `14.1.0` through the plugin dependency when the target permits a managed engine override.
Bind the `checkstyle:check` goal to the target's existing validation phase without replacing its lifecycle.

The native configuration enforces braces, import hygiene, public Javadoc, Java naming, line length, and prohibited direct standard streams.
It does not invent Maven coordinates, Java packages, release levels, module names, or test runners.

## Existing target merge

Inspect the complete `pom.xml`, parent POM, profiles, dependency management, plugin management, wrapper, and Java release properties before editing.
Prefer an existing version property or management section for plugin versions.
If the target already owns Spotless, Checkstyle, or an equivalent formatter, keep that owner and report the overlap.
Merge only the required plugin, properties, Checkstyle configuration, and lifecycle execution.
Do not overwrite the whole POM or replace an incompatible parent, compiler release, repository, module, test, or publication configuration.
Keep the target's existing groupId, artifactId, version, source roots, and toolchain.

Kotlin Maven support is conditional.
Select it only when the target already has a supported Kotlin Maven plugin and a verified way to attach the canonical ruleset at `tooling/kotlin-ktlint/`.
This Java profile does not claim to configure arbitrary Kotlin Maven builds.

For an existing supported Kotlin Maven build, copy the complete `tooling/kotlin-ktlint/` module into a target-owned tooling directory and build its JAR with the target's normal Gradle task.
Run KtLint through the target's existing Maven integration, such as `exec-maven-plugin`, and put the produced JAR on the KtLint CLI classpath.
Make the Maven lint execution depend on the native Gradle `jar` task through the target's existing build orchestration when the two builds are coupled.
If that orchestration cannot express the dependency, report the gap rather than requiring a hidden manual build step.
KtLint discovers `com.ririnto.sinon.harness.ktlint.RuleSetProvider` through its `RuleSetProviderV3` service descriptor.
Use the module coordinates `com.ririnto.sinon.harness:harness-kotlin-ktlint:1.0.0` only as the local artifact identity.
Do not claim that arbitrary Maven builds can consume the ruleset.
Do not use a machine-specific `systemPath` or require a Maven publication for local installation.
If the target cannot express the local JAR classpath and its build dependency, report the integration gap instead of documenting a stale prebuilt JAR.
Keep this Kotlin path independent from the Java Spotless and Checkstyle setup above.

## Commands

Run `./mvnw spotless:check checkstyle:check` for the focused Java gate when the wrapper and goals are available.
Run `./mvnw verify` for the target's normal full lifecycle.
Run `./mvnw spotless:apply` only after reviewing the resulting Java diff.

Spotless Maven plugin `3.10.2` requires Maven to run on Java 17 or newer.
Checkstyle engine `14.1.0` is the selected version for this profile.
If the target's Java baseline is older, retain compatible existing tools or obtain explicit approval for an upgrade.

This profile does not create Git hooks.
Preserve existing hooks and configure a native hook only when the target explicitly selects one.
The GitLab catalog's `maven:3.9.16-eclipse-temurin-25` image records a profile baseline, not the current stable runtime.
Before adopting it, check the official Maven and JDK image releases against the target's wrapper and Java baseline.
The CI catalog assumes that the target contains a checked-in `./mvnw` wrapper.
For a non-root Maven module, add `working-directory: <existing-root>` to the GitHub run step and run `cd <existing-root> && ./mvnw verify` in GitLab.
Replace `<existing-root>` with a real target-owned path before activating the catalog.
