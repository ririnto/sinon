# Spring CredHub reactive access

Open this reference when the blocker is reactive-only CredHub access through `ReactiveCredHubOperations`.

Use `ReactiveCredHubOperations` only when the surrounding application flow is already reactive and blocking a request thread would be the real problem.

Reactive access is available in the current stable 4.0.1 line, but it remains additive to the ordinary blocking client path in [SKILL.md](../SKILL.md).

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

## Reactive test shape

```java
@SpringBootTest
class ReactiveSecretReaderTests {
    @Autowired
    ReactiveSecretReader reader;

    @Test
    void passwordReadCompletes() {
        StepVerifier.create(reader.readPassword())
            .assertNext(password -> assertFalse(password.isEmpty()))
            .verifyComplete();
    }
}
```

## Gotchas

- Do not switch to `ReactiveCredHubOperations` just because the project uses Reactor elsewhere.
- Do not hide a blocking CredHub client behind reactive adapters when the reactive client boundary already exists.
- Do not treat reactive access as a replacement for auth-mode selection or advanced credential-family guidance.

## Decision points

| Situation | First check |
| --- | --- |
| Application flow is servlet or blocking | keep `CredHubOperations` |
| Application flow is already reactive | use `ReactiveCredHubOperations` |
| Reactive path still blocks | verify no blocking client is wrapped in reactive adapters |

## Related blockers

- Return to [SKILL.md](../SKILL.md) when the blocker is the ordinary blocking service boundary.
- Open [auth-and-credential-variants.md](auth-and-credential-variants.md) when the reactive path is blocked by client authentication setup.
- Open [advanced-credential-patterns.md](advanced-credential-patterns.md) when the reactive path is blocked by interpolation, certificate generation, or non-default credential families.
