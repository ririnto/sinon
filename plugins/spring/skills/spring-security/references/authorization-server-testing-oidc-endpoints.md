# Testing OIDC endpoints

Open this reference when the minimal validation in [SKILL.md](../SKILL.md) is not enough and the blocker is OIDC endpoint verification.

```java
@Test
void userInfoReturnsClaimsForValidAccessToken() {
    String accessToken = obtainAccessToken("test-client", "http://127.0.0.1:8080/callback");
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    ResponseEntity<Map> response = restTemplate.exchange("http://127.0.0.1:8080/userinfo", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    Map body = Objects.requireNonNull(response.getBody());
    assertTrue(body.containsKey("sub"));
}
```
