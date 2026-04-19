# Spring AI observability and production debugging

Open this reference when the task involves token accounting, metrics, tracing, or production debugging for a Spring AI application.

Make AI behavior observable before scaling concurrency or changing prompt and retrieval strategy in production.

## When to open this file

Open this reference when the task involves:

- Recording prompt, completion, or total token usage
- Adding latency, error, retrieval, or tool-call metrics
- Setting up tracing for retrieval or tool-call chains
- Debugging unexpected model responses or retrieval drift in production
- Correlating AI call cost and latency to end-user latency

## Minimum observability rule

Log model name, latency, token usage, retrieval count, and tool-call identity without leaking secrets or personal user content.

## Usage handling blocker

**Problem:** the application needs token accounting for cost control, debugging, or SLO review.

**Solution:** read `Usage` from the `ChatResponse` metadata and record prompt, completion, and total tokens as first-class telemetry.

```java
ChatResponse response = chatClient.prompt()
    .user("Summarize the latest deployment changes.")
    .call()
    .chatResponse();

Usage usage = response.getMetadata().getUsage();
Long promptTokens = usage.getPromptTokens();
Long completionTokens = usage.getCompletionTokens();
Long totalTokens = usage.getTotalTokens();
```

Keep token accounting near the application seam so the same numbers are available to logs, metrics, and debugging workflows.

## Logging shape

### ChatClient response logging

```java
@Service
class ReleaseNotesAssistant {
    private final ChatClient chat;
    private final MeterRegistry meter;
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final String modelName = "gpt-4o-mini";

    ReleaseNotesAssistant(ChatClient.Builder builder, MeterRegistry meter) {
        this.chat = builder.defaultSystem("You summarize release notes concisely.").build();
        this.meter = meter;
    }

    String summarize(String notes) {
        long start = System.nanoTime();
        ChatResponse response = chat.prompt()
            .user(user -> user.text("Summarize: {notes}").param("notes", notes))
            .call()
            .chatResponse();
        long elapsed = System.nanoTime() - start;
        Usage usage = response.getMetadata().getUsage();
        meter.timer("ai.call.duration", Tags.of("model", modelName)).record(elapsed, TimeUnit.NANOSECONDS);
        log.atInfo().log(() -> "model=" + modelName + " latencyMs=" + (elapsed / 1_000_000) + " promptTokens=" + usage.getPromptTokens() + " completionTokens=" + usage.getCompletionTokens() + " totalTokens=" + usage.getTotalTokens());
        return response.getResult().getOutput().getText();
    }
}
```

### Tool-call audit logging

```java
@Component
class InventoryTools {
    private final InventoryRepository inventory;
    private final Logger log = LoggerFactory.getLogger(InventoryTools.class);

    InventoryTools(InventoryRepository inventory) {
        this.inventory = inventory;
    }

    @Tool(description = "Find the available inventory quantity for a SKU")
    int quantity(String sku) {
        log.atInfo().log(() -> "tool=quantity sku=" + sku);
        int qty = inventory.availableQuantity(sku);
        log.atInfo().log(() -> "tool=quantity sku=" + sku + " result=" + qty);
        return qty;
    }
}
```

## Metrics to track

- `ai.call.duration` — latency histogram per model or endpoint
- `ai.call.errors` — error count per provider or model
- `ai.call.tokens.prompt` — prompt token count
- `ai.call.tokens.completion` — completion token count
- `ai.call.tokens.total` — total token count
- `ai.retrieval.count` — number of documents returned from vector search
- `ai.retrieval.latency` — latency of the similarity search call
- `ai.tool.calls` — count of tool invocations labeled by tool name

## Tracing Spring AI calls

When Micrometer tracing is active, `ChatClient` calls are automatically traced if the underlying HTTP client is instrumented.

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
```

## Safe production debugging rules

### What never goes into logs

- Raw user prompts containing personal data or credentials
- Full vector-store content or embedding inputs
- Model API keys or authentication tokens
- Session IDs that can be correlated to individual users without consent

### What goes into logs

- Model name and version
- Latency bucketed in milliseconds
- Token counts from `Usage`
- Retrieval count and similarity threshold used
- Tool name, arguments, and return value for non-sensitive tools
- Error type and message without stack traces that expose internal paths

## Debugging unexpected retrieval drift

When the model retrieves documents that appear unrelated to the query:

1. Log the `SearchRequest` query text and `topK` value.
2. Log the returned document IDs and similarity scores when available.
3. Verify the embedding model used at query time matches the one used at ingestion time.
4. Check whether the similarity threshold has been relaxed without a corresponding retrieval test.
5. Confirm the vector index is current and new documents have been ingested.

## Decision points

| Symptom | First check |
| --- | --- |
| Latency spike without error | check `ai.call.duration` histogram and provider-side rate limits |
| Token cost is unclear | inspect `Usage` and record prompt, completion, and total tokens |
| Retrieval drift | verify embedding model alignment and similarity threshold |
| Tool never called | log the model prompt and confirm tool descriptions are present |
| Trace correlation broken | verify Micrometer tracer is wired to the HTTP client |

## Operational checks

- Add metrics for every AI call path before scaling concurrency.
- Verify structured-output deserialization failures are logged as `ai.call.errors`.
- Confirm the embedding model used at ingestion time is the same used at retrieval time.
- Check that tool-call audit logs do not contain sensitive tool arguments such as passwords or PII.
- Verify tracing spans propagate across `ChatClient` calls, retrieval, and tool execution.
