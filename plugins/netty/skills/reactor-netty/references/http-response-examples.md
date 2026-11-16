# HTTP Response and Error Examples

## Response consumption shapes

| Method | Use when | Return shape |
| --- | --- | --- |
| `.responseSingle((resp, content) -> ...)` | you need the status code + full body aggregated at once | `Mono<T>` - body is fully buffered |
| `.responseContent()` | you want streaming/chunked processing of the response body | `Flux<ByteBuf>` - each chunk arrives as it is received |

Aggregated body (status check + full body in one signal):

```java
String body = HttpClient.create()
    .get().uri("http://localhost:8080/hello")
    .responseSingle((resp, content) -> {
        if (resp.status().code() != 200) {
            return Mono.error(new RuntimeException("unexpected: " + resp.status()));
        }
        return content.asString();
    })
    .block();
```

Streaming body (process chunks as they arrive):

```java
List<String> chunks = HttpClient.create()
    .get().uri("http://localhost:8080/stream")
    .responseContent()
    .asString()
    .collectList()
    .block();
```

## Error handling in reactive handlers

Use `onErrorResume` when a handler must convert a reactive failure into an HTTP response, and let unexpected errors propagate when the caller should observe the failure:

Route-level fallback (compose the `500` response inside the handler):

```java
.route(routes -> routes
    .get("/hello", (req, res) -> res.sendString(Mono.just("Hello")))
    .get("/fail", (req, res) -> Mono.error(new RuntimeException("boom"))
        .onErrorResume(error -> res.status(500)
            .sendString(Mono.just("Error: " + req.uri()))
            .then())))
```

Handler-level recovery (map errors to fallback values):

```java
HttpClient.create()
    .get().uri("http://localhost:8080/hello")
    .responseSingle((resp, content) -> content.asString())
    .onErrorResume(TimeoutException.class,
        timeout -> Mono.just("fallback (timeout)"))
    .block();
```

Do not use blocking `try/catch` inside reactive lambdas.
Reactive errors travel through the error channel, not Java exceptions.
