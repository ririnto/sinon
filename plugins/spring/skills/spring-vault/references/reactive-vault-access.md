# Spring Vault reactive access

Open this reference when the surrounding flow is already reactive and secret access happens on the request path, so the task needs `ReactiveVaultTemplate` instead of the ordinary imperative client.

Keep the ordinary path imperative unless the surrounding application is already reactive.

## Reactive read shape

```java
@Service
class ReactiveSecretService {
    private final ReactiveVaultVersionedKeyValueOperations keyValue;

    ReactiveSecretService(ReactiveVaultTemplate vault) {
        this.keyValue = vault.opsForVersionedKeyValue("secret");
    }

    Mono<Map<String, Object>> readSecret() {
        return keyValue.get("app/prod/database")
            .map(Versioned::getRequiredData);
    }
}
```

Use the versioned reactive key-value operations when the application already knows it is talking to a KV v2 mount. Keep raw `secret/data/...` path handling out of the ordinary reactive path unless the task is explicitly about low-level Vault HTTP semantics.

## Decision points

| Situation | Use |
| --- | --- |
| Secret lookup is on a reactive request path | `ReactiveVaultTemplate` |
| Secret access is startup-only or service-layer imperative work | `VaultTemplate` |

## Gotchas

- Do not wrap blocking Vault access in reactive types when the reactive template is already available.
- Do not mix imperative and reactive secret clients casually inside the same boundary service.
- Do not treat reactive access as a substitute for KV version control or alternative auth configuration.

## Validation rule

Verify the reactive path stays on `ReactiveVaultTemplate` and versioned reactive key-value operations end to end instead of falling back to blocking imperative access.
