---
name: java-dependency-versioning
description: >-
  Resolve Java artifact coordinates, prepare Maven or Gradle dependency snippets, or verify current Maven Central releases.
---

# Java Dependency Versioning

Resolve Java dependency coordinates without hardcoding stale version numbers into durable guidance.
The common case is confirming `groupId` and `artifactId` from the user's project, then emitting the smallest Maven or Gradle snippet with a repository-managed version reference or placeholder.
Before recommending a new literal version, check Maven Central for the latest stable release compatible with the project's platform.

## Operating rules

- MUST identify `groupId` and `artifactId` before recommending a release.
- MUST keep the ordinary path offline-safe and version-neutral.
- MUST check the latest stable compatible release for the exact artifact on Maven Central when recommending a new literal version and network access is available.
- MUST treat `latestVersion` as a candidate that still needs coordinate and artifact-type validation.
- MUST keep durable skill content version-neutral.
- SHOULD distinguish library dependency versions from plugin or tool versions.
- MUST preserve an existing BOM, Gradle version catalog, Maven property, or pin unless the task authorizes changing it.
- SHOULD prefer the smallest build-tool snippet that communicates the install shape clearly.
- MUST separate artifact lookup guidance from repository-specific pinning policy.

## Task Context

Read the relevant dependency declaration and repository-managed version source.
Use `${verifiedVersion}` or the repository's version-reference style when no verified version is available.
Open [`maven-central.md`](./references/maven-central.md) for online lookup, response fields, pagination, or artifact-specific installation details.
Dependency lookup does not itself authorize adding or upgrading a dependency.

## First runnable commands

Start with one local coordinate check:

```sh
for file in pom.xml build.gradle build.gradle.kts gradle/libs.versions.toml; do
    if [ -f "${file}" ]; then grep -nE 'org\.awaitility|awaitility' "${file}"; fi
done
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

Open this branch when recommending a new literal version and network access is available.
Reject preview or milestone releases and verify compatibility with the project's framework and managed versions.
Keep the ordinary path version-neutral when those conditions are not met.

### Coordinate verification request

```sh
curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22<groupId>%22+AND+a:%22<artifactId>%22&rows=1&wt=json"
```

### Safer query when `latestVersion` looks suspicious

```sh
curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22<groupId>%22+AND+a:%22<artifactId>%22&core=gav&rows=20&wt=json&sort=v%20desc"
```

## Edge cases

- If the main problem is repository-specific dependency governance such as mirrors, internal catalogs, or organization pinning policy, state that repository-specific policy is outside this skill's scope.
- If searching by artifact name only, warn that results can be ambiguous and confirm both `groupId` and `artifactId`.
- If the environment is offline or network access is disallowed, stay with locally verified coordinates and a version placeholder instead of implying a live release check happened.
- If `latestVersion` sorts unexpectedly (e.g., alpha after release), switch to the safer `core=gav` pattern with explicit sort.
- If the artifact is a plugin rather than a library, state the distinction clearly in the emitted snippet.
- Questions about JUnit structure are outside this skill's scope.
  Use `java:java-test`.
- Questions about public API or type modeling are outside this skill's scope.
  Use `java:java-language-design`.
- Questions about performance and concurrency tradeoffs are outside this skill's scope.
  Use `java:java-performance-concurrency`.

## Result

Return the verified coordinate and the snippet for the active build tool.
Distinguish a locally managed version, placeholder, and live-verified release.
Include the request used for any online verification and any artifact-kind caveat.
