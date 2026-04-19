# Spring CredHub reactive access

Open this reference when the blocker is reactive-only CredHub access through `ReactiveCredHubOperations`.

Use `ReactiveCredHubOperations` only when the surrounding application flow is already reactive and blocking a request thread would be the real problem.

```java
@Service
class ReactiveSecretReader {
    private final ReactiveCredHubOperations credHub;

    ReactiveSecretReader(ReactiveCredHubOperations credHub) {
        this.credHub = credHub;
    }

    Mono<String> readPassword() {
        return credHub.credentials()
            .getByName(new SimpleCredentialName("/app/prod/db-password"), PasswordCredential.class)
            .map(credential -> credential.getValue().getPassword());
    }
}
```

Keep the boundary either fully reactive or fully blocking. Do not wrap a blocking CredHub client in `Mono.fromCallable()` when the reactive client already exists.

## Decision points

| Situation | First check |
| --- | --- |
| Application flow is servlet or blocking | keep `CredHubOperations` |
| Application flow is already reactive | use `ReactiveCredHubOperations` |
| Reactive path still blocks | verify no blocking client is wrapped in reactive adapters |
