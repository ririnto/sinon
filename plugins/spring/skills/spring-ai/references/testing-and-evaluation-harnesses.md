# Spring AI testing and evaluation harnesses

Open this reference when the ordinary prompt-assembly and structured-output checks in `SKILL.md` are not enough and the task needs test harnesses, AI-specific mocking, or evaluation tooling.

Keep test cases and evaluation harnesses versioned alongside the prompts and retrieval logic they exercise.

Use JUnit 5 style consistently in these examples: `@Test`, `@ExtendWith(MockitoExtension.class)` for Mockito-backed unit tests, `@SpringBootTest` for Spring-backed integration tests, and Jupiter `Assertions` helpers such as `assertEquals`, `assertTrue`, `assertThrowsExactly`, and `assertIterableEquals`.

## When to open this file

Open this reference when the task involves:

- Mocking `ChatClient` behavior without a live provider
- Writing evaluation suites for answer quality or tool-decision accuracy
- Testing RAG retrieval fidelity under controlled document sets
- Verifying embedding and vector-store behavior in integration tests
- Running Spring-backed integration tests against preprovisioned local services or dedicated test infrastructure

## ChatClient mocking without a live provider

Use a mocked `ChatModel` or a higher-level application seam for fast, deterministic unit tests.

### Mock via `ChatClient.create(chatModel)`

```java
static ChatClient mockChatClient(ChatModel chatModel) {
    return ChatClient.create(chatModel);
}
```

### Stub with structured output

```java
@ExtendWith(MockitoExtension.class)
class ReleaseNotesAssistantTests {
    @Mock
    ChatModel chatModel;

    @Test
    void summarizesReleaseNotes() {
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("""{"version":"1.0","breakingChanges":[],"actions":["migrate"]}"""))));
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
        ChatClient chat = ChatClient.create(chatModel);
        ReleaseNotesAssistant assistant = new ReleaseNotesAssistant(chat);
        ReleaseSummary summary = assistant.summarize("breaking change here");
        assertEquals("1.0", summary.version());
    }
}
```

## Embedding and vector-store integration testing

Use `@SpringBootTest` with a preprovisioned local vector store, dedicated integration-test service, or a test profile that points at disposable infrastructure prepared outside the test JVM.

### Vector store integration test shape

```java
@SpringBootTest
class KnowledgeBaseRetrievalTests {
    @Autowired
    KnowledgeBase knowledgeBase;

    @Test
    void retrievesRelatedParagraphs() {
        knowledgeBase.ingest("guide", List.of("Spring AI supports ChatClient.", "ChatClient wraps a Model.", "Embeddings convert text to vectors."));
        List<Document> results = knowledgeBase.search("how does ChatClient work");
        assertAll(
            () -> assertEquals(2, results.size()),
            () -> assertTrue(results.get(0).getContent().contains("ChatClient"))
        );
    }
}
```

### Similarity threshold sanity check

```java
@SpringBootTest
class SimilarityThresholdTests {
    @Autowired
    KnowledgeBase knowledgeBase;

    @Autowired
    VectorStore vectorStore;

    @Test
    void highThresholdReturnsEmptyOrMinimal() {
        knowledgeBase.ingest("source", List.of("unrelated content"));
        SearchRequest request = SearchRequest.builder().query("totally unrelated query").topK(5).similarityThreshold(0.95).build();
        List<Document> results = vectorStore.similaritySearch(request);
        assertTrue(results.isEmpty());
    }
}
```

## Tool calling test shape

Verify tool methods behave as normal application APIs.

```java
@ExtendWith(MockitoExtension.class)
class InventoryToolTests {
    @Mock
    InventoryRepository inventoryRepository;

    @InjectMocks
    InventoryTools inventoryTools;

    @Test
    void quantityReturnsAvailableStock() {
        when(inventoryRepository.availableQuantity("SKU-42")).thenReturn(10);
        int quantity = inventoryTools.quantity("SKU-42");
        assertEquals(10, quantity);
    }

    @Test
    void quantityThrowsOnInvalidInput() {
        assertThrowsExactly(IllegalArgumentException.class, () -> inventoryTools.quantity(null));
    }
}
```

## Evaluation harness basics

Spring AI ships model-evaluation support such as `RelevancyEvaluator` and `FactCheckingEvaluator`. Use those when the task needs repeatable answer-quality checks beyond plain assertions.

### Relevancy evaluation

```java
@ExtendWith(MockitoExtension.class)
class EvaluationHarnessTests {
    @Mock
    ChatModel chatModel;

    @Test
    void answerIsRelevantToRetrievedContext() {
        EvaluationRequest evaluationRequest = new EvaluationRequest(question, chatResponse.getMetadata().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT), chatResponse.getResult().getOutput().getText());
        RelevancyEvaluator evaluator = new RelevancyEvaluator(ChatClient.builder(chatModel));
        EvaluationResponse evaluationResponse = evaluator.evaluate(evaluationRequest);
        assertTrue(evaluationResponse.isPass());
    }
}
```

### Tool-decision evaluation

Evaluate whether the model is presented with the right tool set and whether the chosen tool path matches the user intent.

| User intent | Expected tool path | First assertion |
| --- | --- | --- |
| 'What is the stock for SKU-42?' | inventory lookup tool only | tool description mentions stock, SKU, and availability explicitly |
| 'Summarize release notes' | no tool call | structured-output path works without tool registration |
| 'Find documents about pgvector' | retrieval path before answer generation | retrieved document IDs are present in evaluation output |

## Decision points

| Situation | First check |
| --- | --- |
| Unit test too slow or flaky | use mock `ChatClient` instead of live provider |
| Retrieval results non-deterministic | pin document set and similarity threshold in test |
| Tool decision is wrong | verify the prompt explicitly names the tool and its description |
| Evaluation suite is missing | start with `RelevancyEvaluator` on a closed dataset |
| Vector store unavailable in CI | point the test profile at a prepared integration-test service |

## Operational checks

- Run prompt-assembly tests without any network calls.
- Verify structured-output mapping separately from live model responses.
- Use controlled document sets in RAG tests so similarity thresholds are meaningful.
- Log the evaluation prompt alongside the test case so regressions are traceable.
- Gate infrastructure-dependent integration tests behind a profile or explicit maven flag so they can be skipped in fast CI runs.
