# Setup, prompts, tools, and memory

## Dependency baseline

Spring AI 2.0.x supports Spring Boot 4.0.x and 4.1.x.

Import the Spring AI BOM and add only the starters needed for the current model and optional retrieval path.
The `2.0.1` BOM below illustrates the documented Spring AI baseline.
For a new BOM, check `org.springframework.ai:spring-ai-bom` on Maven Central for the latest stable release compatible with the project's Boot line.
Keep an existing BOM or version-catalog pin unless the task authorizes changing it.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>2.0.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
</dependencies>
```

Add retrieval, image, audio, moderation, or MCP starters only when that surface is part of the current job.
Open [references/upgrade-notes-and-migration-branches.md](upgrade-notes-and-migration-branches.md) when changing the project's Spring AI version.

## First safe setup

### Minimal provider properties

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        model: ${OPENAI_CHAT_MODEL}
```

Set `OPENAI_CHAT_MODEL` to a model supported by the target account and Spring AI version.
Start with one provider, one model, and one environment-backed secret.
Open [references/provider-selection-and-model-capability-fit.md](provider-selection-and-model-capability-fit.md) when model family, context window, latency, cost, or provider fit is still unclear.

### Provider-neutral `ChatClient` seam

```java
@Configuration
class AssistantAiConfiguration {
    @Bean
    ChatClient releaseChatClient(ChatClient.Builder builder) {
        return builder.defaultSystem("You summarize release changes for backend engineers.").build();
    }
}
```

Keep the application seam on `ChatClient` or another Spring AI abstraction.
Do not let controllers or domain code depend directly on a provider SDK.

## Prompt templating and structured output

Use prompt templates when reusable instructions or parameterized input are needed.
Keep variables explicit and keep prompt text reviewable in code.

```java
record ReleaseSummary(String version, List<String> breakingChanges, List<String> actions) {}

@Service
class ReleaseSummaryService {
    private final ChatClient chatClient;

    ReleaseSummaryService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    ReleaseSummary summarize(String releaseNotes) {
        return chatClient.prompt()
            .user(user -> user.text("Summarize the release notes and list required migration actions. Notes: {notes}").param("notes", releaseNotes))
            .call()
            .entity(ReleaseSummary.class);
    }
}
```

- Keep prompt variables named and explicit.
- Put reusable system instructions on the `ChatClient` builder or a dedicated service seam.
- Start with Spring AI mapping such as `.entity(...)` so the application stays on Spring AI's portable output contract instead of binding ordinary flows to one provider's JSON mode.
- Keep provider-native JSON modes as an optimization, not the default path, and open [references/upgrade-notes-and-migration-branches.md](upgrade-notes-and-migration-branches.md) when version or provider changes threaten the output contract.

## Tool boundary

Add tools only when the model needs a bounded application capability.

```java
@Component
class InventoryTools {
    private final InventoryRepository inventoryRepository;

    InventoryTools(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Tool(description = "Look up available inventory for a SKU")
    InventorySnapshot inventoryForSku(String sku) {
        return inventoryRepository.findSnapshotBySku(sku);
    }
}

@Service
class ShippingAssistantService {
    private final ChatClient chatClient;
    private final InventoryTools inventoryTools;

    ShippingAssistantService(ChatClient chatClient, InventoryTools inventoryTools) {
        this.chatClient = chatClient;
        this.inventoryTools = inventoryTools;
    }

    String answer(String question) {
        return chatClient.prompt()
            .user(question)
            .tools(inventoryTools)
            .call()
            .content();
    }
}

record InventorySnapshot(String sku, int availableQuantity) {}
```

- Start with read-only or otherwise side-effect-safe tools.
- Treat tool selection as an application contract.
- `ToolCallingAdvisor` is auto-registered when tools are configured (2.0.0+).
  - Disable globally with `spring.ai.chat.client.tool-calling.enabled=false` or per-call with `AdvisorParams.toolCallingAdvisorAutoRegister(false)`.
- Open [references/advanced-tool-orchestration.md](advanced-tool-orchestration.md) when one tool call must explicitly feed the next.
- Open [references/tool-set-curation.md](tool-set-curation.md) when the blocker is exposing only a curated tool set.
- Open [references/tool-failure-and-fallback.md](tool-failure-and-fallback.md) when the blocker is explicit fallback behavior after tool failure.
- Open [references/mcp-client-server-boundaries.md](mcp-client-server-boundaries.md) when the tool boundary may need MCP instead of an in-process Spring bean.

## Memory and retrieval escalation

Use advisors when the request or response must be decorated around the model call.
Use `ChatMemory` through an advisor instead of manually appending prior turns.

Spring AI ships built-in advisors: `MessageChatMemoryAdvisor` (message history), `VectorStoreChatMemoryAdvisor` (vector store-backed memory retrieval), `QuestionAnswerAdvisor` (naive RAG), `RetrievalAugmentationAdvisor` (modular RAG with query transformation, document post-processing, and context augmentation), `ReReadingAdvisor` (re-reading reasoning improvement), and `SafeGuardAdvisor` (content safety gate).
Use in-memory `MessageWindowChatMemory` for demos and tests.
Use repository-backed memory for production multi-session workloads.

```java
@Bean
ChatClient supportChatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
    return builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build()).build();
}

String answer(ChatClient chatClient, String conversationId, String question) {
    return chatClient.prompt()
        .advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, conversationId))
        .user(question)
        .call()
        .content();
}
```

```java
@Service
class KnowledgeSearchService {
    private final VectorStore vectorStore;

    KnowledgeSearchService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    List<Document> search(String question) {
        return vectorStore.similaritySearch(SearchRequest.builder()
            .query(question)
            .topK(4)
            .similarityThreshold(0.75)
            .build());
    }
}
```

- Keep the conversation identifier explicit at the call site.
- Use in-memory chat memory only for demos, tests, or single-instance transient flows.
- Use RAG when the answer requires retrieved context, with explicit retrieval and empty-context behavior.
- Keep `EmbeddingModel` as the portable seam for vector generation and treat the concrete `VectorStore` implementation as a deployment decision.
- Open [references/advisors-memory-and-conversation-state.md](advisors-memory-and-conversation-state.md) when advisor ordering, persistent memory repositories, token buffering, or conversation isolation becomes the blocker.
- Open [references/rag-pipeline-and-vector-store-decisions.md](rag-pipeline-and-vector-store-decisions.md) when chunking, embeddings, metadata filters, vector-store choice, or advanced retrieval tuning is the blocker.
