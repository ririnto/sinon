# Testing token, metadata, and JWK endpoints

Open this reference when the minimal validation in [SKILL.md](../SKILL.md) is not enough and the blocker is token, metadata, or JWK endpoint verification.

```java
@Test
void jwkSetContainsExpectedKeyType() {
    ResponseEntity<JWKSet> response = restTemplate.getForEntity("http://127.0.0.1:8080/oauth2/jwks", JWKSet.class);
    JWKSet jwkSet = Objects.requireNonNull(response.getBody());
    assertAll(
        () -> assertEquals(HttpStatus.OK, response.getStatusCode()),
        () -> assertEquals("RSA", jwkSet.getKeys().get(0).getKeyType().getValue())
    );
}
```

Verify one successful token path, one rejected token path, and one metadata or JWK exposure path for the same issuer setup.
