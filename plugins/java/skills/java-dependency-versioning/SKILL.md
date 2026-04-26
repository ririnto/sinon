---
name: java-dependency-versioning
description: >-
  Resolve Java dependency coordinates, keep reusable install guidance version-neutral,
  and verify current public releases only when online lookup is available.
  Use when the user asks to find a Java artifact coordinate,
  prepare a Maven or Gradle dependency snippet, check Maven Central for a current release,
  or needs offline-safe dependency versioning guidance for Java projects.
---

# Java Dependency Versioning

Resolve Java dependency coordinates without hardcoding stale version numbers into durable guidance. The common case is confirming `groupId` and `artifactId` from the user's project, then emitting the smallest Maven or Gradle snippet with a repository-managed version reference or placeholder. When the user explicitly needs the current public release and network access is available, verify it from Maven Central as a separate online branch.

## Operating rules

- MUST identify `groupId` and `artifactId` before recommending a release.
- MUST keep the ordinary path offline-safe and version-neutral.
- MUST verify the current release from a concrete Maven Central request path only when network access is available and version specificity matters.
- MUST treat `latestVersion` as a candidate that still needs coordinate and artifact-type validation.
- MUST keep durable skill content version-neutral.
- SHOULD distinguish library dependency versions from plugin or tool versions.
- SHOULD prefer an existing repository-managed version source such as a BOM, Gradle version catalog, or Maven property before introducing a literal version.
- SHOULD prefer the smallest build-tool snippet that communicates the install shape clearly.
- MUST separate artifact lookup guidance from repository-specific pinning policy.

## Procedure

1. Identify the target library by `groupId` and `artifactId` before discussing versions.
2. Check whether the repository already pins the version through a BOM, Gradle version catalog, Maven property, or existing dependency declaration.
3. If no verified local version source is available, emit a version-neutral snippet using a placeholder such as `${verifiedVersion}` or the repository's version-reference style.
4. If the user explicitly needs the current public release and network access is available, query Maven Central with a concrete request that can be re-run and checked with `curl`.
5. Treat `response.docs[0].latestVersion` as a candidate value, then confirm the coordinate and artifact type before writing a version-specific snippet.
6. Distinguish whether the artifact is a library, plugin, BOM, or platform dependency before suggesting syntax.
7. Emit the smallest install shape for Maven or Gradle, then layer repository policy on top only if needed.

## First runnable commands

Start with one local coordinate check:

```bash
grep -nE 'org\.awaitility|awaitility' pom.xml build.gradle build.gradle.kts gradle/libs.versions.toml 2>/dev/null
```

Use when: you need to confirm whether the repository already declares the dependency or version reference locally.

## Ready-to-adapt templates

### Maven library dependency

```xml
<dependency>
  <groupId>org.awaitility</groupId>
  <artifactId>awaitility</artifactId>
  <version>${verifiedVersion}</version>
  <scope>test</scope>
</dependency>
```

### Gradle Groovy DSL library dependency

```groovy
testImplementation "org.awaitility:awaitility:${verifiedVersion}"
```

### Gradle Kotlin DSL library dependency

```kotlin
testImplementation("org.awaitility:awaitility:${verifiedVersion}")
```

### Gradle version catalog (libs.versions.toml)

`gradle/libs.versions.toml`:

```toml
[versions]
awaitility = "${verifiedVersion}"

[libraries]
awaitility = { module = "org.awaitility:awaitility", version.ref = "awaitility" }
```

Gradle Groovy DSL usage:

```groovy
dependencies {
    testImplementation libs.awaitility
}
```

Gradle Kotlin DSL usage:

```kotlin
dependencies {
    testImplementation(libs.awaitility)
}
```

### Maven BOM import

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-bom</artifactId>
      <version>${verifiedVersion}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

### Gradle Kotlin DSL platform dependency

```kotlin
implementation(platform("io.quarkus:quarkus-bom:${verifiedVersion}"))
```

### Response verification checklist

1. `response.numFound != 0`
2. `response.docs[0].g == expected groupId`
3. `response.docs[0].a == expected artifactId`
4. `response.docs[0].latestVersion` is treated as a candidate, not the final answer.
5. The result shape still matches the intended library, plugin, or BOM kind.

## Online verification branch

Open this branch only when the user explicitly needs a current public release and network access is available. Keep the ordinary path version-neutral when those conditions are not met.

### Coordinate verification request

```bash
curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22<groupId>%22+AND+a:%22<artifactId>%22&rows=1&wt=json"
```

### Safer query when `latestVersion` looks suspicious

```bash
curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22<groupId>%22+AND+a:%22<artifactId>%22&core=gav&rows=20&wt=json&sort=v%20desc"
```

## Edge cases

- If the main problem is repository-specific dependency governance such as mirrors, internal catalogs, or organization pinning policy, state that repository-specific policy is outside this skill's scope.
- If searching by artifact name only, warn that results can be ambiguous and confirm both `groupId` and `artifactId`.
- If the environment is offline or network access is disallowed, stay with locally verified coordinates and a version placeholder instead of implying a live release check happened.
- If `latestVersion` sorts unexpectedly (e.g., alpha after release), switch to the safer `core=gav` pattern with explicit sort.
- If the artifact is a plugin rather than a library, state the distinction clearly in the emitted snippet.
- If the question is about JUnit structure or test-first workflow, that is outside this skill's scope.
- If the question is about public API or type-modeling decisions, that is outside this skill's scope.
- If the question is about performance or concurrency tradeoffs, that is outside this skill's scope.

## Output contract

Return:

1. The resolved `groupId:artifactId` coordinate and either the verified version or an explicit placeholder/version-reference note.
2. The copyable Maven or Gradle snippet matching the active build tool.
3. The raw `curl` command used for verification when an online check was performed.
4. Explicit note if the artifact kind (library, plugin, BOM) affects the install shape.

## Support-file pointers

| If the blocker is... | Open... |
| --- | --- |
| Online Maven Central verification, response field details, paginated search, BOM/plugin lookup patterns, or installation notes by artifact kind | [`maven-central.md`](./references/maven-central.md) |

## Gotchas

- Do not guess the version from memory.
- Do not search by artifact name only.
- Do not trust `latestVersion` as if it always means most recently released.
- Do not hardcode the resolved version into reusable documentation.
- Do not mix library coordinates with plugin/tool versions without stating the distinction.
