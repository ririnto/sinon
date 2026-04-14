---
title: Maven Central Dependency Lookup
description: >-
  Reference for finding Java dependency coordinates and current releases in Maven Central.
---

Official entry points:

- Maven Central search: <https://search.maven.org/>
- Maven Central search API: <https://search.maven.org/solrsearch/select>
- Concrete verification path template:
  `https://search.maven.org/solrsearch/select?q=g:%22<groupId>%22+AND+a:%22<artifactId>%22&rows=1&wt=json`
- Example `curl` shape:
  `curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22org.awaitility%22+AND+a:%22awaitility%22&rows=1&wt=json"`

## Metadata Caveat

- `latestVersion` can reflect the highest version string rather than the most recently released artifact.
- When the result looks suspicious, switch to a `core=gav` query and sort explicitly instead of trusting one summary document.

Safer pattern when the default summary looks wrong:

```bash
curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22<groupId>%22+AND+a:%22<artifactId>%22&core=gav&rows=20&wt=json&sort=v%20desc"
```

## Concrete Lookup Examples

Single-artifact lookup for `org.awaitility:awaitility`:

```bash
curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22org.awaitility%22+AND+a:%22awaitility%22&rows=1&wt=json"
```

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
# Switch to core=gav to get per-GAV-row data:
curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22org.springframework%22+AND+a:%22spring-core%22&core=gav&rows=20&wt=json&sort=v%20desc"
```

Lookup for a BOM (Bill of Materials) artifact:

```bash
curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22io.quarkus%22+AND+a:%22quarkus-bom%22&rows=1&wt=json"
```

Checking a plugin artifact:

```bash
curl -fsSL "https://search.maven.org/solrsearch/select?q=g:%22org.apache.maven.plugins%22+AND+a:%22maven-compiler-plugin%22&rows=1&wt=json"
```
## Installation Notes

- For Maven, provide the dependency block only after verifying the current release.
- For Gradle, provide the dependency line only after verifying the current release.
- Distinguish ordinary libraries from plugins, BOMs, or platform dependencies before suggesting syntax.

## Review Questions
