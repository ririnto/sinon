# Spring AI upgrade notes and migration branches

Open this reference when the task involves upgrading Spring AI versions, migrating between provider families, or changing prompt, tool, or retrieval strategy across release boundaries.

Treat prompts, model names, tool descriptions, vector-store configurations, and response shapes as part of the compatibility surface that needs explicit migration review.

## When to open this file

Open this reference when the task involves:

- Upgrading the Spring AI BOM version
- Migrating from one model provider to another
- Changing structured output types or prompt templates
- Changing tool APIs or registration style across versions
- Moving between vector-store implementations
- Updating retrieval strategy or similarity thresholds in production

## Version upgrade procedure

### BOM version change

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bom</artifactId>
    <version>2.0.0</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

Replace `2.0.0` with the target Spring AI version during the upgrade and review starter availability before merging.

## Upgrading to 2.0.0

Spring AI 2.0.x supports Spring Boot 4.0.x and 4.1.x.

### Removed modules and starters

| Removed | Replacement |
| --- | --- |
| `spring-ai-starter-model-openai-sdk` | `spring-ai-starter-model-openai` (merged; official openai-java SDK is now the default) |
| `spring-ai-starter-model-azure-openai` | `spring-ai-starter-model-openai` (Azure OpenAI support folded into standard OpenAI module) |
| `spring-ai-advisors-vector-store` | `spring-ai-vector-store-advisor` |
| `spring-ai-hanadb-store` | Removed; no replacement |

### PromptChatMemoryAdvisor removed

`PromptChatMemoryAdvisor` has been removed entirely.
Use `MessageChatMemoryAdvisor`:

```java
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
    .build();

chatClient.prompt()
    .user("Hello!")
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "my-session"))
    .call()
    .content();
```

### Conversation ID now required

`ChatMemory.DEFAULT_CONVERSATION_ID` has been removed.
Every call through a memory advisor must supply `ChatMemory.CONVERSATION_ID` via the advisor context.
The `.conversationId()` builder method on memory advisors has been removed.

### Options immutability

All mutable setter methods have been removed from `ChatOptions` classes.
Use the builder pattern exclusively:

```java
OpenAiChatOptions options = OpenAiChatOptions.builder()
    .model("gpt-4.1")
    .temperature(0.7)
    .build();
```

### Configuration properties flattened

Properties no longer use `.options` in the path:

```properties
# Before
spring.ai.openai.chat.options.model=gpt-4.1

# After
spring.ai.openai.chat.model=gpt-4.1
```

### ToolCallingAdvisor auto-registration

`ChatClient` now automatically registers a `ToolCallingAdvisor` when tools are configured.
The `internalToolExecutionEnabled` option has been removed.
Control tool calling globally:

```properties
spring.ai.chat.client.tool-calling.enabled=false
```

### ToolCallingAdvisor manages conversation history

`ToolCallingAdvisor` manages conversation history internally across tool-call iterations by default.
Memory advisors only store the final user/assistant exchange.
Set `Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER` explicitly if memory is needed inside the tool-call loop.

### JDBC Chat Memory schema change

`JdbcChatMemoryRepository` adds a `sequence_id BIGINT` column for deterministic message ordering.
Existing tables require a schema migration to add this column.

### MCP support updated for Spring AI 2.0

Spring AI 2.0 requires MCP Java SDK 1.0.0 or later in the 1.0.x line.
SSE MCP server transports are deprecated in favor of Streamable HTTP.
Server-side tool input validation is enabled by default.
`Tool.inputSchema` changed from `JsonSchema` to `Map`.

### Anthropic migrated to official SDK

`spring-ai-anthropic` now uses `com.anthropic:anthropic-java`.
`AnthropicApi` and its nested types are removed.
Use `AnthropicChatModel.builder()`.
`AnthropicChatOptions.maxTokens` default changed from 500 to 4096.

### Observability changes

Tool calling span renamed from `tool_call` to `execute_tool`.
New attributes: `spring.ai.tool.type` and `spring.ai.tool.call.id`.

### Community or bridge-maintained providers

MiniMax dedicated support has been removed.
Use Anthropic support with the MiniMax base URL.
OCI GenAI is maintained outside the Spring AI project in community documentation.
ZhipuAI is not documented in the Spring AI 2.0 reference.
Verify community coordinates before recommending it.

### Provider starter compatibility check

Not every provider starter is available in every Spring AI release.
Check the reference documentation for the target version before changing the BOM.

```sh
mvn dependency:list | grep spring-ai-starter-model
```

### Tool API migration check

Tool registration APIs can change across release lines.
If the target version changes tool registration semantics, update the application seam and tests in the same branch.

```java
ChatClient chatClient = ChatClient.create(chatModel);
```

Keep the tool registration style aligned with the target Spring AI version instead of mixing examples from different releases.

### Structured output type migration

When upgrading, verify that the POJO record or class used for structured output still maps correctly.

```java
record ReleaseSummary(String version, List<String> breakingChanges, List<String> actions) {}
```

Check that field names, types, and nested records still produce the expected schema used by the model.

### Retrieval configuration migration

When defaults change, set retrieval controls explicitly so behavior does not drift across versions.

```java
SearchRequest.builder().query(q).topK(5).similarityThreshold(0.72).build();
```

## Provider migration rules

### Chat memory advisor migration

For Spring AI 2.0.0, follow the `PromptChatMemoryAdvisor` removal and explicit `CONVERSATION_ID` guidance in the breaking-change section above.

When moving from one provider family to another:

1. Replace the model starter dependency.
2. Update configuration properties or deployment names at the provider edge.
3. Review tool descriptions and structured-output prompts.
4. Re-run evaluation harnesses against the new provider.
5. Re-check image, audio, or moderation surfaces separately if the application uses them.

## Embedding model migration

When the embedding model changes, re-ingest all documents.
Vector representations are not interchangeable across embedding providers.

```java
@Service
class KnowledgeBase {
    private final VectorStore vectorStore;

    KnowledgeBase(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    void reingestAll(List<String> staleIds, List<Document> documents) {
        vectorStore.delete(staleIds);
        vectorStore.add(documents);
    }
}
```

## Migration branch checklist

- [ ] BOM version updated and dependencies resolved without conflicts
- [ ] Structured output types verified against the new model response
- [ ] Tool registration style reviewed for the target version
- [ ] Tool descriptions reviewed for provider-specific formatting changes
- [ ] Embedding model changed and all documents re-ingested if the embedding model changed
- [ ] Retrieval tests updated with explicit `topK` and similarity-threshold values
- [ ] Evaluation harness run against the new provider or model
- [ ] Logs reviewed to confirm no new sensitive data is emitted
- [ ] Metrics and tracing still emit after the migration

## Decision points

| Situation | First check |
| --- | --- |
| Retrieval count changed after upgrade | verify `topK` and similarity threshold |
| Structured output deserialization fails | check field name changes or schema drift |
| Tool registration stops working after upgrade | check target-version tool API differences |
| Model not available in target version | check provider starter availability in the BOM |
| Embeddings changed | re-ingest all documents under the new embedding model |

## Operational checks

- Pin the Spring AI BOM version in every environment so migration is explicit.
- Treat the model name as a configuration property that must be reviewed at upgrade time.
- Keep a snapshot of old evaluation-harness results before upgrading.
- Log the model version used at both ingestion and retrieval time so drift is visible.
- After upgrade, run the evaluation harness in the same branch before merging.
