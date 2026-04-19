---
name: "spring-grpc"
description: "Use this skill when implementing protobuf-first gRPC servers or clients in a Spring application, generating stubs, configuring channels, applying interceptors, and controlling deadlines, metadata, and reflection." 
metadata:
  title: "Spring gRPC"
  official_project_url: "https://spring.io/projects/spring-grpc"
  reference_doc_urls:
    - "https://docs.spring.io/spring-grpc/reference/"
  compatibility_note: "Reference documentation is current 1.1.0-M1; validate Boot and starter compatibility against the version already chosen in the project."
  version: "1.1.0-M1"
---

Use this skill when implementing protobuf-first gRPC servers or clients in a Spring application, generating stubs, configuring channels, applying interceptors, and controlling deadlines, metadata, and reflection.

## Boundaries

Use `spring-grpc` for gRPC transport, generated protobuf stubs, Spring-managed gRPC services, channel configuration, request metadata, and gRPC-specific error handling.

- Use narrower guidance for HTTP, reactive HTTP, or GraphQL API design.
- Keep business logic outside the gRPC transport class. Service implementations should translate between protobuf contracts and application services.

## Common path

The ordinary Spring gRPC job is:

1. Define the `.proto` contract first and generate Java stubs before writing Spring code.
2. Add only the server or client starter needed by the application, plus reflection support only when operators or local tools actually need it.
3. Register client stubs with `@ImportGrpcClients` for the ordinary path, and fall back to explicit `@Bean` stub creation only when the channel or stub needs custom construction.
4. Implement a Spring-managed gRPC service that maps protobuf messages to application inputs and outputs.
5. Configure deadlines, metadata, and interceptors at the client or server boundary.
6. Add an integration test that proves the generated contract, server binding, and client call all agree.

## Starter and runtime decisions

| Situation | Use |
| --- | --- |
| Application only serves gRPC | server starter |
| Application only calls another gRPC service | client starter |
| Same application serves and calls gRPC | both starters |
| Operators or local tooling need descriptor discovery | add reflection support |

Keep reflection and optional support services out of the default path unless a concrete runtime need exists.

## Dependency baseline

Use only the starter set the application actually needs.

### Server-only baseline

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.grpc</groupId>
        <artifactId>spring-grpc-server-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

### Client-only baseline

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.grpc</groupId>
        <artifactId>spring-grpc-client-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

### Combined application baseline

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.grpc</groupId>
        <artifactId>spring-grpc-server-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.grpc</groupId>
        <artifactId>spring-grpc-client-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

### Optional support-services add-on

```xml
<dependencies>
    <dependency>
        <groupId>io.grpc</groupId>
        <artifactId>grpc-services</artifactId>
    </dependency>
</dependencies>
```

Add `grpc-services` only when reflection, health, or other optional gRPC support services are needed.

## First safe configuration

### Server properties

```yaml
spring:
  grpc:
    server:
      port: 9090
```

### Client properties

```yaml
spring:
  grpc:
    client:
      channels:
        greeter:
          address: static://localhost:9090
```

Start with explicit static addresses in local development. Add service discovery or advanced channel customization only when the deployment actually needs it.

## Client wiring choices

| Situation | Use |
| --- | --- |
| Standard blocking stubs from generated types | `@ImportGrpcClients` |
| Need to target one generated stub explicitly | `@ImportGrpcClients(types = GreeterGrpc.GreeterBlockingStub.class)` |
| Need custom bean naming or multiple variants | `@ImportGrpcClients(prefix = "secure", ...)` or explicit `@Bean` creation |
| Need custom channel construction or per-stub tuning | explicit `@Bean` with `GrpcChannelFactory` |

The default client path is importing generated blocking stubs into the Spring context. Use manual stub beans only when the client setup needs more control than the import path provides.

## Coding procedure

1. Write the `.proto` file first and keep field numbers stable once the contract is published.
2. Generate stubs before implementing handlers so the server and client compile against the same contract.
3. Register shared transport concerns with `@GlobalServerInterceptor` and `@GlobalClientInterceptor`, then add per-service interceptors with `@GrpcService(interceptors = ..., blendWithGlobalInterceptors = true)` only when one service needs extra behavior.
4. Keep `@GrpcService` classes thin and delegate to application services for real work.
5. Translate validation failures and business failures into explicit gRPC statuses instead of leaking generic runtime exceptions.
6. Use `GrpcExceptionHandler` or `@GrpcExceptionHandler` when several handlers need the same status-mapping rule.
7. Attach metadata, deadlines, and interceptors at the client or server boundary, not inside core business services.
8. Enable reflection only when local debugging or tooling actually needs it.

## RPC style decisions

| Situation | Use |
| --- | --- |
| Simple request-response boundary | unary RPC with a blocking stub |
| One request returns many messages | server streaming |
| Client uploads many items before one response | client streaming |
| Both sides need a long-lived conversation | bidirectional streaming |
| Caller must overlap many remote calls | future or async stub |

Unary request-response is the ordinary path. Open the streaming reference only when the contract or caller model genuinely needs a non-unary RPC style.

## Error and boundary decisions

| Situation | Guidance |
| --- | --- |
| Invalid client input | map to `Status.INVALID_ARGUMENT` |
| Missing or denied access | map to the matching gRPC status instead of generic runtime exceptions |
| Cross-service call | set an explicit deadline |
| Correlation or tracing data | attach metadata at the transport boundary |
| Same exception rule across multiple handlers | centralize it in `GrpcExceptionHandler` or `@GrpcExceptionHandler` |
| One service needs extra transport policy | use `@GrpcService(interceptors = ...)` instead of copying logic into business code |

## Health and observability decisions

| Situation | Use |
| --- | --- |
| Server should report serving state | server health support |
| Actuator health should include selected gRPC services | `spring.grpc.server.health.actuator.health-indicator-paths` |
| Client should stop calling an unhealthy upstream | per-channel client health checks |
| Actuator is already on the classpath | use the autoconfigured observability interceptor |

Server health and client health are separate concerns. Server health publishes service state, while client health decides whether a channel should keep using an upstream endpoint.

## Implementation examples

### Protobuf contract

```proto
syntax = "proto3";

option java_multiple_files = true;
option java_package = "com.example.grpc";

service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply);
}

message HelloRequest {
  string name = 1;
}

message HelloReply {
  string message = 1;
}
```

### Server implementation

```java
@GrpcService(interceptors = LoggingInterceptor.class)
class GreeterService extends GreeterGrpc.GreeterImplBase {
    private final GreetingApplicationService greetingService;

    GreeterService(GreetingApplicationService greetingService) {
        this.greetingService = greetingService;
    }

    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        HelloReply reply = HelloReply.newBuilder()
            .setMessage(greetingService.greet(request.getName()))
            .build();

        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}
```

### Imported blocking stub

```java
@SpringBootApplication
@ImportGrpcClients(types = GreeterGrpc.GreeterBlockingStub.class)
class GrpcApplication {
}
```

### Client stub with deadline

```java
@Configuration
class GrpcClientConfig {
    @Bean
    GreeterGrpc.GreeterBlockingStub greeterStub(GrpcChannelFactory channels) {
        return GreeterGrpc.newBlockingStub(channels.createChannel("greeter"));
    }
}

@Service
class GreetingClient {
    private final GreeterGrpc.GreeterBlockingStub greeter;

    GreetingClient(GreeterGrpc.GreeterBlockingStub greeter) {
        this.greeter = greeter;
    }

    String greet(String name) {
        HelloReply reply = greeter
            .withDeadlineAfter(2, TimeUnit.SECONDS)
            .sayHello(HelloRequest.newBuilder().setName(name).build());

        return reply.getMessage();
    }
}
```

### Global interceptor for request metadata

```java
@Bean
@GlobalServerInterceptor
ServerInterceptor correlationInterceptor() {
    return new ServerInterceptor() {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call,
                Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {
            String correlationId = headers.get(Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER));
            return next.startCall(call, headers);
        }
    };
}
```

### Shared exception mapping

```java
@Bean
GrpcExceptionHandler<IllegalArgumentException, HelloRequest> invalidArgumentHandler() {
    return (exception, request) -> Status.INVALID_ARGUMENT
        .withDescription(exception.getMessage());
}
```

### Status mapping for validation failures

```java
throw Status.INVALID_ARGUMENT
    .withDescription("name must not be blank")
    .asRuntimeException();
```

## Output and configuration shapes

### Client channel shape

```yaml
spring:
  grpc:
    client:
      channels:
        greeter:
          address: static://localhost:9090
```

### Deadline shape

```java
greeter.withDeadlineAfter(2, TimeUnit.SECONDS)
```

### Reflection-enabled local server shape

```yaml
spring:
  grpc:
    server:
      reflection:
        enabled: true
```

Keep this as an opt-in local tooling shape rather than the ordinary default.

### Server health shape

```yaml
spring:
  grpc:
    server:
      health:
        enabled: true
        actuator:
          health-indicator-paths: Greeter
```

### Client health-check shape

```yaml
spring:
  grpc:
    client:
      channels:
        greeter:
          address: static://localhost:9090
          health:
            enabled: true
```

Client health checks are optional and should be enabled only when the upstream exposes the health service and the deployment wants the channel to react to serving state.

### Test port injection shape

```java
@Bean
@Lazy
GreeterGrpc.GreeterBlockingStub greeterStub(GrpcChannelFactory channels, @LocalGrpcPort int port) {
    return GreeterGrpc.newBlockingStub(channels.createChannel("static://localhost:" + port));
}
```

## Testing checklist

- Verify generated stubs and the checked-in `.proto` contract stay aligned.
- Verify the server binds the expected service and returns the protobuf response shape the client expects.
- Verify deadlines, metadata propagation, and interceptor behavior on at least one representative call.
- Verify invalid input maps to the intended gRPC status instead of an internal error.
- Verify imported stubs or manually registered stubs point to the intended channel in tests.
- Verify in-process transport or injected local ports when the test should avoid a real network port.
- Verify reflection is disabled in environments where exposing service descriptors is not acceptable.

## Production checklist

- Keep published field numbers stable and never repurpose them after clients exist.
- Bound every cross-service call with an explicit deadline.
- Propagate correlation ids or tracing metadata consistently across client and server boundaries.
- Prefer the autoconfigured observability interceptor when Actuator is already in use instead of hand-rolling duplicate metrics logic.
- Distinguish server health publication from client health gating and enable each intentionally.
- Expose reflection only when operations tooling requires it.
- Keep transport errors and application errors distinct so retries and observability stay meaningful.

## References

- Open [references/streaming-and-async-stubs.md](references/streaming-and-async-stubs.md) when the ordinary blocking unary path is not enough and the task needs future-style stubs or streaming RPC patterns.
- Open [references/channel-customization.md](references/channel-customization.md) when the deployment needs richer client-channel construction, global client interceptors, compression, keepalive, retries, or per-channel tuning.
- Open [references/exception-handling.md](references/exception-handling.md) when exception-to-status mapping needs reusable handler beans, `@GrpcExceptionHandler`, or different behavior per service.
- Open [references/in-process-testing.md](references/in-process-testing.md) when integration tests should use in-process transport, `@LocalGrpcPort`, or explicit test-only channel wiring.
- Open [references/security-tls-mtls.md](references/security-tls-mtls.md) when the deployment needs TLS or mTLS, Basic authentication, bearer tokens, or OAuth2 and server-authentication integration.
