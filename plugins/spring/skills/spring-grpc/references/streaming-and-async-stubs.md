# Spring gRPC streaming and future stubs

Open this reference when the ordinary blocking unary path is not enough and the task needs future-style stubs or streaming RPC patterns.

## Future-style client

Use future stubs when the caller must overlap multiple unary remote calls without blocking the current thread.
Generated gRPC Java future stubs return `ListenableFuture`.

```java
@Service
class AsyncGreetingClient {
    private final GreeterGrpc.GreeterFutureStub greeter;
    AsyncGreetingClient(GreeterGrpc.GreeterFutureStub greeter) {
        this.greeter = greeter;
    }
    ListenableFuture<HelloReply> greet(String name) {
        return greeter
            .withDeadlineAfter(2, TimeUnit.SECONDS)
            .sayHello(HelloRequest.newBuilder().setName(name).build());
    }
}
```

Use one stub style per call boundary unless there is a strong reason to mix blocking and future-based APIs.

Server-streaming shape:

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
