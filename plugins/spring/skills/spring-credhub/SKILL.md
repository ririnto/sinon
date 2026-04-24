---
name: "spring-credhub"
description: "Integrate Spring applications with CredHub for credential reads, writes, generated passwords or certificates, interpolation, and mutual-TLS or OAuth2 authenticated client access. Use this skill when integrating a Spring application with CredHub for credential reads, writes, generated passwords or certificates, interpolation, and mutual-TLS or OAuth2 authenticated client access."
metadata:
  title: "Spring CredHub"
  official_project_url: "https://spring.io/projects/spring-credhub"
  reference_doc_urls:
    - "https://docs.spring.io/spring-credhub/docs/current/reference/"
  version: "4.0.1"
---

Use this skill when integrating a Spring application with CredHub for credential reads, writes, generated passwords or certificates, interpolation, and mutual-TLS or OAuth2 authenticated client access.

The current stable Spring CredHub line is 4.0.1. Use the official Spring CredHub starter and keep mutual TLS or OAuth2 selection aligned with the platform's existing CredHub authentication path instead of inventing a second auth mode inside the application.

## Boundaries

Use `spring-credhub` for application-side CredHub client usage, credential naming, typed credential reads, credential generation requests, and secure client configuration.

- Use `spring-vault` for HashiCorp Vault integration, lease handling, and Vault-specific secret backends.
- Keep this skill focused on consuming the CredHub API from an application, not on platform-level CredHub operator procedures.

## Common path

The ordinary Spring CredHub job is:

1. Choose the authentication mode that the platform already supports: mutual TLS first, OAuth2 only when certificates are not available.
2. Add the CredHub starter and configure the API base URL.
3. Read or write credentials through a small service layer instead of scattering path lookups across the application.
4. Keep credential names stable and environment-scoped.
5. Use `CredHubOperations` by default and switch to `ReactiveCredHubOperations` only when the surrounding application flow is already reactive.
6. Add a focused test around the service boundary and verify failures do not leak secrets into logs.

### Branch selector

| Situation | Stay here or open a reference |
| --- | --- |
| Mutual TLS or OAuth2 selection for an ordinary service boundary | Stay in `SKILL.md` |
| Startup or off-request OAuth2 token acquisition is the blocker | Open [references/auth-and-credential-variants.md](references/auth-and-credential-variants.md) |
| The application flow is fully reactive | Open [references/reactive-access.md](references/reactive-access.md) |
| Interpolation, certificate generation, permissions, or less-common credential families are the blocker | Open [references/advanced-credential-patterns.md](references/advanced-credential-patterns.md) |

## Dependency baseline

Use the Spring CredHub starter for ordinary Boot-based integration.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.credhub</groupId>
        <artifactId>spring-credhub-starter</artifactId>
        <version>4.0.1</version>
    </dependency>
</dependencies>
```

## First safe configuration

### First safe commands

```bash
./mvnw test -Dtest=DatabaseCredentialServiceTests
```

```bash
./gradlew test --tests DatabaseCredentialServiceTests
```

### Mutual TLS client properties

```yaml
spring:
  credhub:
    url: https://credhub.example.com:8844
    tls:
      enabled: true
      key-store: classpath:credhub-client.p12
      key-store-password: ${CREDHUB_KEYSTORE_PASSWORD}
      trust-store: classpath:credhub-truststore.p12
      trust-store-password: ${CREDHUB_TRUSTSTORE_PASSWORD}
```

### OAuth2 client properties

```yaml
spring:
  credhub:
    url: https://credhub.example.com:8844
    oauth2:
      registration-id: credhub-client
```

Prefer one authentication path per application profile so startup behavior stays predictable.

## Coding procedure

1. Start by fixing the credential path contract such as `/app/{env}/db/password` before writing any client code.
2. Inject `CredHubOperations` into a narrow service layer by default, and use `ReactiveCredHubOperations` only when the application flow is already reactive end to end.
3. Read typed credentials where possible instead of treating every secret as an unstructured string.
4. Use generated credentials for passwords or certificates only when the platform expects CredHub to own rotation.
5. Fail closed: when a credential is missing or unreadable, return a controlled application error and do not print secret values.
6. Test both the happy path and missing-credential behavior at the service boundary.

## Failure classification

- Treat missing credentials as controlled application failures, not as nullable happy-path reads.
- Treat permission denials as request failures that must surface clearly instead of being retried blindly.
- Treat mutual-TLS handshake failures and OAuth2 token acquisition failures as startup or boundary-auth failures.
- Treat network timeouts or CredHub 5xx responses as transient infrastructure failures and surface them through health or retry policy, not secret-value fallbacks.

## Implementation examples

### Typed password credential access

```java
@Service
class DatabaseCredentialService {
    private final CredHubOperations credHub;

    DatabaseCredentialService(CredHubOperations credHub) {
        this.credHub = credHub;
    }

    String password() {
        return credHub.credentials()
            .getByName(new SimpleCredentialName("/app/prod/db-password"), PasswordCredential.class)
            .getValue().getPassword();
    }
}
```

### JSON credential lookup for grouped settings

```java
@Service
class MessagingCredentialService {
    private final CredHubOperations credHub;

    MessagingCredentialService(CredHubOperations credHub) {
        this.credHub = credHub;
    }

    JsonCredential credentials() {
        return credHub.credentials()
            .getByName(new SimpleCredentialName("/app/prod/messaging"), JsonCredential.class)
            .getValue();
    }
}
```

### Writing a simple value credential

```java
@Service
class FeatureFlagWriter {
    private final CredHubOperations credHub;

    FeatureFlagWriter(CredHubOperations credHub) {
        this.credHub = credHub;
    }

    void writeFlag(String environment, String value) {
        credHub.credentials().set(new SimpleCredentialName("/app/%s/feature-flag".formatted(environment)), new ValueCredential(value));
    }
}
```

### Requesting a generated password credential

```java
@Service
class PasswordGenerationService {
    private final CredHubOperations credHub;

    PasswordGenerationService(CredHubOperations credHub) {
        this.credHub = credHub;
    }

    void generateDatabasePassword() {
        credHub.credentials().generatePassword(new SimpleCredentialName("/app/prod/db-password"));
    }
}
```

### JUnit 5 service-boundary test shape

```java
@SpringBootTest
class DatabaseCredentialServiceTests {
    @Autowired
    DatabaseCredentialService service;

    @Test
    void passwordReturnsValueFromCredHub() {
        String password = service.password();
        assertAll(
            () -> assertNotNull(password),
            () -> assertFalse(password.isEmpty())
        );
    }
}
```

## Output and configuration shapes

### Credential path shape

```text
/app/{environment}/{service}/{credential-name}
```

### Password credential access shape

```java
PasswordCredential credential = credHub.credentials()
    .getByName(new SimpleCredentialName("/app/prod/db-password"), PasswordCredential.class)
    .getValue();
```

### JSON credential structure shape

```json
{
  "username": "app",
  "password": "secret",
  "host": "db.example.com",
  "port": 5432
}
```

## Output contract

Return:

1. The selected auth mode and why it matches the platform's certificate or token path
2. The credential path contract and typed credential classes used for each path
3. The service-boundary read or write shape used by the application
4. The failure behavior for missing credentials, permission denials, and auth failures
5. The test shape proving one happy path and one controlled failure boundary
6. Any blocker that requires OAuth2 manager customization, reactive access, interpolation, or certificate generation

## Testing checklist

- Verify the service requests the expected credential path for the active environment.
- Verify typed credential reads map to the expected application fields.
- Verify missing credentials produce a controlled failure path without logging secret contents.
- Verify generated password or certificate requests target the expected CredHub name.
- Verify mutual TLS or OAuth2 configuration fails fast when the required client material is absent.
- Use `assertAll(...)` when one test checks multiple properties of the same credential read.

## Production checklist

- Never log credential values, generated passwords, private keys, or certificate payloads.
- Keep CredHub path conventions stable across environments so rotation jobs and applications resolve the same names.
- Validate client certificate renewal or OAuth2 token renewal before production rollout.
- Keep CredHub HTTP wire logging disabled outside isolated local debugging because tokens and credential payloads can leak.
- Bound network timeouts and surface CredHub availability failures through application health signals.
- Prefer credential reads at startup only for static requirements; for runtime refresh, make the refresh boundary explicit in the service design.

## References

- Open [references/auth-and-credential-variants.md](references/auth-and-credential-variants.md) when the blocker is choosing between mutual TLS and OAuth2 client authentication.
- Open [references/reactive-access.md](references/reactive-access.md) when the blocker is reactive-only CredHub access through `ReactiveCredHubOperations`.
- Open [references/advanced-credential-patterns.md](references/advanced-credential-patterns.md) when the blocker is advanced credential usage beyond the ordinary password, JSON, or value path.
