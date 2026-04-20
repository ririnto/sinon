---
name: java-dependency-versioning
description: >-
  Look up Java dependency coordinates and current releases from Maven Central,
  verify artifact versions without hardcoding stale numbers,
  and emit the smallest correct Maven or Gradle install snippet.
  Use when the user asks to find the latest Maven dependency version,
  look up a Java artifact coordinate, check Maven Central for the current release,
  or needs guidance on version-neutral dependency lookup for Java projects.
---

# Java Dependency Versioning

Look up Java dependency coordinates and current releases without hardcoding stale version numbers into durable guidance. The common case is confirming `groupId`, `artifactId`, and the current version from Maven Central, then emitting the smallest Maven or Gradle snippet that fits the project.

## Operating rules

- MUST identify `groupId` and `artifactId` before recommending a release.
- MUST verify the current release from a concrete Maven Central request path at execution time when version specificity matters.
- MUST treat `latestVersion` as a candidate that still needs coordinate and artifact-type validation.
- MUST keep durable skill content version-neutral.
- SHOULD distinguish library dependency versions from plugin or tool versions.
- SHOULD prefer the smallest build-tool snippet that communicates the install shape clearly.
- MUST separate artifact lookup guidance from repository-specific pinning policy.

## Procedure

1. Identify the target library by `groupId` and `artifactId` before discussing versions.
2. Query Maven Central with a concrete request that can be re-run and checked with `curl`.
3. Treat `response.docs[0].latestVersion` as a candidate value, then confirm the coordinate and artifact type before writing a dependency snippet.
4. Distinguish whether the artifact is a library, plugin, BOM, or platform dependency before suggesting syntax.
5. Emit the smallest install shape for Maven or Gradle, then layer repository policy on top only if needed.

## First runnable commands

Start with one concrete Maven Central check:

```bash
curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22org.awaitility%22+AND+a:%22awaitility%22&rows=1&wt=json"
```

Use when: you need to verify the current version of a known artifact without trusting memory.

## Ready-to-adapt templates

### Coordinate verification request

```bash
curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22<groupId>%22+AND+a:%22<artifactId>%22&rows=1&wt=json"
```

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

```text
# 1. response.numFound != 0
# 2. response.docs[0].g == expected groupId
# 3. response.docs[0].a == expected artifactId
# 4. response.docs[0].latestVersion treated as candidate, not final answer
# 5. result shape still matches the intended library, plugin, or BOM kind
```

### Safer query when `latestVersion` looks suspicious

```bash
curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22<groupId>%22+AND+a:%22<artifactId>%22&core=gav&rows=20&wt=json&sort=v%20desc"
```

## Edge cases

- If the main problem is repository-specific dependency governance such as mirrors, internal catalogs, or organization pinning policy, state that repository-specific policy is outside this skill's scope.
- If searching by artifact name only, warn that results can be ambiguous and confirm both `groupId` and `artifactId`.
- If `latestVersion` sorts unexpectedly (e.g., alpha after release), switch to the safer `core=gav` pattern with explicit sort.
- If the artifact is a plugin rather than a library, state the distinction clearly in the emitted snippet.
- If the question is about JUnit structure or test-first workflow, that is outside this skill's scope.
- If the question is about public API or type-modeling decisions, that is outside this skill's scope.
- If the question is about performance or concurrency tradeoffs, that is outside this skill's scope.

## Output contract

Return:

1. The verified `groupId:artifactId:version` coordinate.
2. The copyable Maven or Gradle snippet matching the active build tool.
3. The raw `curl` command used for verification so the result is reproducible.
4. Explicit note if the artifact kind (library, plugin, BOM) affects the install shape.

## Support-file pointers

| If the blocker is... | Open... |
| --- | --- |
| Maven Central request shape variants, response field details, paginated search, BOM/plugin lookup patterns, or installation notes by artifact kind | [`maven-central.md`](./references/maven-central.md) |

## Gotchas

- Do not guess the version from memory.
- Do not search by artifact name only.
- Do not trust `latestVersion` as if it always means most recently released.
- Do not hardcode the resolved version into reusable documentation.
- Do not mix library coordinates with plugin/tool versions without stating the distinction.
