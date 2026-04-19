# RAG pipeline and vector store decisions

Open this reference when ingestion pipeline design, chunking strategy, embedding model choice, vector store behavior, retrieval tuning, or RAG assembly must be decided.

## ETL and document ingestion blocker

**Problem:** Spring AI `Document` ingestion fails silently or produces poor retrieval results downstream.

**Solution:** Always control the reader, transformer, and writer chain explicitly.

```java
PagePdfDocumentReader pdfReader = new PagePdfDocumentReader("classpath:/docs/reference.pdf");
vectorStore.write(TokenTextSplitter.builder()
    .withChunkSize(800)
    .withMinChunkSizeChars(350)
    .build()
    .split(pdfReader.read()));
```

Use Spring `Resource` inputs for static content and keep the reader choice explicit so ingestion stays reproducible.

## Chunking strategy blocker

**Problem:** Chunks are either too large (low precision) or too small (missing context and coherence).

**Solution:** Start with `TokenTextSplitter` and tune chunk size, minimum chunk size, and punctuation handling against representative retrieval queries.

| Strategy | Good for | Risk |
| --- | --- | --- |
| Default `TokenTextSplitter` | Good general-purpose retrieval | May need threshold tuning later |
| Smaller chunk size | Precise factual retrieval | Loses cross-chunk context |
| Larger chunk size | Coherent topic coverage | Lower precision, more token cost |

```java
TokenTextSplitter splitter = TokenTextSplitter.builder().withChunkSize(800).withMinChunkSizeChars(350).withKeepSeparator(true).build();
```

Verify retrieval quality with a representative question set before shipping. Do not treat chunk size as a one-time tuning knob.

## Embedding model blocker

**Problem:** Embeddings do not match the retrieval expectation or are slow to generate at scale.

**Solution:** Separate embedding generation from ingestion and align the model family with the vector store.

```java
@Bean
EmbeddingModel embeddingModel(OpenAiEmbeddingModel model) {
    return model;
}

@Bean
VectorStore vectorStore(EmbeddingModel embeddingModel, PgVectorStore pgVectorStore) {
    return pgVectorStore;
}
```

Use `EmbeddingModel` interface so the embedding source can be swapped without changing retrieval code. Batch embedding calls during ingestion to reduce per-document overhead.

## Vector store selection blocker

**Problem:** The chosen vector store does not fit the deployment environment or retrieval latency target.

**Solution:** Match store characteristics to the deployment constraint.

| Store | Best when | Limitation |
| --- | --- | --- |
| PgVector | PostgreSQL is already in the stack | Requires pgvector extension |
| Qdrant | High-dimensional similarity at scale | Separate service required |
| Milvus | High throughput with partitioned data | Separate service required |
| Redis | Sub-millisecond retrieval is critical | Memory-constrained at scale |
| In-memory | Local development and testing only | No persistence, no shared state |

```java
@Bean
VectorStore vectorStore(EmbeddingModel model) {
    return new PgVectorStore(model, dataSource);
}
```

Prefer stores that align with existing infrastructure first. Treat in-memory stores as development-only artifacts.

## Metadata filtering blocker

**Problem:** Similarity search returns too many irrelevant results because no pre-filter is applied.

**Solution:** Use `FilterExpression` at retrieval time to scope by document metadata.

```java
SearchRequest request = SearchRequest.builder()
    .query(question)
    .topK(5)
    .filterExpression("source == 'product-docs' && version == '2.1'")
    .build();
```

Attach metadata during ingestion and keep it stable across re-indexing. When filter cardinality is high, validate that the filter actually prunes results and does not accidentally exclude all matches.

## Retrieval tuning blocker

**Problem:** `similaritySearch` returns wrong documents or the same documents regardless of query.

**Solution:** Tune `topK` and `similarityThreshold` together and verify with a closed test set.

```java
SearchRequest request = SearchRequest.builder().query(question).topK(5).similarityThreshold(0.72).build();
```

- `topK` controls how many candidates are returned before re-ranking. Set it higher than the desired answer-set size when recall matters.
- `similarityThreshold` is store-specific. Test against a known-relevant query to find the right cutoff before hardening it.
- Keep the query-time embedding path aligned with the same embedding model family used at ingestion time.

## RAG assembly blocker

**Problem:** Retrieved documents are injected into the prompt without organization or deduplication, causing confusing or contradictory answers.

**Solution:** Separate retrieval, filtering, ordering, and prompt assembly.

```java
List<Document> docs = vectorStore.similaritySearch(request);
Map<String, Document> unique = new LinkedHashMap<>();
for (Document doc : docs) {
    if (!unique.containsKey(doc.getId())) {
        unique.put(doc.getId(), doc);
    }
}
StringBuilder context = new StringBuilder();
for (Document doc : unique.values()) {
    context.append("## " + doc.getMetadata().get("source") + "\n" + doc.getContent() + "\n\n");
}
```

This example first deduplicates by document ID and then assembles the retained documents into ordered prompt context.

- Use `Document::getId` for deduplication, not content similarity.
- Append source metadata as section headers so the model can attribute answers.
- Validate that the assembled context fits within the model's context window and token budget.

## Decision checklist

- [ ] Ingestion reader and transformer chain are explicit, not auto-configured defaults
- [ ] Chunking strategy is tuned against representative queries, not guessed
- [ ] Embedding model is injected as a bean, not hardcoded to a store implementation
- [ ] Vector store choice fits the deployment environment and latency target
- [ ] Metadata filters are applied at retrieval time and tested for recall
- [ ] Retrieval `topK` and `similarityThreshold` are tuned with a closed test set
- [ ] RAG assembly deduplicates and annotates retrieved documents before prompt injection
