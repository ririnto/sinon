---
name: "spring-vault"
description: "Use this skill when integrating Spring applications with HashiCorp Vault for KV secret reads and writes, AppRole or token authentication, property-source loading, or transit encryption."
metadata:
  title: "Spring Vault"
  official_project_url: "https://spring.io/projects/spring-vault"
  reference_doc_urls:
    - "https://docs.spring.io/spring-vault/reference/index.html"
  version: "4.0.1"
---

Use this skill when integrating Spring applications with HashiCorp Vault for KV secret reads and writes, AppRole or token authentication, property-source loading, or transit encryption.

## Boundaries

Use `spring-vault` for application-side Vault access, authentication setup, KV engine access, transit operations, and Spring configuration import from Vault.

- Keep CredHub-specific secret access and credential generation outside this skill's scope.
- Keep this skill focused on application integration. Vault cluster operations, seal management, and policy administration are platform concerns.
- Keep one auth mode per runtime profile so startup and token-renewal behavior stay predictable.
- Keep Kubernetes auth, reactive access, and versioned KV behavior out of the ordinary path unless the task explicitly needs them.
- Keep Vault repositories, PKI certificate issuance, and Spring Security crypto integration outside the ordinary path unless those surfaces are the actual blocker.

## Common path

The ordinary Spring Vault job is:

1. Pick the smallest authentication mode the deployment already supports, usually token for local work and AppRole for production.
2. Decide whether the application reads secrets directly through `VaultTemplate` or loads configuration from Vault during bootstrap.
3. Keep the secret path contract stable across environments and explicit about KV v1 versus KV v2 semantics.
4. Prefer KV reads and writes through a narrow service layer rather than scattered path strings.
5. Fail fast on missing or forbidden secrets and add a focused test for the secret access boundary.

### Branch selector

- Stay in `SKILL.md` for the ordinary token-or-AppRole path: direct `VaultTemplate` access, KV read or write operations, property import, KV v1 versus KV v2 path awareness, transit encrypt or decrypt, and fail-fast handling for missing secrets.
- Open [references/kubernetes-authentication.md](references/kubernetes-authentication.md) when the runtime is in Kubernetes and Vault login must use the mounted service-account token.
- Open [references/reactive-vault-access.md](references/reactive-vault-access.md) when secret access is already on a reactive request path and the task needs `ReactiveVaultTemplate`.
- Open [references/kv-versioning-and-cas.md](references/kv-versioning-and-cas.md) when KV v2 version retrieval, CAS writes, or version-aware conflict handling matters.

## Dependency baseline

Use `spring-vault-core` for direct client access. Add Spring Boot config integration only when Vault-backed property loading is the actual requirement.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.vault</groupId>
        <artifactId>spring-vault-core</artifactId>
        <version>4.0.1</version>
    </dependency>
</dependencies>
```

## First safe configuration

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

## Coding procedure

1. Fix the secret path shape first, such as `secret/data/app/{env}/database` for KV v2.
2. Keep auth configuration outside business services and inject `VaultTemplate` into a narrow boundary service.
3. Distinguish KV v1 and KV v2 paths explicitly. Prefer `opsForKeyValue(...)` for ordinary KV access instead of teaching raw `data/` path handling as the main integration shape.
4. Read typed secret fields into application-specific records or value objects as early as possible.
5. Use `getRequiredData()` when the read path must fail fast instead of silently tolerating a missing payload.
6. Use transit only when the application needs Vault-managed encryption or signing without exposing raw key material.
7. Treat missing secrets, permission denials, and version conflicts as controlled failures, not recoverable happy-path events.

## Edge cases

- Open [references/kubernetes-authentication.md](references/kubernetes-authentication.md) when the application already runs inside Kubernetes and Vault should trust the pod identity.
- Open [references/reactive-vault-access.md](references/reactive-vault-access.md) when secret access must stay reactive end to end.
- Open [references/kv-versioning-and-cas.md](references/kv-versioning-and-cas.md) when multiple writers, CAS, or version retrieval matter.
- Treat transit as part of this skill only for the basic encrypt or decrypt boundary. More specialized secret-engine work should stay outside the ordinary KV path.

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

```yaml
spring:
  config:
    import: vault://
```

Use config import only when application startup should fail if Vault configuration is unavailable.

## Output contract

Return:

1. The chosen Vault auth mode and why it fits the runtime
2. The secret path contract, including whether access is direct or configuration import
3. Whether the implementation relies on KV v1 or KV v2 semantics
4. The failure behavior for missing secrets, permission denials, or startup import failures
5. The test shape proving the secret access boundary
6. Any blocker that requires Kubernetes auth, reactive access, or KV versioning and CAS behavior

## Testing checklist

- Verify the service reads or writes the expected Vault path for the active environment.
- Verify KV v2 reads unwrap the expected payload and fail fast when required data is absent.
- Verify missing secrets and permission denials fail without leaking secret values.

## Production checklist

- Never log Vault tokens, AppRole secret ids, decrypted secret values, or transit plaintext.
- Align token renewal or AppRole credential rotation with the application's lifecycle expectations.
- Keep secret path conventions stable so deployments, policies, and applications agree on location.
- Bound Vault client timeouts and surface Vault availability through health or startup failure signals.
- Use the narrowest policy needed for the application's read, write, or transit operations.

## References

- Open [references/kubernetes-authentication.md](references/kubernetes-authentication.md) when the ordinary token-or-AppRole path is not enough and the task needs Kubernetes auth.
- Open [references/reactive-vault-access.md](references/reactive-vault-access.md) when the ordinary imperative client path is not enough and the task needs `ReactiveVaultTemplate`.
- Open [references/kv-versioning-and-cas.md](references/kv-versioning-and-cas.md) when the ordinary KV read-or-write path is not enough and the task needs KV v2 version control or CAS behavior.
