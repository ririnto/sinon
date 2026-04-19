# Spring Security SAML2 login

Open this reference when the task specifically depends on SAML2 because an enterprise identity provider requires it.

Use SAML2 only when the identity provider or enterprise environment already requires it.

## Dependency boundary

SAML2 login needs the service-provider module in addition to the ordinary starter.

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-saml2-service-provider</artifactId>
</dependency>
```

## Minimal relying-party configuration

```yaml
spring:
  security:
    saml2:
      relyingparty:
        registration:
          adfs:
            assertingparty:
              entity-id: https://idp.example.com/issuer
              verification.credentials:
                - certificate-location: classpath:idp.crt
              singlesignon.url: https://idp.example.com/issuer/sso
              singlesignon.sign-request: false
```

The `registrationId` determines the login processing path.

## Security filter chain shape

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .saml2Login(Customizer.withDefaults())
        .build();
}
```

## Programmatic relying-party registration

```java
@Bean
RelyingPartyRegistrationRepository relyingPartyRegistrations() {
    RelyingPartyRegistration registration = RelyingPartyRegistrations
        .fromMetadataLocation("https://idp.example.com/metadata")
        .registrationId("adfs")
        .build();

    return new InMemoryRelyingPartyRegistrationRepository(registration);
}
```

## Decision points

| Situation | Guidance |
| --- | --- |
| Enterprise IdP mandates SAML2 | use this path |
| OAuth2 or OIDC already available | prefer simpler delegated login path |
| Metadata available from IdP | build registration from metadata first |
