---
name: "spring-ai"
description: "Use this skill when building Spring AI application features with ChatClient, prompt templates, structured output, tool calling, advisors, chat memory, embeddings, vector stores, RAG, MCP, image or audio model flows, moderation, evaluation, or provider-neutral model integration in Spring."
metadata:
  title: "Spring AI"
  official_project_url: "https://spring.io/projects/spring-ai"
  reference_doc_urls:
    - "https://docs.spring.io/spring-ai/reference/"
    - "https://docs.spring.io/spring-ai/reference/api/chatclient.html"
    - "https://docs.spring.io/spring-ai/reference/api/tools.html"
    - "https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html"
    - "https://docs.spring.io/spring-ai/reference/api/vectordbs.html"
    - "https://docs.spring.io/spring-ai/reference/api/image/"
    - "https://docs.spring.io/spring-ai/reference/api/audio/"
    - "https://docs.spring.io/spring-ai/reference/api/moderation/"
    - "https://docs.spring.io/spring-ai/reference/api/effective-agents.html"
    - "https://docs.spring.io/spring-ai/reference/api/usage-handling.html"
  version: "1.1.4"
---

Use this skill when building Spring AI application features with `ChatClient`, prompt templates, structured output, tool calling, advisors, chat memory, embeddings, vector stores, retrieval-augmented generation, MCP, image or audio model flows, moderation, effective-agent workflows, or other provider-neutral model seams in Spring.

## Boundaries

Use `spring-ai` for model-facing application seams, retrieval flow, Spring-managed AI integration, and provider-neutral model abstractions.

- Use `spring-integration` for non-AI message routing, adapters, and Enterprise Integration Patterns.
- Keep provider SDK details at the configuration edge. Application services should depend on Spring AI abstractions such as `ChatClient`, `EmbeddingModel`, `VectorStore`, `ImageModel`, `TranscriptionModel`, `TextToSpeechModel`, or `ModerationModel`.
- Keep business rules outside prompts and outside tool implementations. Spring AI should orchestrate model interaction, not replace core domain logic.

## Official surface map

Use this map to keep the official Spring AI surface visible without pushing the common path into `references/`.

| Surface | Start here when | Open a reference when |
| --- | --- | --- |
| Chat + prompt templates | The feature reads text and returns text or structured data | Provider fit or model capability is the blocker in [references/provider-selection-and-model-capability-fit.md](references/provider-selection-and-model-capability-fit.md) |
| Structured output | Downstream code needs fields, records, or typed objects | Upgrade or provider behavior changes the output contract in [references/upgrade-notes-and-migration-branches.md](references/upgrade-notes-and-migration-branches.md) |
| Tool calling | The model may request a narrow, side-effect-safe application capability | Sequential tool choreography is the blocker in [references/advanced-tool-orchestration.md](references/advanced-tool-orchestration.md), tool-set curation is the blocker in [references/tool-set-curation.md](references/tool-set-curation.md), or fallback policy is the blocker in [references/tool-failure-and-fallback.md](references/tool-failure-and-fallback.md) |
| Advisors + chat memory | Requests need prompt decoration, history, or token-window control | Advisor ordering or persistent memory is the blocker in [references/advisors-memory-and-conversation-state.md](references/advisors-memory-and-conversation-state.md) |
| RAG + vector stores | The answer must use retrieved enterprise context | Ingestion, chunking, embeddings, or store choice is the blocker in [references/rag-pipeline-and-vector-store-decisions.md](references/rag-pipeline-and-vector-store-decisions.md) |
| MCP | Tools or prompts cross a process or service boundary | Client/server choice or transport setup is the blocker in [references/mcp-client-server-boundaries.md](references/mcp-client-server-boundaries.md) |
| Vision + image generation | The feature must inspect images or generate images from prompts | Vision payload shape is the blocker in [references/image-generation-and-vision-inputs.md](references/image-generation-and-vision-inputs.md), multiple-image comparison is the blocker in [references/multiple-image-comparison.md](references/multiple-image-comparison.md), or image-model output is the blocker in [references/image-generation.md](references/image-generation.md) |
| Audio transcription + speech | The feature transcribes audio or returns synthesized speech | Transcription or TTS configuration is the blocker in [references/audio-transcription-and-speech-output.md](references/audio-transcription-and-speech-output.md) |
| Moderation | The application needs input or output safety gates | Moderation placement or category thresholds are the blocker in [references/moderation-and-safety-gates.md](references/moderation-and-safety-gates.md) |
| Effective agents | One bounded workflow must route, chain, plan, or iteratively refine work | Routing is the blocker in [references/routing-workflow.md](references/routing-workflow.md), chaining is the blocker in [references/chain-workflow.md](references/chain-workflow.md), stepwise planning is the blocker in [references/planning-and-stepwise-execution.md](references/planning-and-stepwise-execution.md), or loop bounds are the blocker in [references/loop-bounds-and-iteration-control.md](references/loop-bounds-and-iteration-control.md) |
| Evaluation + testing | Prompt, retrieval, or tool behavior needs repeatable checks | Evaluation harness design is the blocker in [references/testing-and-evaluation-harnesses.md](references/testing-and-evaluation-harnesses.md) |
| Usage + observability | You need token accounting, latency, tracing, or production debugging | Telemetry or incident diagnosis is the blocker in [references/observability-and-production-debugging.md](references/observability-and-production-debugging.md) |
| Local development infra | You need local models, vector stores, or containerized dev services | Local model runtime is the blocker in [references/development-services-and-local-infra.md](references/development-services-and-local-infra.md), local vector-store provisioning is the blocker in [references/local-vector-store-dev.md](references/local-vector-store-dev.md), or full containerized bootstrap is the blocker in [references/containerized-dev-environment.md](references/containerized-dev-environment.md) |
| Upgrade and migration | Version changes alter starters, APIs, defaults, or provider behavior | Upgrade mechanics are the blocker in [references/upgrade-notes-and-migration-branches.md](references/upgrade-notes-and-migration-branches.md) |

## Common path

The ordinary Spring AI job is:

1. Pin one Spring AI BOM version and add only the starters needed for the first production use case.
2. Start with one provider-neutral `ChatClient` seam around an application service.
3. Use prompt templates and structured output before adding tools, memory, or retrieval.
4. Expose only narrow, side-effect-safe tools when the plain prompt path is already correct.
5. Add advisors or chat memory only when the use case needs request decoration or multi-turn continuity.
6. Add RAG only after the non-RAG path is testable and the retrieval boundary is explicit.
7. Add image, audio, moderation, MCP, or effective-agent workflows only for concrete blockers, not by default.
8. Validate prompt assembly, output mapping, tool safety, conversation scoping, retrieval behavior, token usage, and production telemetry before rollout.

## Dependency baseline

Import the Spring AI BOM and add only the starters needed for the current model and optional retrieval path.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.1.4</version>
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

Add retrieval, image, audio, moderation, or MCP starters only when that surface is part of the current job. Open [references/upgrade-notes-and-migration-branches.md](references/upgrade-notes-and-migration-branches.md) when the target Spring AI version differs from the version pinned here.

## First safe setup

### Minimal provider properties

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
```

Start with one provider, one model, and one environment-backed secret. Open [references/provider-selection-and-model-capability-fit.md](references/provider-selection-and-model-capability-fit.md) when model family, context window, latency, cost, or provider fit is still unclear.

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

Keep the application seam on `ChatClient` or another Spring AI abstraction. Do not let controllers or domain code depend directly on a provider SDK.

## Prompt templating and structured output

Use prompt templates before introducing tools, memory, or retrieval. Keep variables explicit and keep prompt text reviewable in code.

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
            .user(user -> user
                .text("Summarize the release notes and list required migration actions. Notes: {notes}")
                .param("notes", releaseNotes))
            .call()
            .entity(ReleaseSummary.class);
    }
}
```

- Keep prompt variables named and explicit.
- Put reusable system instructions on the `ChatClient` builder or a dedicated service seam.
- Start with Spring AI mapping such as `.entity(...)` so the application stays on Spring AI's portable output contract instead of binding ordinary flows to one provider's JSON mode.
- Keep provider-native JSON modes as an optimization, not the default path, and open [references/upgrade-notes-and-migration-branches.md](references/upgrade-notes-and-migration-branches.md) when version or provider changes threaten the output contract.

## Tool boundary

Add tools only when the model genuinely needs a bounded application capability.

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
- Open [references/advanced-tool-orchestration.md](references/advanced-tool-orchestration.md) when one tool call must explicitly feed the next.
- Open [references/tool-set-curation.md](references/tool-set-curation.md) when the blocker is exposing only a curated tool set.
- Open [references/tool-failure-and-fallback.md](references/tool-failure-and-fallback.md) when the blocker is explicit fallback behavior after tool failure.
- Open [references/mcp-client-server-boundaries.md](references/mcp-client-server-boundaries.md) when the tool boundary may need MCP instead of an in-process Spring bean.

## Memory and retrieval escalation

Use advisors when the request or response must be decorated around the model call. Use `ChatMemory` through an advisor instead of manually appending prior turns.

```java
@Bean
ChatClient supportChatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
    return builder.defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory)).build();
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
- Add RAG only after the non-RAG path is correct and testable.
- Keep `EmbeddingModel` as the portable seam for vector generation and treat the concrete `VectorStore` implementation as a deployment decision.
- Open [references/advisors-memory-and-conversation-state.md](references/advisors-memory-and-conversation-state.md) when advisor ordering, persistent memory repositories, token buffering, or conversation isolation becomes the blocker.
- Open [references/rag-pipeline-and-vector-store-decisions.md](references/rag-pipeline-and-vector-store-decisions.md) when chunking, embeddings, metadata filters, vector-store choice, or advanced retrieval tuning is the blocker.

## Secondary official surfaces

These surfaces are part of official Spring AI scope, but they are not on the ordinary path unless the use case requires them.

- Open [references/image-generation-and-vision-inputs.md](references/image-generation-and-vision-inputs.md) when the feature must attach single-image vision input to a chat request.
- Open [references/multiple-image-comparison.md](references/multiple-image-comparison.md) when the blocker is comparing or cross-referencing several images in one request.
- Open [references/image-generation.md](references/image-generation.md) when the blocker is producing generated image artifacts instead of text.
- Open [references/audio-transcription-and-speech-output.md](references/audio-transcription-and-speech-output.md) when the feature must transcribe audio or synthesize speech.
- Open [references/moderation-and-safety-gates.md](references/moderation-and-safety-gates.md) when input or output moderation is required.
- Open [references/routing-workflow.md](references/routing-workflow.md) when routing is the blocker.
- Open [references/chain-workflow.md](references/chain-workflow.md) when one bounded model step must explicitly feed the next.
- Open [references/planning-and-stepwise-execution.md](references/planning-and-stepwise-execution.md) when the task is too large for one safe pass and needs a bounded plan first.
- Open [references/loop-bounds-and-iteration-control.md](references/loop-bounds-and-iteration-control.md) when iterative refinement needs an application-level bound.

## Usage handling and observability

Treat token accounting as part of the application contract, not as an afterthought.

- Read `Usage` from the final `ChatResponse` when cost, token budgets, or provider drift matter.
- Record prompt, completion, and total token counts together with latency and tool or retrieval activity.
- Open [references/observability-and-production-debugging.md](references/observability-and-production-debugging.md) when usage accounting, tracing, or production debugging becomes the blocker.

## Minimal validation

Verify the first Spring AI path before expanding scope.

- Verify prompt assembly without a live provider where possible.
- Verify structured-output mapping for one valid and one invalid response shape.
- Verify tool methods behave like normal application APIs, including validation and authorization boundaries.
- Verify advisor and chat-memory scoping with an explicit conversation ID.
- Verify retrieval returns the expected documents and that empty-context behavior is explicit.
- Verify token usage, latency, and tool-call identity are observable in the final path.

Open [references/testing-and-evaluation-harnesses.md](references/testing-and-evaluation-harnesses.md) when the task needs repeatable evaluation datasets, regression checks, or infrastructure-backed integration tests. Open [references/observability-and-production-debugging.md](references/observability-and-production-debugging.md) when adding usage accounting, tracing, or production incident diagnostics.

## Production guardrails

- Externalize provider credentials, endpoints, model names, moderation settings, and retrieval settings.
- Keep prompts, tool contracts, retrieval settings, and structured-output types versioned and reviewable.
- Put timeouts, retries, fallback behavior, and provider switching at the provider edge.
- Log latency, token usage, retrieval count, and tool usage without leaking secrets or personal data.
- Treat image, audio, and moderation model choices as explicit configuration, not ambient defaults.

## References

- Open [references/provider-selection-and-model-capability-fit.md](references/provider-selection-and-model-capability-fit.md) when choosing a provider, model family, context window, latency profile, cost envelope, or model-type selector.
- Open [references/advisors-memory-and-conversation-state.md](references/advisors-memory-and-conversation-state.md) when advisor ordering, persistent memory repositories, or token buffering is the blocker.
- Open [references/rag-pipeline-and-vector-store-decisions.md](references/rag-pipeline-and-vector-store-decisions.md) when chunking, embeddings, filters, vector stores, or retrieval flow design is the blocker.
- Open [references/mcp-client-server-boundaries.md](references/mcp-client-server-boundaries.md) when deciding between in-process tools and MCP client or server boundaries.
- Open [references/advanced-tool-orchestration.md](references/advanced-tool-orchestration.md) when one tool call must explicitly feed the next.
- Open [references/tool-set-curation.md](references/tool-set-curation.md) when the blocker is exposing only a curated tool set to the model.
- Open [references/tool-failure-and-fallback.md](references/tool-failure-and-fallback.md) when tool failures need explicit fallback behavior.
- Open [references/image-generation-and-vision-inputs.md](references/image-generation-and-vision-inputs.md) when the blocker is single-image vision-style input handling.
- Open [references/multiple-image-comparison.md](references/multiple-image-comparison.md) when the blocker is comparing several images in one request.
- Open [references/image-generation.md](references/image-generation.md) when the blocker is text-to-image generation.
- Open [references/audio-transcription-and-speech-output.md](references/audio-transcription-and-speech-output.md) when the blocker is speech-to-text or text-to-speech behavior.
- Open [references/moderation-and-safety-gates.md](references/moderation-and-safety-gates.md) when content safety must be enforced before or after generation.
- Open [references/routing-workflow.md](references/routing-workflow.md) when the blocker is routing across bounded specialist seams.
- Open [references/chain-workflow.md](references/chain-workflow.md) when the blocker is an explicit multi-step chain.
- Open [references/planning-and-stepwise-execution.md](references/planning-and-stepwise-execution.md) when the blocker is bounded planning before execution.
- Open [references/loop-bounds-and-iteration-control.md](references/loop-bounds-and-iteration-control.md) when the blocker is bounding iterative loops.
- Open [references/testing-and-evaluation-harnesses.md](references/testing-and-evaluation-harnesses.md) when the task needs repeatable evaluation datasets, regression checks, or model-behavior test harnesses.
- Open [references/observability-and-production-debugging.md](references/observability-and-production-debugging.md) when token accounting, tracing, or production debugging must be added.
- Open [references/development-services-and-local-infra.md](references/development-services-and-local-infra.md) when the blocker is a local model runtime such as Ollama.
- Open [references/local-vector-store-dev.md](references/local-vector-store-dev.md) when the blocker is a reproducible local vector store.
- Open [references/containerized-dev-environment.md](references/containerized-dev-environment.md) when the blocker is a repeatable containerized AI development stack.
- Open [references/upgrade-notes-and-migration-branches.md](references/upgrade-notes-and-migration-branches.md) when upgrading Spring AI or reconciling starter, API, or model changes across branches.
