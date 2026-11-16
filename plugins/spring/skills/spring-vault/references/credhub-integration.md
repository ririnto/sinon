# CredHub integration

## CredHub integration (Cloud Foundry)

Use this section when the secret store is Cloud Foundry CredHub instead of HashiCorp Vault.
Spring CredHub 4.1.x provides the client library.

### CredHub dependency baseline

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.credhub</groupId>
        <artifactId>spring-credhub-starter</artifactId>
        <version>4.1.0</version>
    </dependency>
    <dependency>
        <groupId>org.apache.httpcomponents.client5</groupId>
        <artifactId>httpclient5</artifactId>
    </dependency>
</dependencies>
```

The starter does not bring Apache HttpClient 5 transitively.
Keep it in the dependency baseline when `ca-cert-files` must configure custom trust.
The JDK request-factory fallback does not install that trust material.

### CredHub first safe configuration

Mutual TLS (preferred):

The CredHub starter exposes no `spring.credhub.tls.*` namespace.
It binds only `spring.credhub.url`, `spring.credhub.oauth2.registration-id`, and the `ClientOptions` fields (`connection-timeout`, `read-timeout`, `ca-cert-files`).
Configure CA trust through `spring.credhub.ca-cert-files`, and configure client-certificate mutual TLS by supplying a custom `CredHubOperations` bean backed by a `ClientHttpRequestFactory` that carries the client certificate and trust material.

```yaml
spring:
  credhub:
    url: https://credhub.example.com:8844
    ca-cert-files:
      - /etc/credhub/credhub-ca.pem
```

OAuth2 (when client certificates are not available):

```yaml
spring:
  credhub:
    url: https://credhub.example.com:8844
    oauth2:
      registration-id: credhub-client
```

Keep OAuth2 client registration outside the skill-specific scope.
Service token acquisition stays a platform concern.
Prefer mutual TLS first, OAuth2 only when certificates are not available.
Keep one auth mode active per application profile.

### CredHub implementation guidance

- Keep the credential path contract explicit, such as `/app/{env}/db/password`.
- Inject `CredHubOperations` into a narrow service layer.
  Use `ReactiveCredHubOperations` only when the application flow is already reactive.
- Read typed credentials (`PasswordCredential`, `JsonCredential`, `ValueCredential`) where possible instead of treating every secret as a string.
- Use generated credentials for passwords or certificates only when the platform expects CredHub to own rotation.
- Fail closed: when a credential is missing or unreadable, return a controlled application error.
  Do not fall back to defaults silently.

### CredHub typed credential read

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

### CredHub JSON credential read

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

### CredHub credential write

`write` accepts a `CredentialRequest`, not a name and value pair.
Build a `ValueCredentialRequest` to write a value credential.

```java
@Service
class FeatureFlagWriter {
    private final CredHubOperations credHub;

    FeatureFlagWriter(CredHubOperations credHub) {
        this.credHub = credHub;
    }

    void writeFlag(String environment, String value) {
        credHub.credentials().write(ValueCredentialRequest.builder().name(new SimpleCredentialName("/app/%s/feature-flag".formatted(environment))).value(new ValueCredential(value)).build());
    }
}
```

### CredHub generated password

`generate` accepts a `ParametersRequest`, not a credential name.
Build a `PasswordParametersRequest` to generate a password.

```java
@Service
class PasswordGenerationService {
    private final CredHubOperations credHub;

    PasswordGenerationService(CredHubOperations credHub) {
        this.credHub = credHub;
    }

    void generateDatabasePassword() {
        credHub.credentials().generate(PasswordParametersRequest.builder().name(new SimpleCredentialName("/app/prod/db-password")).parameters(PasswordParameters.builder().length(30).build()).build());
    }
}
```

### CredHub failure classification

Treat missing credentials as controlled application errors, not configuration bugs.

```java
try {
    return credHub.credentials()
        .getByName(new SimpleCredentialName("/app/prod/db-password"), PasswordCredential.class)
        .getValue().getPassword();
} catch (CredHubException e) {
    throw new IllegalStateException("CredHub credential unreadable: /app/prod/db-password", e);
}
```

### CredHub first safe testing shape

```java
@Test
void passwordReturnsValueFromCredHub() {
    PasswordCredential credential = new PasswordCredential("s3cret");
    when(credHub.credentials().getByName(any(SimpleCredentialName.class), eq(PasswordCredential.class)))
        .thenReturn(new CredentialDetails<>("id", new SimpleCredentialName("/app/prod/db-password"), CredentialType.PASSWORD, credential));
    String password = service.password();
    assertAll(() -> assertNotNull(password), () -> assertFalse(password.isEmpty()));
}
```

### CredHub verification

Use existing service or integration tests for the affected credential behavior.
Select checks for the changed contract and its failure paths:

- Verify expected credential path matches the target environment.
- Verify typed credential classes used for each path.
- Verify missing credentials produce a controlled failure path without silent fallback.
- Verify generated password and certificate requests use the correct CredHub name.
- Verify mutual TLS or OAuth2 configuration fails when credentials are absent.

### CredHub production checklist

- Never log credential values, generated passwords, private keys, or certificate payloads.
- Keep CredHub path conventions stable across environments.
- Validate client certificate renewal or OAuth2 token renewal before production rollout.
- Keep CredHub HTTP wire logging disabled.
  Tokens and credential payloads can leak.
- Bound network timeouts and surface CredHub availability failures through application health signals.
