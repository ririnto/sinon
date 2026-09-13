# Testing the authorization endpoint

Open this reference when the minimal validation is not enough and the blocker is deeper authorization-endpoint verification.

```java
@Test
void authorizationRequestWithPkceSucceeds() {
    OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
        .clientId("test-client")
        .redirectUri("http://127.0.0.1:8080/callback")
        .scopes(Set.of("openid", "message.read"))
        .apply(OAuth2AuthorizationRequestCustomizers.withPkce())
        .build();
    assertAll(() -> assertNotNull(request.getAttribute(PkceParameterNames.CODE_VERIFIER)), () -> assertEquals("S256", request.getAdditionalParameters().get(PkceParameterNames.CODE_CHALLENGE_METHOD)));
}
```

## Official documentation

- [Getting started](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/getting-started.html)
- [Authorization code grant and PKCE](https://docs.spring.io/spring-authorization-server/reference/guides/how-to-pkce.html)
