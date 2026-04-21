# Spring LDAP embedded testing and LDIF

Open this reference when embedded LDAP setup needs custom ports, LDIF handling, or schema-validation tuning.

## Embedded LDAP shape

```yaml
spring:
  ldap:
    embedded:
      base-dn: dc=test,dc=example,dc=com
      ldif: classpath:test-data.ldif
      port: 0
```

## Schema-validation rule

Disable embedded schema validation only when the embedded server does not publish a schema compatible with the test data.

## Embedded server compatibility note

Use the embedded LDAP server implementation that is already proven in the build. Prefer UnboundID when the project does not already have a known-good ApacheDS testing path, because the common Spring LDAP test examples in this repository assume UnboundID-backed embedded tests.

## LDIF loading choices

- Use simple `spring.ldap.embedded.ldif` properties when one fixture file is enough for the test.
- Use a dedicated LDIF populator or test context factory setup when the test needs more control over when data is loaded or reset.

## LDIF verification shape

```java
@Test
void verifyEmbeddedDataLoads() {
    List<Person> people = repository.findBySurname("Doe");
    assertAll(
        () -> assertEquals(1, people.size()),
        () -> assertEquals("Doe", people.get(0).getSurname())
    );
}
```

## Decision points

| Situation | Use |
| --- | --- |
| Test can use a random port | `port: 0` |
| Test harness needs a fixed port | explicit embedded LDAP port |
| Embedded server schema conflicts with test data | validation tuning |

## Validation rule

Verify the LDIF fixture actually loads into the embedded server before running repository assertions, and prefer `port: 0` unless the test harness truly requires a fixed port.
