# Directory operation examples

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
`LdapClient` does not support ODM.
Use `LdapTemplate` when ODM entry mapping is required.

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
For embedded LDAPS testing, use `spring.ldap.embedded.ssl.bundle` instead of configuring `spring.ldap.urls` manually - see [references/embedded-testing-and-ldif.md](embedded-testing-and-ldif.md).
