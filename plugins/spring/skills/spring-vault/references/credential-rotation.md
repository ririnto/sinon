# Spring Vault credential rotation and certificate management

Open this reference when secrets must renew or rotate during application lifetime, when PKI certificates need scheduled re-issuance, or when rotating secrets that do not carry a lease.

## SecretLeaseContainer

`SecretLeaseContainer` manages the lifecycle of leased secrets.
It renews leases before expiration and rotates credentials when necessary.

```java
SecretLeaseContainer container = new SecretLeaseContainer(vaultOperations, taskScheduler);
container.afterPropertiesSet();
container.start();
```

Register secrets through `RequestedSecret` and `LeaseListener`:

```java
RequestedSecret requestedSecret = RequestedSecret.rotating("mysql/creds/my-role");
container.register(requestedSecret, (lease, secrets) -> {
    Map<String, Object> data = secrets.getRequiredData();
    dataSource.setPassword(data.get("password").toString());
});
```

Secrets can be registered at any time after the container starts.
The container manages unique registrations and deduplicates identical `RequestedSecret` instances.

### TTL tuning

- `setExpiryThresholdSeconds` - how long before lease expiry the container considers a lease expired (default 60 seconds).
- `setMinRenewalSeconds` - minimum time between renewal requests to avoid excessive calls (default 10 seconds).

Both values are calculated from the lease TTL and local system clock.

## ManagedSecret API (4.1)

`ManagedSecret` is a higher-level abstraction that registers with `SecretLeaseContainer` and handles lease events on your behalf.
As an implementation of `SecretRegistrar`, `ManagedSecret` beans are registered with the container before startup through `AbstractVaultConfiguration`.

```java
@Configuration
class MyConfiguration {
    @Bean
    ManagedSecret mysqlCredentials(HikariDataSource dataSource) {
        return ManagedSecret.rotating("mysql/creds/my-role", secrets -> secrets.as(UsernamePassword::from)
                .applyTo((username, password) -> {
                    dataSource.setUsername(username);
                    dataSource.setPassword(password);
                }));
    }
}
```

### From RequestedSecret

```java
@Bean
ManagedSecret awsSecret(RequestedSecret requestedSecret) {
    return ManagedSecret.from(requestedSecret, secrets -> {
        String accessKey = secrets.getRequiredString("access_key");
        String secretKey = secrets.getRequiredString("secret_key");
    }, error -> {
        // handle rotation errors
    });
}
```

### Lifecycle ordering

`SecretLeaseContainer` lifecycle defers secret availability until the container starts.
`AbstractVaultConfiguration` starts the container during bean creation to allow early credentials access.
Ensure dependency ordering when components require credentials during `@PostConstruct` or `InitializingBean.afterPropertiesSet()`.

## Lease-less secret rotation (4.1)

Secrets obtained from generic secrets engines are associated with a TTL (`refresh_interval`) but not a lease ID.
Spring Vault rotates generic secrets when reaching their TTL.

`SecretLeaseContainer` now supports rotation for secrets that have a non-renewable lease.
Previously, only secrets with a renewable lease or no lease at all were rotated.
Secrets with a non-renewable lease (such as certificates issued without lease generation) are now eligible for rotation.

```java
RequestedSecret requestedSecret = RequestedSecret.renewing("secret/data/my-app")
        .withMode(RequestedSecret.Mode.ROTATE);
container.register(requestedSecret, (lease, secrets) -> {
    // called on initial request and each rotation
});
```

## CertificateContainer (4.1)

`CertificateContainer` manages certificates issued by Vault's PKI secrets engine.
Certificates typically have no lease and therefore do not require renewal.
Rotate them when they expire.

```java
CertificateContainer container = new CertificateContainer(vaultOperations.opsForPki());
container.afterPropertiesSet();
container.start();
RequestedCertificate cert = container
        .register(RequestedCertificate.trustAnchor("vault-ca"));
```

### Issuing a managed certificate

```java
VaultCertificateRequest request = VaultCertificateRequest.builder()
        .commonName("www.example.com")
        .ttl(Duration.ofHours(12))
        .build();

ManagedCertificate managed = ManagedCertificate.issue("my-bundle", "my-role", request, bundle -> {
    KeyStore keyStore = bundle.createKeyStore("my-alias");
});
managed.register(container);
```

### Decision points

| Situation | Use |
| --- | --- |
| Database credentials need periodic renewal | `SecretLeaseContainer` with `RequestedSecret.rotating` |
| Credentials should propagate to application components declaratively | `ManagedSecret` bean |
| PKI certificates need scheduled re-issuance | `CertificateContainer` with `ManagedCertificate` |
| Secrets without a renewable lease must rotate on TTL | `RequestedSecret` with `Mode.ROTATE` on a generic secret |
| Fine-grained control over lease events | `LeaseListener` directly on `SecretLeaseContainer` |

## Gotchas

- Do not assume secrets are available before `SecretLeaseContainer.start()`.
- Do not share `RestTemplate` between Vault operations and external service authentication without understanding the coupling risk.
- Do not register duplicate `RequestedSecret` instances expecting independent lifecycle management.
- Do not use lease renewal on certificates when Vault performance recommends issuing without a lease.
