# Spring Vault reactive access

Open this reference when the surrounding flow is already reactive and secret access happens on the request path, so the task needs `ReactiveVaultTemplate` instead of the ordinary imperative client.

Keep the ordinary path imperative unless the surrounding application is already reactive.

## Reactive read shape

```java
@Service
class ReactiveSecretService {
    private final ReactiveVaultTemplate vault;

    ReactiveSecretService(ReactiveVaultTemplate vault) {
        this.vault = vault;
    }

    Mono<Map<String, Object>> readSecret() {
        return vault.read("secret/data/app/prod/database")
            .map(response -> (Map<String, Object>) response.getRequiredData().get("data"));
    }
}
```

This shape is low-level KV v2 handling. On the raw `secret/data/...` path, the response still contains the outer KV v2 wrapper, so the nested `data` map must be extracted explicitly.

## Decision points

| Situation | Use |
| --- | --- |
| Secret lookup is on a reactive request path | `ReactiveVaultTemplate` |
| Secret access is startup-only or service-layer imperative work | `VaultTemplate` |

## Gotchas

- Do not wrap blocking Vault access in reactive types when the reactive template is already available.
- Do not mix imperative and reactive secret clients casually inside the same boundary service.
- Do not treat reactive access as a substitute for KV version control or alternative auth configuration.
