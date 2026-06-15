# Spring Integration CloudEvents and gRPC support

Open this reference when the flow uses CloudEvents specification interoperability or gRPC protocol gateways.

## CloudEvents dependency

```xml
<dependency>
    <groupId>org.springframework.integration</groupId>
    <artifactId>spring-integration-cloudevents</artifactId>
</dependency>
```

## ToCloudEventTransformer shape

Converts Spring Integration `Message<byte[]>` into a CloudEvents-compliant message.
Supports CloudEvents specification v1.0.

```java
import org.springframework.integration.cloudevents.out.ToCloudEventTransformer;
import org.springframework.integration.cloudevents.dsl.CloudEvents;

@Bean
IntegrationFlow cloudEventTransformFlow() {
    return IntegrationFlows
        .from("inputChannel")
        .transform(CloudEvents.toCloudEventTransformer().get())
        .channel("outputChannel")
        .get();
}
```

### Extension patterns

Use `extensionPatterns` (vararg of strings) to specify which message headers become CloudEvent extensions.
Supports wildcard patterns (`*`) and negation (`!` prefix).
First matching pattern wins.

```java
new ToCloudEventTransformer("trace*", "correlation-id", "!internal-*")
```

### EventFormat serialization

When an `EventFormat` is specified, the transformer serializes the CloudEvent into the payload.
Without an `EventFormat`, attributes and extensions are added to message headers with a `ce-` prefix (binary format mode).

## FromCloudEventTransformer shape

Converts CloudEvents (both `CloudEvent` objects and serialized byte arrays) into Spring Integration messages.

```java
import org.springframework.integration.cloudevents.in.FromCloudEventTransformer;
import org.springframework.integration.cloudevents.dsl.CloudEvents;

@Bean
IntegrationFlow fromCloudEventFlow() {
    return IntegrationFlows
        .from("cloudEventInputChannel")
        .transform(CloudEvents.fromCloudEventTransformer())
        .channel("messageOutputChannel")
        .get();
}
```

CloudEvent attributes and extensions are mapped to message headers with a `ce-` prefix.
The `contentType` header is set to the CloudEvent's `datacontenttype` value.

## CloudEventHeadersBuilder shape

Use `CloudEvents.headers()` to enrich messages with CloudEvent attributes before transformation.

```java
@Bean
IntegrationFlow cloudEventFlow() {
    return IntegrationFlow
        .from("inputChannel")
        .enrichHeaders(CloudEvents.headers()
            .idExpression("headers.orderId")
            .sourceFunction(msg -> URI.create("https://example.com/" + msg.getHeaders().get("action")))
            .type("order.created")
            .subject("order-processing"))
        .transform(CloudEvents.toCloudEventTransformer())
        .channel("outputChannel")
        .get();
}
```

## CloudEvents decision points

- Use `ToCloudEventTransformer` when publishing to systems that consume CloudEvents (event brokers, observability pipelines).
- Use `FromCloudEventTransformer` when consuming CloudEvents from external sources.
- Ensure message payloads are `byte[]` before transformation.
  - Other types throw `IllegalArgumentException`.
- Add the appropriate `EventFormat` dependency (e.g., `io.cloudevents:cloudevents-json`) when structured serialization is required.

## gRPC dependency

```xml
<dependency>
    <groupId>org.springframework.integration</groupId>
    <artifactId>spring-integration-grpc</artifactId>
</dependency>
```

## GrpcInboundGateway shape

Receives gRPC requests and produces responses via the downstream flow.
Requires a `*ImplBase` generated service class.

```java
import org.springframework.integration.grpc.inbound.GrpcInboundGateway;
import org.springframework.integration.grpc.dsl.Grpc;

@Bean
GrpcInboundGateway helloWorldService() {
    return new GrpcInboundGateway(TestHelloWorldGrpc.TestHelloWorldImplBase.class);
}

@Bean
IntegrationFlow grpcInboundFlow() {
    return IntegrationFlow.from(
                    Grpc.inboundGateway(TestSingleHelloWorldGrpc.TestSingleHelloWorldImplBase.class)
                        .requestTimeout(3000L))
            .transform(this::requestReply)
            .get();
}
```

The inbound gateway implements `BindableService` and must be declared as a top-level bean for Spring gRPC auto-discovery.
Do not wrap it inside `IntegrationFlow.from(new GrpcInboundGateway(...))` DSL.

### Server-side method routing

Route on `GrpcHeaders.SERVICE_METHOD` to dispatch to different handlers per gRPC method.

```java
.route(Message.class, message ->
        message.getHeaders().get(GrpcHeaders.SERVICE_METHOD, String.class),
    router -> router
        .subFlowMapping("SayHello", flow -> flow.transform(this::requestReply))
        .subFlowMapping("StreamSayHello", flow -> flow.transform(this::streamReply)))
```

`CLIENT_STREAMING` delivers a `Flux<RequestType>` as payload.
`SERVER_STREAMING` can return a `Flux<ReplyType>`.

## GrpcOutboundGateway shape

Sends gRPC requests to a remote server and receives responses.
Requires a gRPC `Channel` and service class.

```java
import org.springframework.integration.grpc.outbound.GrpcOutboundGateway;
import org.springframework.integration.grpc.dsl.Grpc;

@Bean
IntegrationFlow grpcOutboundFlow(ManagedChannel channel) {
    return f -> f
        .handle(Grpc.outboundGateway(channel, TestSingleHelloWorldGrpc.class))
        .transform(this::processReply);
}
```

The gateway is asynchronous by default.
Set `async(false)` for synchronous behavior.

### Method name configuration

When the service has one method, it is auto-detected.
For multiple methods, use one of:

```java
Grpc.outboundGateway(channel, TestHelloWorldGrpc.class)
    .methodName("SayHello")
```

```java
Grpc.outboundGateway(channel, TestHelloWorldGrpc.class)
    .methodNameExpression(new SpelExpressionParser().parseExpression("payload.class.simpleName"))
```

When neither is configured, the gateway reads the `GrpcHeaders.SERVICE_METHOD` header from the input message.

## gRPC decision points

- Use gRPC gateways for type-safe, high-performance inter-service communication where Protobuf contracts exist.
- Declare `GrpcInboundGateway` as a top-level bean, not wrapped in DSL flows.
- Use reactive types (`Flux`, `Mono`) for streaming patterns.
  - Plain objects for unary.
- The gateway does not generate Protobuf types.
  - Payloads are gRPC messages as-is.
