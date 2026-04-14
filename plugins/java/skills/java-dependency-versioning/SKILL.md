---
name: java-dependency-versioning
description: >-
  This skill should be used when the user asks to "find the latest Maven dependency version", "look up a Java artifact coordinate", "check Maven Central for the current release", "install a Java testing library", or needs guidance on version-neutral dependency lookup for Java projects.
---

# Java Dependency Versioning

## Overview

Use this skill to look up Java dependency coordinates and current releases without hardcoding stale version numbers into durable guidance. The common case is confirming `groupId`, `artifactId`, and the current version from Maven Central, then emitting the smallest Maven or Gradle snippet that fits the project. Treat the version as live execution-time data, not something to memorize into the skill itself.

## Use This Skill When

- You need to identify a Maven coordinate for a Java library.
- You need to verify the current release of a Java artifact from Maven Central.
- You need a copyable Maven or Gradle dependency snippet after live verification.
- Do not use this skill when the main problem is repository-specific dependency governance such as mirrors, internal catalogs, or organization pinning policy.

## Common-Case Workflow

1. Identify the target library by `groupId` and `artifactId` before discussing versions.
2. Query Maven Central with a concrete request that can be re-run and checked with `curl`.
3. Treat `response.docs[0].latestVersion` as a candidate value, then confirm the coordinate and artifact type before writing a dependency snippet.
4. Emit the smallest install shape for Maven or Gradle, then layer repository policy on top only if needed.

## First Runnable Commands or Code Shape

Start with one concrete Maven Central check:

```bash
curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22org.awaitility%22+AND+a:%22awaitility%22&rows=1&wt=json"
```

*Applies when:* you need to verify the current version of a known artifact without trusting memory.

## Ready-to-Adapt Templates

Coordinate verification request:

```bash
curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22<groupId>%22+AND+a:%22<artifactId>%22&rows=1&wt=json"
```

*Applies when:* you know the candidate coordinate and need the current version.

Maven dependency snippet:

```xml
<dependency>
  <groupId>org.awaitility</groupId>
  <artifactId>awaitility</artifactId>
  <version>${verifiedVersion}</version>
  <scope>test</scope>
</dependency>
```

*Applies when:* the build tool is Maven.

Gradle dependency snippet:

```groovy
testImplementation "org.awaitility:awaitility:${verifiedVersion}"
```

*Applies when:* the build tool is Gradle.

Response verification checklist (use when the coordinate is plausible but still needs confirmation):

```text
# 1. response.numFound != 0
# 2. response.docs[0].g == expected groupId
# 3. response.docs[0].a == expected artifactId
# 4. response.docs[0].latestVersion treated as candidate, not final answer
# 5. result shape still matches the intended library, plugin, or BOM kind
```

## Validate the Result

Validate the common case with these checks:

- the coordinate is precise and unambiguous
- the version comes from the live Maven Central response rather than memory
- the emitted snippet matches the active build tool
- the reusable guidance remains version-neutral even though the live answer uses a concrete current version

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| Maven Central request shape, placeholder meaning, or response fields | `./references/maven-central.md` |

## Invariants

- MUST identify `groupId` and `artifactId` before recommending a release.
- MUST verify the current release from a concrete Maven Central request path at execution time when version specificity matters.
- MUST treat `latestVersion` as a candidate that still needs coordinate and artifact-type validation.
- MUST keep durable skill content version-neutral.
- SHOULD distinguish library dependency versions from plugin or tool versions.
- SHOULD prefer the smallest build-tool snippet that communicates the install shape clearly.
- MUST separate artifact lookup guidance from repository-specific pinning policy.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| guessing the version from memory | the answer goes stale immediately | verify with a concrete Maven Central request |
| searching by artifact name only | results can be ambiguous or wrong | confirm both `groupId` and `artifactId` |
| trusting `latestVersion` as if it always means most recently released | metadata can sort unexpectedly or point at the wrong artifact shape | validate the coordinate, artifact kind, and whether a safer `core=gav` query is needed |
| hardcoding the resolved version into reusable documentation | the skill becomes outdated | keep the skill version-neutral and use the live version only in the answer snippet |
| mixing library coordinates with plugin/tool versions | the install guidance becomes misleading | state clearly what kind of artifact is being verified |

## Scope Boundaries

- Activate this skill for:
  - Maven Central artifact lookup
  - current-release verification
  - version-neutral dependency installation guidance
- Do not use this skill as the primary source for:
  - JUnit structure or test-first workflow
  - public API or type-modeling decisions
  - performance or concurrency tradeoffs
  - standard JDK packaging and module workflows
