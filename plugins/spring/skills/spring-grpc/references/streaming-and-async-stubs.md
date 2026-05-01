# Spring gRPC streaming and future stubs

Open this reference when the ordinary blocking unary path in `SKILL.md` is not enough and the task needs future-style stubs or streaming RPC patterns.

## Future-style client

Use future stubs when the caller must overlap multiple remote calls without blocking the current thread. On the stable spring-grpc 1.0.3 line, async stubs return `ListenableFuture`. The 1.1.0-M1 milestone tracks possible reactive return types; pin to 1.0.3 for production until 1.1 stabilizes.

```java
@Service
class AsyncGreetingClient {
    private final GreeterGrpc.GreeterFutureStub greeter;

    AsyncGreetingClient(GreeterGrpc.GreeterFutureStub greeter) {
        this.greeter = greeter;
    }

    ListenableFuture<HelloReply> greet(String name) {
        return greeter.sayHello(HelloRequest.newBuilder().setName(name).build());
    }
}
```

Use one stub style per call boundary unless there is a strong reason to mix blocking and future-based APIs.

## Streaming choices

- Unary RPC: default for request-response calls.
- Server streaming: use when one request should produce a sequence of responses.
- Client streaming: use when the client uploads many items before one final response.
- Bidirectional streaming: use only when both sides genuinely need a long-lived conversation.

## Server-streaming shape

```proto
service Greeter {
  rpc StreamHellos (HelloRequest) returns (stream HelloReply);
}
```

```java
@Override
public void streamHellos(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
    responseObserver.onNext(HelloReply.newBuilder().setMessage("hello " + request.getName()).build());
    responseObserver.onCompleted();
}
```

## Decision points

| Situation | Use |
| --- | --- |
| Simple request-response boundary | blocking unary stub |
| Overlap many remote calls | future stub |
| One request returns many messages | server streaming |
| Both sides exchange a long-lived stream | bidirectional streaming |
