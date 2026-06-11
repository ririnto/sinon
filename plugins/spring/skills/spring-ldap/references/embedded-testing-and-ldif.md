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

## Embedded LDAP SSL (LDAPS)

Spring Boot 4.1.0 adds SSL support for the embedded UnboundID server. The server starts an LDAPS listener instead of the plain LDAP listener when an SSL bundle is configured.

Properties:

```yaml
spring:
  ssl:
    bundle:
      jks:
        embedded-ldap:
          keystore:
            location: classpath:test-ldap.keystore
            password: changeit
          protocol: TLSv1.2
  ldap:
    embedded:
      base-dn: dc=test,dc=example,dc=com
      ssl:
        bundle: embedded-ldap
```

The `spring.ldap.embedded.ssl.bundle` property references an SSL bundle defined under `spring.ssl.bundle`. The `spring.ldap.embedded.ssl.enabled` property is optional; SSL is enabled automatically when `ssl.bundle` is set. Set `ssl.enabled: false` to disable SSL even when a bundle name is configured.

SSL bundle format choices:

- `spring.ssl.bundle.jks.<name>` for Java KeyStore (JKS or PKCS12) bundles.
- `spring.ssl.bundle.pem.<name>` for PEM-encoded certificate and key bundles.

The embedded server uses the SSL bundle's `SSLContext` to create both the `SSLServerSocketFactory` (server side) and the `SSLSocketFactory` (client side for test connections). Use `spring.ssl.bundle.jks.<name>.protocol` to control the TLS protocol version; `TLSv1.2` is a safe default.

Test configuration with `application-test.yml`:

```yaml
spring:
  ssl:
    bundle:
      pem:
        embedded-ldap:
          keystore:
            certificate: classpath:test-server.crt
            private-key: classpath:test-server.key
  ldap:
    embedded:
      base-dn: dc=test,dc=example,dc=com
      ssl:
        bundle: embedded-ldap
```

## Decision points

| Situation | Use |
| --- | --- |
| Test can use a random port | `port: 0` |
| Test harness needs a fixed port | explicit embedded LDAP port |
| Embedded server schema conflicts with test data | validation tuning |
| Test must verify LDAPS behavior | `ssl.bundle` referencing an `spring.ssl.bundle` entry |

## Validation rule

Verify the LDIF fixture actually loads into the embedded server before running repository assertions, and prefer `port: 0` unless the test harness truly requires a fixed port.
