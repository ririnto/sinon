# Testing introspection, revocation, and consent

Open this reference when the minimal validation in [SKILL.md](../SKILL.md) is not enough and the blocker is introspection, revocation, or consent verification.

```java
@Test
void introspectionReturnsActiveForIssuedToken() {
    ResponseEntity<Map> response = restTemplate.postForEntity("http://127.0.0.1:8080/oauth2/introspect", introspectionRequest("issued-token"), Map.class);
    Map body = Objects.requireNonNull(response.getBody());
    assertAll(
        () -> assertEquals(HttpStatus.OK, response.getStatusCode()),
        () -> assertEquals(Boolean.TRUE, body.get("active"))
    );
}

@Test
void revocationAcceptsKnownToken() {
    ResponseEntity<Void> response = restTemplate.postForEntity("http://127.0.0.1:8080/oauth2/revoke", revocationRequest("issued-token"), Void.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
}
```

- Verify introspection only for the enabled endpoint and caller contract.
- Verify revocation behavior against the same token lifecycle policy used in production.
- Verify remembered consent and requested scopes against the registered-client settings.
