---
name: "spring-ldap"
description: "Use this skill when building LDAP directory reads and writes in Spring with LdapTemplate, ODM entry mapping, LDAP repository queries, authentication checks, and embedded LDAP tests."
metadata:
  title: "Spring LDAP"
  official_project_url: "https://spring.io/projects/spring-ldap"
  version: "4.0.3"
---

Use this skill when building LDAP directory reads and writes in Spring with LdapTemplate, ODM entry mapping, LDAP repository queries, authentication checks, and embedded LDAP tests.

## Boundaries

Use `spring-ldap` for LDAP operations, ODM mapping, LDAP repository support, and embedded LDAP testing.

- Use `spring-security` for authentication, authorization, and filter-chain design around LDAP authentication providers. Keep this skill focused on directory operations and data mapping.
- Keep repository-backed LDAP query design in this skill. Use `spring-security` only when the real task is authentication or authorization policy rather than directory access itself.

## Common path

The ordinary Spring LDAP job is:

1. Add the smallest dependency set that covers the LDAP operation type.
2. Configure the LDAP context source with server URL, base DN, and credentials.
3. Use `LdapTemplate` for direct reads and writes or `LdapRepository` for repository-backed queries.
4. Map directory entries with ODM annotations or `DirContextAdapter`.
5. Add an embedded LDAP test that proves queries return the expected entries.

## Mapping decisions

| Situation | Use |
| --- | --- |
| Direct search or update logic | `LdapTemplate` |
| Repository-style query methods fit the schema | `LdapRepository` |
| Entry maps cleanly to a Java class | ODM annotations |
| Mapping is partial or highly dynamic | `DirContextAdapter` |

Prefer one mapping style per aggregate unless the schema forces a mixed approach.

## Dependency baseline

Use the Spring Boot starter for ordinary LDAP work.

### Runtime baseline

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-ldap</artifactId>
    </dependency>
</dependencies>
```

### Test-only additions

Add the Spring LDAP test module when tests need LDAP-specific assertions or helpers. If the test path uses `spring.ldap.embedded.*`, also add an embedded LDAP server implementation for tests.

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

## First safe configuration

### Embedded LDAP test configuration

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

Disable validation only when the embedded server does not publish its schema to the JNDI provider.

### Production context source properties

```yaml
spring:
  ldap:
    urls: ldap://ldap.example.com:389
    base: dc=example,dc=com
    username: cn=admin,dc=example,dc=com
    password: ${LDAP_ADMIN_PASSWORD}
```

Always externalize credentials. Use environment variables or secrets management, never hard-code passwords.

## Coding procedure

1. Identify whether the task needs `LdapTemplate` for direct operations or `LdapRepository` for query-method derivation.
2. Configure the `ContextSource` with URL, base DN, user DN, and password before writing any query or mapping code.
3. Use ODM annotations (`@Entry`, `@DnAttribute`, `@Attribute`) when the directory schema maps cleanly to Java types. Use `DirContextAdapter` when mapping is dynamic or partial.
4. Build filters with `LdapQueryBuilder` or `query().where(...)` for readable filter chains. Treat raw string filters as a last resort and keep Spring LDAP builders or helpers in front of manual escaping.
5. For authentication checks, search by the directory attribute your deployment uses for login such as `uid` or `cn`, and keep the attribute name explicit in code and tests.
6. Add an embedded LDAP test that loads a small LDIF file and verifies the query returns the expected entries.

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
        return ldap.search(
            query().where("objectclass").is("person").and("sn").is(lastName),
            (AttributesMapper<Person>) attrs -> {
                Person p = new Person();
                p.setCn((String) attrs.get("cn").get());
                p.setSn((String) attrs.get("sn").get());
                p.setMail((String) attrs.get("mail").get());
                return p;
            }
        );
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

Use `DirContextAdapter` when the task needs direct write operations such as attribute updates and the schema does not justify a full ODM aggregate workflow.

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

Use `@DnAttribute` for attributes that appear in the entry's relative distinguished name. Use `@Attribute` for other directory attributes.

### LDAP repository query method

```java
interface PersonRepository extends LdapRepository<Person> {
    List<Person> findBySurname(String surname);
    List<Person> findByCommonNameContaining(String nameFragment);
}
```

Spring Data derives the filter from the method name. `findBySurname` generates `(sn=<value>)`. `findByCommonNameContaining` generates `(cn=*<value>*)`.

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
            ldap.authenticate(
                query().where("uid").is(uid).and("objectclass").is("person"),
                password
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

Use this pattern when you need a boolean result rather than an authenticated context. Replace `uid` with the actual login attribute used by the target directory schema.

### Repository test shape with embedded LDAP data

```java
@SpringBootTest
class PersonRepositoryTest {
    @Autowired
    PersonRepository repository;

    @Test
    void findBySurnameReturnsExpectedPerson() {
        List<Person> results = repository.findBySurname("Doe");
        assertEquals(1, results.size());
        assertEquals("jane.doe@example.com", results.get(0).getEmail());
    }
}
```

Pair this test with embedded LDAP properties that point to a small LDIF fixture under `src/test/resources/`.

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

```java
// Simple equality
query().where("objectclass").is("person")

// Compound filter
query().where("objectclass").is("person").and("sn").is("Doe")

// Substring match
query().where("cn").like("*Doe*")

// Presence check
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

The `id` field holds the full DN. `@DnAttribute(index = N)` extracts the N-th component from the DN path.

### LDAP properties for external server

```yaml
spring:
  ldap:
    urls: ldaps://ldap.example.com:636
    base: dc=example,dc=com
    username: cn=reader,dc=example,dc=com
    password: ${LDAP_PASSWORD}
```

Use `ldap://` for plain LDAP ports such as 389 and `ldaps://` for LDAPS ports such as 636. Keep the scheme and port consistent.

## Testing checklist

- Verify queries return the expected entries after loading a known LDIF data set.
- Verify ODM mapping extracts DN components and directory attributes correctly.
- Verify `LdapTemplate.authenticate()` returns true for valid credentials and throws for invalid ones.
- Verify substring and presence filters match the expected subset of entries.
- For LDAP repositories, verify derived query method names produce the correct filter strings.
- For embedded LDAP tests, verify the test server starts and loads test data before assertions run.

## Production checklist

- Externalize directory credentials and never log them.
- Set timeouts on the LDAP context or JNDI environment to avoid thread exhaustion on unresponsive servers.
- Validate LDIF imports against the target directory schema before deployment.
- Monitor directory operation latency and connection pool utilization through application metrics.
- Record the Spring LDAP version in build files or a platform BOM to avoid unexpected compatibility issues.

## References

- Open [references/advanced-odm-and-repositories.md](references/advanced-odm-and-repositories.md) when the schema needs multi-valued ODM fields, raw `@Query`, or deeper repository derivation rules.
- Open [references/filters-and-dn-handling.md](references/filters-and-dn-handling.md) when the task needs complex LDAP filters, escaping, or DN parsing beyond the common path.
- Open [references/transactions-and-context-source.md](references/transactions-and-context-source.md) when the application needs transaction-aware context sources or connection-pool tuning.
- Open [references/embedded-testing-and-ldif.md](references/embedded-testing-and-ldif.md) when embedded LDAP setup needs custom ports, LDIF handling, or schema-validation tuning.
