# Spring gRPC in-process testing

Open this reference when integration tests should avoid a real network port, inject the local gRPC port into a stub, or prove server and client wiring with Spring test support.

## Choose the test transport

| Situation | Use |
| --- | --- |
| Full local server on an allocated port is acceptable | `@LocalGrpcPort` |
| Test should avoid opening a real port | in-process transport |
| Client bean should exist only for tests | test-scoped `@Bean` stub wiring |

## Inject the local port

```java
@TestConfiguration
class GrpcTestConfiguration {
    @Bean
    @Lazy
    GreeterGrpc.GreeterBlockingStub greeterStub(GrpcChannelFactory channels, @LocalGrpcPort int port) {
        return GreeterGrpc.newBlockingStub(channels.createChannel("static://localhost:" + port));
    }
}
```

## In-process transport shape

```java
@SpringBootTest
@AutoConfigureInProcessTransport
class GreetingIntegrationTests {
}
```

```yaml
spring:
  grpc:
    test:
      inprocess:
        enabled: true
```

## Guardrails

- Use in-process transport when the goal is contract and wiring verification rather than socket-level behavior.
- Keep test-only stub beans inside test configuration so production wiring stays simple.
- Verify imported or explicit stubs point at the intended test channel before asserting business behavior.
- Use a connectable client address such as `localhost`, not a wildcard bind address such as `0.0.0.0`.
