# Spring Boot gRPC support

Open this reference when the task is about gRPC server, client, security, or testing setup.

## Add starters

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-grpc-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-grpc-client</artifactId>
</dependency>
```

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-grpc-server")
    implementation("org.springframework.boot:spring-boot-starter-grpc-client")
}
```

For testing:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-grpc-test</artifactId>
    <scope>test</scope>
</dependency>
```

```kotlin
dependencies {
    testImplementation("org.springframework.boot:spring-boot-grpc-test")
}
```

## Server

Implement a generated `BindableService` from your `.proto` file and expose it as a Spring bean.
Spring gRPC automatically discovers and exposes any `BindableService` bean.

```java
@GrpcService
class MyServiceImpl extends MyServiceGrpc.MyServiceImplBase {
    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        responseObserver.onNext(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
    }
}
```

## Server SSL

```yaml
spring:
  grpc:
    server:
      ssl:
        enabled: true
        bundle: example
```

For client authentication:

```yaml
spring:
  grpc:
    server:
      ssl:
        enabled: true
        bundle: example
        client-auth: require
```

## Server security

Boot auto-configures `GrpcSecurity` and `SecurityGrpcExceptionHandler` beans.
Secure gRPC services using `@PreAuthorize` annotations.

```java
@GrpcService
class MyServiceImpl extends MyServiceGrpc.MyServiceImplBase {
    @PreAuthorize("hasRole('ADMIN')")
    @Override
    public void adminMethod(Request request, StreamObserver<Response> responseObserver) {
        // ...
    }
}
```

## @GrpcAdvice exception handling

```java
@GrpcAdvice
class GrpcExceptionHandling {

    @GrpcExceptionHandler
    public Status handleNotFound(NotFoundException ex) {
        return Status.NOT_FOUND.withDescription(ex.getMessage());
    }
}
```

## Client

Register generated stubs with `@ImportGrpcClients` when the application can use Spring-managed stub beans.

```java
@Configuration
@ImportGrpcClients(types = MyServiceGrpc.MyServiceBlockingStub.class)
class GrpcClientConfig {
}
```

```java
@Service
class MyServiceClient {
    private final MyServiceGrpc.MyServiceBlockingStub myService;
    MyServiceClient(MyServiceGrpc.MyServiceBlockingStub myService) {
        this.myService = myService;
    }
}
```

```yaml
spring:
  grpc:
    client:
      channel:
        myservice:
          target: static://localhost:9090
```

## Client SSL

```yaml
spring:
  grpc:
    client:
      channel:
        myservice:
          target: static://localhost:9090
          ssl:
            enabled: true
```

For mutual TLS:

```yaml
spring:
  grpc:
    client:
      channel:
        myservice:
          target: static://localhost:9090
          ssl:
            bundle: example
```

## Testing

`@AutoConfigureTestGrpcTransport` replaces gRPC communication channels with in-process channels for testing.

```java
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = MyServiceGrpc.MyServiceBlockingStub.class)
class MyGrpcTests {
    private final MyServiceGrpc.MyServiceBlockingStub myService;
    MyGrpcTests(MyServiceGrpc.MyServiceBlockingStub myService) {
        this.myService = myService;
    }
    @Test
    void testHello() {
        var reply = myService.sayHello(HelloRequest.newBuilder().setName("Test").build());
        assertThat(reply.getMessage()).isEqualTo("Hello Test");
    }
}
```

## Reflection service

Spring Boot gRPC server support can expose gRPC reflection when `io.grpc:grpc-services` is available through the server starter and `spring.grpc.server.reflection.enabled` is enabled.

## Gotchas

- For Servlet-based gRPC, ensure `server.http2.enabled` is set to `true`.
- Prefer Spring-managed generated stubs with `@ImportGrpcClients`.
  - Use `GrpcChannelFactory` only when channel construction must be customized directly.
