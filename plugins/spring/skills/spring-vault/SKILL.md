---
name: spring-vault
description: >-
  Integrate Spring applications with Vault or CredHub authentication, secret access, property loading, transit encryption, and credential rotation.
---

# Spring Vault and CredHub

The latest released Spring Vault line is 4.1.x.
Pin the concrete artifact example in this skill to 4.1.0 because this skill documents the current released standalone client path rather than a Spring BOM-managed path.

## Boundaries

Use `spring-vault` for application-side Vault or CredHub access, authentication setup, KV engine access, transit operations, credential management, and Spring configuration import from Vault or CredHub.

- Keep this skill focused on application integration.
  - Vault cluster operations, seal management, and policy administration are platform concerns.
- Keep one auth mode per runtime profile so startup and token-renewal behavior stay predictable.
- Keep Kubernetes auth, reactive access, and versioned KV behavior out of the ordinary path unless the task explicitly needs them.
- Keep Vault repositories, PKI certificate issuance, and Spring Security crypto integration outside the ordinary path unless those surfaces are the actual blocker.
- Keep CredHub advanced credential families, interpolation, and reactive CredHub access in references unless the task explicitly needs them.

## Vendor decision

| Condition | Use |
| --- | --- |
| HashiCorp Vault is the secret store | Vault paths in this skill |
| Cloud Foundry CredHub is the secret store | CredHub paths in this skill |

Vault and CredHub follow the same integration pattern: configure a client, authenticate, read and write secrets.
The code shapes differ but the workflow is equivalent.

## Surface map

| Surface | Start here when | Open a reference when |
| --- | --- | --- |
| Token or AppRole auth | one application reads or writes secrets in an imperative path | stay in `SKILL.md` |
| Property import from Vault | startup configuration must come from Vault | stay in `SKILL.md` |
| Transit encrypt/decrypt | the app needs Vault-managed crypto without raw key material | stay in `SKILL.md` |
| Kubernetes auth | pod identity and service-account token are the real auth boundary | open [references/kubernetes-authentication.md](references/kubernetes-authentication.md) |
| Reactive secret access | secret reads are already on a reactive request path | open [references/reactive-vault-access.md](references/reactive-vault-access.md) |
| KV v2 versioning and CAS | multiple writers or explicit secret versions matter | open [references/kv-versioning-and-cas.md](references/kv-versioning-and-cas.md) |
| Credential rotation | secrets must renew or rotate during application lifetime | open [references/credential-rotation.md](references/credential-rotation.md) |
| Certificate issuance and rotation | PKI certificates must rotate on a schedule | open [references/credential-rotation.md](references/credential-rotation.md) |
| Vault repositories and query keywords | domain objects must persist in Vault with query derivation | open [references/repositories.md](references/repositories.md) |
| CredHub credential read/write | Cloud Foundry CredHub is the secret store and the app reads or writes typed credentials | stay in `SKILL.md` |
| CredHub auth (mutual TLS / OAuth2) | the app must authenticate with CredHub using client certificates or OAuth2 tokens | open [references/credhub-auth-and-credential-variants.md](references/credhub-auth-and-credential-variants.md) |
| CredHub reactive access | CredHub reads are already on a reactive request path | open [references/credhub-reactive-access.md](references/credhub-reactive-access.md) |
| CredHub advanced credential families | interpolation, certificate generation, permissions, or non-default credential types are the blocker | open [references/credhub-advanced-credential-patterns.md](references/credhub-advanced-credential-patterns.md) |

## Task scope

Use the deployment's supported authentication mode, usually token for local work and AppRole for production.
Distinguish direct client access from startup configuration loading.
Preserve secret paths and authentication policy unless changing them is authorized.
Use the sections and references for the affected authentication, secret engine, property import, or credential lifecycle.

## Dependency baseline

Use `spring-vault-core` for direct client access.
Use Spring Cloud Vault (`spring-cloud-starter-vault-config`) only when Vault-backed property loading is the actual requirement.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.vault</groupId>
        <artifactId>spring-vault-core</artifactId>
        <version>4.1.0</version>
    </dependency>
</dependencies>
```

`spring-vault-core` does not bind `spring.cloud.vault.*` properties and does not provide a `vault://` config-import resolver.
Those belong to Spring Cloud Vault (`spring-cloud-starter-vault-config`), which is managed by the Spring Cloud release train and brings `spring-vault-core` transitively.
The current Spring Cloud Vault line is 5.0.x.
Add it only when the application loads Vault-backed property sources at startup, and let the Spring Cloud BOM manage its version.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-vault-config</artifactId>
    </dependency>
</dependencies>
```

### Feature-to-artifact map

| Need | Artifact or module |
| --- | --- |
| Imperative Vault client access with `VaultTemplate` | `spring-vault-core` |
| Reactive secret access with `ReactiveVaultTemplate` | `spring-vault-core` |
| Low-level Vault HTTP access with `VaultClient` | `spring-vault-core` |
| KV v2 version-aware access and CAS | `spring-vault-core` |
| Declarative secret rotation with `ManagedSecret` | `spring-vault-core` |
| Certificate issuance and rotation with `CertificateContainer` | `spring-vault-core` |
| Vault-backed property import with `spring.cloud.vault.*` and `spring.config.import: vault://` | Spring Cloud Vault (`spring-cloud-starter-vault-config`) |
| Tests that prove the secret boundary | the project test stack plus focused Vault integration tests |

## First safe configuration

The `spring.cloud.vault.*` examples in this section require Spring Cloud Vault (`spring-cloud-starter-vault-config`).
`spring-vault-core` alone does not bind these properties.

### Token-authenticated local setup

```yaml
spring:
  cloud:
    vault:
      uri: https://vault.example.com:8200
      token: ${VAULT_TOKEN}
      kv:
        enabled: true
        backend: secret
        application-name: app
```

### AppRole-authenticated setup

```yaml
spring:
  cloud:
    vault:
      uri: https://vault.example.com:8200
      authentication: approle
      app-role:
        role-id: ${VAULT_ROLE_ID}
        secret-id: ${VAULT_SECRET_ID}
      kv:
        enabled: true
        backend: secret
        application-name: app
```

### Common property keys

Keep these keys explicit in the active profile:

| Property | Purpose |
| --- | --- |
| `spring.cloud.vault.uri` | Vault endpoint |
| `spring.cloud.vault.authentication` | auth mode such as `token` or `approle` |
| `spring.cloud.vault.kv.enabled` | enable KV property access |
| `spring.cloud.vault.kv.backend` | backend mount such as `secret` |
| `spring.cloud.vault.kv.application-name` | application path segment |

Prefer one auth mode per runtime profile so startup and renewal behavior stay obvious.

### First safe commands

```sh
./mvnw test -Dtest=SecretServiceTests
```

```sh
./gradlew test --tests SecretServiceTests
```

### First safe testing shape

```java
@SpringBootTest
class SecretServiceTests {
    @Autowired
    SecretService secretService;

    @Test
    void databaseSecretReturnsRequiredData() {
        Map<String, Object> secret = secretService.readDatabaseSecret();
        assertAll(() -> assertNotNull(secret), () -> assertTrue(secret.containsKey("username")));
    }
}
```

## Implementation guidance

- Keep the secret path contract explicit, such as `secret/data/app/{env}/database` for KV v2.
- Keep auth configuration outside business services and inject `VaultTemplate` into a narrow boundary service.
- Distinguish KV v1 and KV v2 paths explicitly.
  Prefer `opsForKeyValue(...)` for ordinary KV access over raw `data/` path handling.
- `opsForKeyValue(String path)` auto-detects KV v1 vs v2 at runtime (4.1).
  Explicit `opsForKeyValue(String, KeyValueBackend)` overrides when the mount version is known and detection overhead is unacceptable.
- Use `VaultClient` for low-level Vault HTTP access instead of `RestOperations` or `RestTemplate`.
- Read typed secret fields into application-specific records or value objects as early as possible.
- Use `getRequiredData()` when the read path must fail fast instead of silently tolerating a missing payload.
- Use transit only when the application needs Vault-managed encryption or signing without exposing raw key material.
- Treat missing secrets, permission denials, and version conflicts as controlled failures, not recoverable happy-path events.

## Edge cases

- Treat transit as part of this skill only for the basic encrypt or decrypt boundary.
  - More specialized secret-engine work should stay outside the ordinary KV path.
- `VaultClient` requires a `VaultEndpoint` or `VaultEndpointProvider` for relative path usage.
  - Without one, callers must provide absolute URLs for every request.
- `opsForKeyValue(String path)` queries `sys/internal/ui/mounts/...` at runtime.
  - Ensure the token policy allows accessing that path.
- `ManagedSecret` availability depends on `SecretLeaseContainer` lifecycle.
  - Components that need credentials during `@PostConstruct` must declare `SecretLeaseContainer` as a dependency to ensure correct ordering.

## Implementation examples

### KV v2 secret read with `VaultTemplate`

```java
@Service
class SecretService {
    private final VaultKeyValueOperations keyValue;

    SecretService(VaultTemplate vault) {
        this.keyValue = vault.opsForKeyValue("secret", KeyValueBackend.KV_2);
    }

    Map<String, Object> readDatabaseSecret() {
        Versioned<Map<String, Object>> version = keyValue.get("app/prod/database");
        return version.getRequiredData();
    }
}
```

### KV write for application configuration

```java
@Service
class SecretWriter {
    private final VaultKeyValueOperations keyValue;

    SecretWriter(VaultTemplate vault) {
        this.keyValue = vault.opsForKeyValue("secret", KeyValueBackend.KV_2);
    }

    void writeFeatureFlag(String environment, String value) {
        keyValue.put("app/%s/feature".formatted(environment), Map.of("enabled", value));
    }
}
```

### Transit encryption boundary

```java
@Service
class TransitEncryptionService {
    private final VaultTransitOperations transit;

    TransitEncryptionService(VaultTemplate vault) {
        this.transit = vault.opsForTransit();
    }

    String encrypt(String plaintext) {
        return transit.encrypt("app-key", plaintext);
    }
}
```

### Vault-backed property import shape

The `vault://` import resolver is provided by Spring Cloud Vault (`spring-cloud-starter-vault-config`), not by `spring-vault-core`.

```yaml
spring:
  config:
    import: vault://
```

Use config import only when application startup should fail if Vault configuration is unavailable.

### KV read with auto-detected version (4.1)

```java
VaultKeyValueOperations keyValue = vault.opsForKeyValue("secret");
```

`opsForKeyValue(String path)` queries Vault mount metadata and returns either `VaultKeyValue1Template` or `VaultKeyValue2Template`.
Use this when the KV version is not known at compile time.

### VaultClient for low-level Vault HTTP access (4.1)

`VaultClient` replaces `RestOperations`/`RestTemplate` for low-level Vault interaction.
It enforces path-based entry points and prevents accidental requests to non-Vault servers.

```java
VaultResponse read = vaultClient.get()
        .path("secret/data/my-key")
        .retrieve()
        .body(VaultResponse.class);

vaultClient.post()
        .path("transit/encrypt/my-key")
        .body(Map.of("plaintext", Base64.getEncoder().encodeToString(plaintext)))
        .retrieve()
        .toBodilessEntity();
```

`AbstractVaultConfiguration` creates `VaultClient` automatically.
Register a `VaultClientCustomizer` bean to customize the instance.

### ManagedSecret for declarative rotation (4.1)

`ManagedSecret` registers with `SecretLeaseContainer` and handles lease events without manual event listeners.
`ManagedSecret` beans are auto-registered when declared in `AbstractVaultConfiguration` subclasses.

```java
@Configuration
class MyConfiguration {
    @Bean
    ManagedSecret databaseCredentials(HikariDataSource dataSource) {
        return ManagedSecret.rotating("mysql/creds/my-role", secrets -> secrets.as(UsernamePassword::from)
                .applyTo((username, password) -> {
                    dataSource.setUsername(username);
                    dataSource.setPassword(password);
                }));
    }
}
```

## CredHub integration (Cloud Foundry)

Use this section when the secret store is Cloud Foundry CredHub instead of HashiCorp Vault.
Spring CredHub 4.0.x provides the client library.

### CredHub dependency baseline

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.credhub</groupId>
        <artifactId>spring-credhub-starter</artifactId>
        <version>4.0.1</version>
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

## Output contract

Report the changed Vault or CredHub contract, authentication and failure behavior, and verification results.
Identify unverified integration behavior or blockers without exposing secrets.
Follow the task's required response format.

## Output shapes

### KV read shape

```json
{
  "username": "app",
  "password": "secret-value"
}
```

### Transit encrypt shape

```text
vault:v1:8sd7f6...
```

### Missing secret failure shape

```text
Vault secret missing at secret/app/prod/database
```

### CredHub credential read shape

```java
credHub.credentials().getByName(new SimpleCredentialName("/app/prod/db-password"), PasswordCredential.class)
```

### CredHub credential write shape

```java
credHub.credentials().write(ValueCredentialRequest.builder().name(new SimpleCredentialName("/app/%s/feature-flag".formatted(env))).value(new ValueCredential(value)).build());
```

### CredHub missing credential failure shape

```text
CredHub credential unreadable: /app/prod/db-password
```

## Vault verification

Use existing tests for the changed Vault access or configuration boundary.
Choose integration coverage when authentication, mount behavior, or startup property loading needs the real client boundary.
Select the checks that apply:

- Verify the service reads or writes the expected Vault path for the active environment.
- Verify KV v2 reads unwrap the expected payload and fail fast when required data is absent.
- Verify missing secrets and permission denials fail without leaking secret values.
- For authentication or property-import changes, verify the affected integration boundary.

## Vault production guardrails

- Never log Vault tokens, AppRole secret ids, decrypted secret values, or transit plaintext.
- Align token renewal or AppRole credential rotation with the application's lifecycle expectations.
- Keep secret path conventions stable so deployments, policies, and applications agree on location.
- Bound Vault client timeouts and surface Vault availability through health or startup failure signals.
- Use the narrowest policy needed for the application's read, write, or transit operations.
