# Implementation and output examples

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

    /**
     * Replies to one unary greeting request through the response observer.
     */
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
        /**
         * Reads the correlation-id metadata key so every server call carries request correlation.
         */
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            String correlationId = headers.get(Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER));
            return next.startCall(call, headers);
        }
    };
}
```

### Shared exception mapping

```java
@Bean
GrpcExceptionHandler invalidArgumentHandler() {
    return exception -> exception instanceof IllegalArgumentException
        ? Status.INVALID_ARGUMENT.withDescription(exception.getMessage()).asException()
        : null;
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
      channel:
        greeter:
          target: static://localhost:9090
```

### Deadline shape

```java
greeter.withDeadlineAfter(2, TimeUnit.SECONDS)
```

### Reflection configuration

```yaml
spring:
  grpc:
    server:
      reflection:
        enabled: true
```

Reflection defaults to enabled when the reflection service is present and the application exposes a gRPC service.
Set `spring.grpc.server.reflection.enabled: false` explicitly when production deployments must not expose reflection.

### Server health shape

```yaml
spring:
  grpc:
    server:
      health:
        enabled: true
        service:
          Greeter:
            include: "*"
```

Use `spring.grpc.server.health.service.<name>.include` to list the health indicators each gRPC service should report.
Keep it intentional instead of publishing every service by default.

### Client health-check shape

```yaml
spring:
  grpc:
    client:
      channel:
        greeter:
          target: static://localhost:9090
          health:
            enabled: true
```

Client health checks are optional and should be enabled only when the upstream exposes the health service and the deployment wants the channel to react to serving state.

If the upstream does not publish the gRPC health service, leave client health disabled and rely on explicit deadlines, retries, or transport-level failure handling instead.

### Test port injection shape

```java
@Bean
@Lazy
GreeterGrpc.GreeterBlockingStub greeterStub(GrpcChannelFactory channels, @LocalGrpcServerPort int port) {
    return GreeterGrpc.newBlockingStub(channels.createChannel("static://localhost:" + port));
}
```
