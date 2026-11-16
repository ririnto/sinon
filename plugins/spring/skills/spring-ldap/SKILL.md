---
metadata:
  reference:
    Spring LDAP:
      version: 4.1.1
      url:
        - https://docs.spring.io/spring-ldap/reference/
        - https://repo.maven.apache.org/maven2/org/springframework/ldap/spring-ldap-core/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/ldap/spring-ldap-core/4.1.1/spring-ldap-core-4.1.1.pom
name: spring-ldap
description: >-
  Use Spring LDAP for directory queries, DN handling, ODM, repositories, and embedded tests.
---

# Spring LDAP

The direct-dependency examples target Spring LDAP 4.1.x.
That 4.x line targets Spring Framework 7+, while Spring Boot 3.x still manages the parallel 3.x line.
Keep the direct 4.1.x path in this skill only when the project baseline already matches the 4.x generation.
If the job needs `LdapRepository`, use the matching Spring Data LDAP 4.1.x and Spring LDAP 4.1.x lines.

## Boundaries

Use `spring-ldap` for LDAP operations, ODM mapping, LDAP repository support, and embedded LDAP testing.

- Authentication, authorization, and filter-chain design around LDAP authentication providers are Spring Security concerns outside this directory-operations scope.
- Keep repository-backed LDAP query design in this skill.
  - Treat authentication or authorization policy as a separate application-security concern rather than directory access itself.

## Task scope

Preserve the directory schema, base DN, and access contract unless the task requires changes.
Choose the access and mapping surface for the directory operation rather than adding repository or ODM layers by default.
Use the sections and references for the affected query, mapping, connection, or test concern.

## Surface map

| Surface | Start here when | Open a reference when |
| --- | --- | --- |
| Ordinary LDAP reads and writes | one service uses `LdapTemplate` or `LdapRepository` | read [directory operation examples](references/directory-operation-examples.md) |
| Complex filters or DN parsing | filter escaping or DN extraction is needed | open [filters and dn handling](references/filters-and-dn-handling.md) |
| Context source tuning or compensating transactions | connection tuning or multi-write rollback semantics matter | open [transactions and context source](references/transactions-and-context-source.md) |
| Advanced ODM and repository mapping | multi-valued attributes or raw `@Query` are needed | open [advanced odm and repositories](references/advanced-odm-and-repositories.md) |
| Embedded LDAP details | LDIF loading, embedded LDAPS/SSL, or schema-validation tuning is needed | open [embedded testing and ldif](references/embedded-testing-and-ldif.md) |
| LDAP-backed authentication | the real task is authentication or authorization policy | outside this directory access and mapping scope |

## Mapping decisions

| Situation | Use |
| --- | --- |
| Direct search or update logic without ODM | `LdapClient` |
| Direct search or update logic with ODM | `LdapTemplate` |
| Repository-style query methods fit the schema | `LdapRepository` |
| Entry maps cleanly to a Java class | ODM annotations |
| Mapping is partial or highly dynamic | `DirContextAdapter` |

Prefer one mapping style per aggregate unless the schema forces a mixed approach.

## Dependencies and configuration

Use direct Spring LDAP artifacts on the ordinary 4.1.x path, including `spring-data-ldap` only for repository-backed queries.
For new dependencies, check Maven Central for stable versions compatible with the project's Boot and Framework lines; preserve existing pins unless authorized to change them.
Use the Boot starter only when the project's Boot line manages the intended LDAP generation.
For embedded LDAP properties, include a test server implementation and disable schema validation only if its schema is incompatible with test data.
Externalize credentials through environment variables or secrets management; never hard-code passwords.
For dependency declarations, direct or Boot configuration, or embedded test setup, read [dependencies and configuration](references/dependencies-and-configuration.md).

## Implementation guidance

- Configure the `ContextSource` with URL, base DN, user DN, and an externalized password.
- Use ODM annotations (`@Entry`, `@DnAttribute`, `@Attribute`) when the directory schema maps cleanly to Java types.
  Use `DirContextAdapter` when mapping is dynamic or partial.
- Build filters with `LdapQueryBuilder` or `query().where(...)`.
  Use raw strings only when necessary, and prefer Spring LDAP escaping helpers over manual escaping.
- For authentication checks, use the deployment's login attribute, such as `uid` or `cn`.
  Keep that attribute explicit in code and tests.

## Examples

For LDAP search, writes, ODM mappings, repository queries, authentication checks, filters, or embedded tests, read [directory operation examples](references/directory-operation-examples.md).

## Verification

Use existing tests for changed directory behavior and regression risks.
Extend embedded LDAP coverage when real filters, mappings, binds, or server configuration define the contract.
Select the checks that apply:

- Verify queries return the expected entries after loading a known LDIF data set.
- Verify ODM mapping extracts DN components and directory attributes correctly.
- Verify `LdapTemplate.authenticate()` returns true for valid credentials and throws for invalid ones.
- Verify substring and presence filters match the expected subset of entries.
- For LDAP repositories, verify derived query method names produce the correct filter strings.
- For embedded LDAP tests, verify the test server starts and loads test data before assertions run.

## Failure classification

- Treat invalid credentials and bind failures as authentication failures, not retryable infrastructure events.
- Treat malformed filters, schema mismatches, and DN mapping errors as configuration or contract failures.
- Treat timeouts, pool exhaustion, and unavailable servers as infrastructure failures that may justify retry or health-based fail-fast behavior.

## Output contract

Report the changed LDAP access contract, relevant configuration or mapping decisions, and verification results.
Name any unverified directory behavior or blocker without exposing credentials.
Follow the task's required response format.

## Production checklist

- Externalize directory credentials and never log them.
- Set timeouts on the LDAP context or JNDI environment to avoid thread exhaustion on unresponsive servers.
- Validate LDIF imports against the target directory schema before deployment.
- Monitor directory operation latency and connection pool utilization through application metrics.
- Record the Spring LDAP version in build files or a platform BOM to avoid unexpected compatibility issues.
