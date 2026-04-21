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

### 1. BOM version change

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bom</artifactId>
    <version>1.1.4</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

Replace `1.1.4` with the target Spring AI version during the upgrade and review starter availability before merging.

### 2. Provider starter compatibility check

Not every provider starter is available in every Spring AI release. Check the reference documentation for the target version before changing the BOM.

```bash
mvn dependency:list | grep spring-ai-starter-model
```

### 3. Tool API migration check

Tool registration APIs can change across release lines. If the target version changes tool registration semantics, update the application seam and tests in the same branch.

```java
ChatClient chatClient = ChatClient.create(chatModel);
```

Keep the tool registration style aligned with the target Spring AI version instead of mixing examples from different releases.

### 4. Structured output type migration

When upgrading, verify that the POJO record or class used for structured output still maps correctly.

```java
record ReleaseSummary(String version, List<String> breakingChanges, List<String> actions) {}
```

Check that field names, types, and nested records still produce the expected schema used by the model.

### 5. Retrieval configuration migration

When defaults change, set retrieval controls explicitly so behavior does not drift across versions.

```java
SearchRequest.builder().query(q).topK(5).similarityThreshold(0.72).build();
```

## Provider migration rules

When moving from one provider family to another:

1. Replace the model starter dependency.
2. Update configuration properties or deployment names at the provider edge.
3. Review tool descriptions and structured-output prompts.
4. Re-run evaluation harnesses against the new provider.
5. Re-check image, audio, or moderation surfaces separately if the application uses them.

## Embedding model migration

When the embedding model changes, re-ingest all documents. Vector representations are not interchangeable across embedding providers.

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
