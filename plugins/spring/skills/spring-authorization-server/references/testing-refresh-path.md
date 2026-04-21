# Testing refresh-token paths

Open this reference when the minimal validation in [SKILL.md](../SKILL.md) is not enough and the blocker is refresh-token path verification.

```java
@Test
void refreshTokenGrantSucceedsForRegisteredClient() {
    ResponseEntity<Map> response = restTemplate.postForEntity("http://127.0.0.1:8080/oauth2/token", refreshTokenRequest("refresh-client", "valid-refresh-token"), Map.class);
    Map body = Objects.requireNonNull(response.getBody());
    assertAll(
        () -> assertEquals(HttpStatus.OK, response.getStatusCode()),
        () -> assertTrue(body.containsKey("access_token"))
    );
}

@Test
void refreshTokenGrantRejectsUnknownRefreshToken() {
    ResponseEntity<Map> response = restTemplate.postForEntity("http://127.0.0.1:8080/oauth2/token", refreshTokenRequest("refresh-client", "unknown-refresh-token"), Map.class);
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
}
```

Verify one representative refresh success path and one representative refresh rejection path for the registered client that actually enables the refresh-token grant.
