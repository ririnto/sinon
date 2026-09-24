---
metadata:
  reference:
    Spring LDAP:
      version: 4.1.1
      url: https://docs.spring.io/spring-ldap/reference/
name: spring-ldap
description: >-
  Implement or troubleshoot Spring LDAP queries, DN handling, ODM mapping, repositories, authentication checks, and embedded LDAP tests.
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
| Ordinary LDAP reads and writes | one service uses `LdapTemplate` or `LdapRepository` | stay in `SKILL.md` |
| Complex filters or DN parsing | filter escaping or DN extraction is the blocker | open [references/filters-and-dn-handling.md](references/filters-and-dn-handling.md) |
| Context source tuning or compensating transactions | connection tuning or multi-write rollback semantics matter | open [references/transactions-and-context-source.md](references/transactions-and-context-source.md) |
| Advanced ODM and repository mapping | multi-valued attributes or raw `@Query` are the blocker | open [references/advanced-odm-and-repositories.md](references/advanced-odm-and-repositories.md) |
| Embedded LDAP details | LDIF loading, embedded LDAPS/SSL, or schema-validation tuning is the blocker | open [references/embedded-testing-and-ldif.md](references/embedded-testing-and-ldif.md) |
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

## Dependency baseline

Use direct Spring LDAP artifacts for the ordinary 4.1.x path.
The `4.1.1` versions below illustrate the documented direct-dependency pairing.
For new direct dependencies, check `org.springframework.ldap:spring-ldap-core` and `org.springframework.data:spring-data-ldap` on Maven Central for stable releases compatible with the project's Boot and Framework lines.
Keep existing BOM or version-catalog pins unless the task authorizes changing them.
Treat Spring Boot starter wiring as a parallel Boot-managed path only when the project is already on the matching Boot generation.

### Runtime baseline

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.ldap</groupId>
        <artifactId>spring-ldap-core</artifactId>
        <version>4.1.1</version>
    </dependency>
</dependencies>
```

### Repository-backed direct path

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.ldap</groupId>
        <artifactId>spring-ldap-core</artifactId>
        <version>4.1.1</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.data</groupId>
        <artifactId>spring-data-ldap</artifactId>
        <version>4.1.1</version>
    </dependency>
</dependencies>
```

Use this direct path when repository-backed queries are required outside Boot-managed dependency alignment.

### Boot-managed alternative

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-ldap</artifactId>
    </dependency>
</dependencies>
```

Use the Boot starter only when the project is already on a Boot line that manages the intended Spring LDAP generation.

### Test-only additions

Add the Spring LDAP test module when tests need LDAP-specific assertions or helpers.
If the test path uses `spring.ldap.embedded.*`, also add an embedded LDAP server implementation for tests.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.ldap</groupId>
        <artifactId>spring-ldap-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>com.unboundid</groupId>
        <artifactId>unboundid-ldapsdk</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Feature-to-artifact map

| Need | Artifact |
| --- | --- |
| Ordinary LDAP access with `LdapTemplate` or `LdapClient` | `spring-ldap-core` |
| Repository-backed LDAP queries with `LdapRepository` | `spring-data-ldap` plus `spring-ldap-core` |
| Boot-managed LDAP integration | `spring-boot-starter-data-ldap` |
| LDAP-specific test helpers | `spring-ldap-test` |
| Embedded test server implementation | `unboundid-ldapsdk` or another proven embedded LDAP server |

## First safe configuration

### First safe commands

```sh
./mvnw test -Dtest=PersonRepositoryTest
```

```sh
./gradlew test --tests PersonRepositoryTest
```

### Embedded LDAP test configuration

Boot-managed property shape:

```yaml
spring:
  ldap:
    embedded:
      base-dn: dc=example,dc=com
      validation:
        enabled: false
  data:
    ldap:
      repositories:
        enabled: true
```

Disable validation only when the embedded server does not publish a schema compatible with the test data.

### Production context source properties

Boot-managed property shape:

```yaml
spring:
  ldap:
    urls: ldap://ldap.example.com:389
    base: dc=example,dc=com
    username: cn=admin,dc=example,dc=com
    password: ${LDAP_ADMIN_PASSWORD}
```

Always externalize credentials.
Use environment variables or secrets management, never hard-code passwords.

### Direct Spring LDAP context source shape

```java
@Configuration
class LdapConfiguration {
    @Bean
    BaseLdapPathContextSource contextSource() {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl("ldap://ldap.example.com:389");
        contextSource.setBase("dc=example,dc=com");
        contextSource.setUserDn("cn=admin,dc=example,dc=com");
        contextSource.setPassword(password);
        contextSource.afterPropertiesSet();
        return contextSource;
    }

    @Bean
    LdapTemplate ldapTemplate(BaseLdapPathContextSource contextSource) {
        return new LdapTemplate(contextSource);
    }

    @Bean
    LdapClient ldapClient(BaseLdapPathContextSource contextSource) {
        return LdapClient.create(contextSource);
    }
}
```

Use this direct bean path as the ordinary 4.1.x baseline when the project is not relying on Boot-managed LDAP configuration.

### Direct repository configuration shape

```java
@Configuration
@EnableLdapRepositories(basePackages = "com.example.ldap")
class RepositoryConfiguration {
    @Bean
    BaseLdapPathContextSource contextSource() {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl("ldap://ldap.example.com:389");
        contextSource.setBase("dc=example,dc=com");
        contextSource.setUserDn("cn=admin,dc=example,dc=com");
        contextSource.setPassword(password);
        contextSource.afterPropertiesSet();
        return contextSource;
    }
}
```

Use this configuration only when the direct non-Boot path needs `LdapRepository`.
If the task only needs `LdapTemplate`, keep the simpler direct bean path above.

## Implementation guidance

- Configure the `ContextSource` with URL, base DN, user DN, and an externalized password.
- Use ODM annotations (`@Entry`, `@DnAttribute`, `@Attribute`) when the directory schema maps cleanly to Java types.
  Use `DirContextAdapter` when mapping is dynamic or partial.
- Build filters with `LdapQueryBuilder` or `query().where(...)`.
  Use raw strings only when necessary, and prefer Spring LDAP escaping helpers over manual escaping.
- For authentication checks, use the deployment's login attribute, such as `uid` or `cn`.
  Keep that attribute explicit in code and tests.

## Implementation examples

### LdapTemplate search with `AttributesMapper`

```java
@Service
class PersonDirectory {
    private final LdapTemplate ldap;

    PersonDirectory(LdapTemplate ldap) {
        this.ldap = ldap;
    }

    List<Person> findByLastName(String lastName) {
        return ldap.search(query().where("objectclass").is("person").and("sn").is(lastName), (AttributesMapper<Person>) attrs -> {
                Person p = new Person();
                p.setCn((String) attrs.get("cn").get());
                p.setSn((String) attrs.get("sn").get());
                p.setMail((String) attrs.get("mail").get());
                return p;
            });
    }
}
```

### LdapTemplate write shape with `DirContextAdapter`

```java
@Service
class PersonWriter {
    private final LdapTemplate ldap;

    PersonWriter(LdapTemplate ldap) {
        this.ldap = ldap;
    }

    void updateMail(Name dn, String mail) {
        DirContextOperations context = ldap.lookupContext(dn);
        context.setAttributeValue("mail", mail);
        ldap.modifyAttributes(context);
    }
}
```

Use `DirContextAdapter` when the task needs direct write operations such as attribute updates and the schema is too small for a full ODM aggregate workflow.

### LdapClient search (Spring LDAP 4.1.x)

```java
@Service
class PersonDirectory {
    private final LdapClient ldap;

    PersonDirectory(LdapClient ldap) {
        this.ldap = ldap;
    }

    List<Person> findByLastName(String lastName) {
        return ldap.search()
            .where("objectclass").is("person")
            .and("sn").is(lastName)
            .list((AttributesMapper<Person>) attrs -> {
                Person p = new Person();
                p.setCn((String) attrs.get("cn").get());
                p.setSn((String) attrs.get("sn").get());
                p.setMail((String) attrs.get("mail").get());
                return p;
            });
    }
}
```

`LdapClient` provides `list()`, `stream()`, `single()`, `optional()`, and `map()` terminal operations on search queries.
Use `LdapClient` when ODM is not needed.
`LdapClient` does not support ODM; use `LdapTemplate` when ODM entry mapping is required.

### ODM entity with DN-relative attributes

```java
@Entry(objectClasses = {"person"})
class Person {
    @Id
    private Name id;

    @DnAttribute("cn")
    private String commonName;

    @Attribute("sn")
    private String surname;

    @Attribute("mail")
    private String email;

    @Attribute("telephoneNumber")
    private String phone;
}
```

Use `@DnAttribute` for attributes that appear in the entry's relative distinguished name.
Use `@Attribute` for other directory attributes.

### LDAP repository query method

```java
interface PersonRepository extends LdapRepository<Person> {
    List<Person> findBySurname(String surname);
    List<Person> findByCommonNameContaining(String nameFragment);
}
```

Spring Data derives the filter from the method name.
`findBySurname` generates `(sn=<value>)`.
`findByCommonNameContaining` generates `(cn=*<value>*)`.

### Authentication check with explicit login attribute

```java
@Service
class LdapAuthService {
    private final LdapTemplate ldap;

    LdapAuthService(LdapTemplate ldap) {
        this.ldap = ldap;
    }

    boolean checkCredentials(String uid, String password) {
        try {
            ldap.authenticate(query().where("uid").is(uid).and("objectclass").is("person"), password);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

Use this pattern when you need a boolean result rather than an authenticated context.
Replace `uid` with the actual login attribute used by the target directory schema.

### Boot-managed repository test shape with embedded LDAP data

```java
@SpringBootTest
class PersonRepositoryTest {
    @Autowired
    PersonRepository repository;

    @Test
    void findBySurnameReturnsExpectedPerson() {
        List<Person> results = repository.findBySurname("Doe");
        assertAll(() -> assertEquals(1, results.size()), () -> assertEquals("jane.doe@example.com", results.get(0).getEmail()));
    }
}
```

Pair this test with embedded LDAP properties that point to a small LDIF fixture under `src/test/resources/`.

When the direct non-Boot path is the actual target, treat this example as a Boot-managed test slice and add an explicit repository configuration like the one above instead of assuming Boot auto-configuration.

### Embedded LDAP test properties shape

```yaml
spring:
  ldap:
    embedded:
      base-dn: dc=example,dc=com
      ldif: classpath:test-data.ldif
      port: 0
```

Use a tiny LDIF fixture with only the entries needed by the test so query expectations stay obvious.

## Output and configuration shapes

### LdapQuery filter shapes

Simple equality:

```java
query().where("objectclass").is("person")
```

Compound filter:

```java
query().where("objectclass").is("person").and("sn").is("Doe")
```

Substring match:

```java
query().where("cn").like("*Doe*")
```

Presence check:

```java
query().where("telephoneNumber").isPresent()
```

### ODM DN-relative mapping shape

```java
@Entry(objectClasses = {"inetOrgPerson"})
class User {
    @Id
    private Name id;

    @DnAttribute(value = "ou", index = 0)
    private String organizationalUnit;

    @DnAttribute(value = "cn", index = 1)
    private String commonName;
}
```

The `id` field holds the full DN.
`@DnAttribute(index = N)` extracts the N-th component from the DN path.

### LDAP properties for external server

```yaml
spring:
  ldap:
    urls: ldaps://ldap.example.com:636
    base: dc=example,dc=com
    username: cn=reader,dc=example,dc=com
    password: ${LDAP_PASSWORD}
```

Use `ldap://` for plain LDAP ports such as 389 and `ldaps://` for LDAPS ports such as 636.
Keep the scheme and port consistent.
For embedded LDAPS testing, use `spring.ldap.embedded.ssl.bundle` instead of configuring `spring.ldap.urls` manually - see [references/embedded-testing-and-ldif.md](references/embedded-testing-and-ldif.md).

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
