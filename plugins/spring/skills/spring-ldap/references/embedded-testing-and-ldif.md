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

When using ApacheDS for embedded tests, keep the server version aligned with the Spring LDAP testing support expected by the current project baseline. Use UnboundID when the ApacheDS compatibility path is not already proven in the build.

## LDIF loading choices

- Use simple `spring.ldap.embedded.ldif` properties when one fixture file is enough for the test.
- Use a dedicated LDIF populator or test context factory setup when the test needs more control over when data is loaded or reset.

## LDIF verification shape

```java
@Test
void verifyEmbeddedDataLoads() {
    List<Person> people = repository.findBySurname("Doe");
    assertEquals(1, people.size());
}
```

## Decision points

| Situation | Use |
| --- | --- |
| Test can use a random port | `port: 0` |
| Test harness needs a fixed port | explicit embedded LDAP port |
| Embedded server schema conflicts with test data | validation tuning |
