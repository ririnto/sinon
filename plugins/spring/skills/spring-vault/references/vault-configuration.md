# Vault configuration and testing

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
