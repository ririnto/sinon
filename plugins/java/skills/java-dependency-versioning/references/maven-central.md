---
title: Maven Central Dependency Lookup
description: >-
  Reference for Maven Central search API shapes, response field semantics,
  paginated lookup patterns, BOM/plugin artifact handling,
  and installation guidance by artifact kind.
---

Open this reference when the coordinate is verified but you still need one of these deeper jobs:

- alternative query shapes when the default summary looks wrong
- paginated group-level search or version-descending sort
- BOM or plugin artifact lookup (different from library dependencies)
- parsing the JSON response with `jq` or shell tools
- distinguishing installation syntax by artifact kind

## Official entry points

- Maven Central search: <https://search.maven.org/>
- Maven Central search API: <https://search.maven.org/solrsearch/select>
- Concrete verification path template:
  `https://search.maven.org/solrsearch/select?q=g:%22<groupId>%22+AND+a:%22<artifactId>%22&rows=1&wt=json`

## Metadata caveat

- `latestVersion` can reflect the highest version string rather than the most recently released artifact.
- When the result looks suspicious, switch to a `core=gav` query and sort explicitly instead of trusting one summary document.

## Concrete lookup examples

Parsing the current version from the JSON response:

```bash
VERSION=$(curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22org.awaitility%22+AND+a:%22awaitility%22&rows=1&wt=json" | jq -r '.response.docs[0].latestVersion')
echo "Latest: $VERSION"
```

Paginated search for all artifacts matching a group:

```bash
curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22org.awaitility%22&rows=20&wt=json"
```

Sort by version descending to find the truly latest published release:

```bash
curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22org.awaitility%22+AND+a:%22awaitility%22&core=gav&rows=20&wt=json&sort=v%20desc"
```

When the default summary looks wrong (artifact is not a library, or `latestVersion` sorts unexpectedly):

```bash
curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22org.springframework%22+AND+a:%22spring-core%22&core=gav&rows=20&wt=json&sort=v%20desc"
```

This switches to `core=gav` to get per-GAV-row data instead of one summary document.

Lookup for a BOM (Bill of Materials) artifact:

```bash
curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22io.quarkus%22+AND+a:%22quarkus-bom%22&rows=1&wt=json"
```

Checking a plugin artifact:

```bash
curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22org.apache.maven.plugins%22+AND+a:%22maven-compiler-plugin%22&rows=1&wt=json"
```

## Installation notes by artifact kind

### Library dependency

Maven:

```xml
<dependency>
  <groupId>${groupId}</groupId>
  <artifactId>${artifactId}</artifactId>
  <version>${verifiedVersion}</version>
</dependency>
```

Gradle Groovy DSL:

```groovy
implementation "${groupId}:${artifactId}:${verifiedVersion}"
```

Gradle Kotlin DSL:

```kotlin
implementation("${groupId}:${artifactId}:${verifiedVersion}")
```

### BOM (Bill of Materials)

Maven:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>${groupId}</groupId>
      <artifactId>${artifactId}</artifactId>
      <version>${verifiedVersion}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Gradle Groovy DSL:

```groovy
implementation platform("${groupId}:${artifactId}:${verifiedVersion}")
```

Gradle Kotlin DSL:

```kotlin
implementation(platform("${groupId}:${artifactId}:${verifiedVersion}"))
```

### Plugin

Maven:

```xml
<plugin>
  <groupId>${groupId}</groupId>
  <artifactId>${artifactId}</artifactId>
  <version>${verifiedVersion}</version>
</plugin>
```

Gradle Groovy DSL:

```groovy
plugins {
    id "${artifactId}" version "${verifiedVersion}"
}
```

Gradle Kotlin DSL:

```kotlin
plugins {
    id("${artifactId}") version "${verifiedVersion}"
}
```

### Gradle version catalog

`gradle/libs.versions.toml`:

```toml
[versions]
mylib = "${verifiedVersion}"

[libraries]
mylib = { module = "${groupId}:${artifactId}", version.ref = "mylib" }
```

Gradle Groovy DSL usage:

```groovy
dependencies {
    implementation libs.mylib
}
```

Gradle Kotlin DSL usage:

```kotlin
dependencies {
    implementation(libs.mylib)
}
```

## Response field reference

| Field | Meaning |
| --- | --- |
| `response.numFound` | Total matching documents |
| `response.docs[0].g` | Group ID |
| `response.docs[0].a` | Artifact ID |
| `response.docs[0].latestVersion` | Highest version string (may not be most recent release) |
| `response.docs[0].id` | Combined `g:a` |
| `response.docs[0].p` | Packaging type (`jar`, `pom`, `war`, etc.) |
