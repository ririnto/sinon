# Vault implementation examples

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
        return keyValue.get("app/prod/database").getRequiredData();
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

## Edge cases

- Treat transit as part of this skill only for the basic encrypt or decrypt boundary.
  - More specialized secret-engine work should stay outside the ordinary KV path.
- `VaultClient` requires a `VaultEndpoint` or `VaultEndpointProvider` for relative path usage.
  - Without one, callers must provide absolute URLs for every request.
- `opsForKeyValue(String path)` queries `sys/internal/ui/mounts/...` at runtime.
  - Ensure the token policy allows accessing that path.
- `ManagedSecret` availability depends on `SecretLeaseContainer` lifecycle.
  - Components that need credentials during `@PostConstruct` must declare `SecretLeaseContainer` as a dependency to ensure correct ordering.

Before implementing Vault KV reads or writes, transit, property import, low-level HTTP, or managed rotation, read [Vault examples](./vault-examples.md).
Use config import only when startup must fail if Vault configuration is unavailable.
