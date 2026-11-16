# Vault dependencies

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
The Spring Cloud Vault examples target the 5.0.x line.
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

Before configuring Spring Cloud Vault authentication or properties, or testing that integration, read [Vault configuration](./vault-configuration.md).
