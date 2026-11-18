---
metadata:
  reference:
    Spring AI:
      version: 2.0.1
      url:
        - https://docs.spring.io/spring-ai/reference/
        - https://docs.spring.io/spring-ai/reference/upgrade-notes.html
        - https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-bom/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-bom/2.0.1/spring-ai-bom-2.0.1.pom
    Anthropic Model Lifecycle:
      url: https://platform.claude.com/docs/en/about-claude/model-deprecations.md
name: spring-ai
description: >-
  Use Spring AI for prompts, model calls, tools, memory, retrieval, vector stores, and MCP.
---

# Spring AI

## Boundaries

Use `spring-ai` for model-facing application seams, retrieval flow, Spring-managed AI integration, and provider-neutral model abstractions.

- Non-AI message routing, adapters, and Enterprise Integration Patterns are outside this skill's scope.
- Keep provider SDK details at the configuration edge.
  - Application services should depend on Spring AI abstractions such as `ChatClient`, `EmbeddingModel`, `VectorStore`, `ImageModel`, `TranscriptionModel`, `TextToSpeechModel`, or `ModerationModel`.
- Keep business rules outside prompts and outside tool implementations.
  - Spring AI should orchestrate model interaction, not replace core domain logic.

## Surface routing

Read a reference when the corresponding decision applies:

- Model family, context, latency, cost, or provider fit: [provider selection](references/provider-selection-and-model-capability-fit.md).
- Output contracts, starters, APIs, defaults, or provider behavior affected by upgrades: [migration](references/upgrade-notes-and-migration-branches.md).
- Sequential tool calls: [orchestration](references/advanced-tool-orchestration.md); curated tools: [tool sets](references/tool-set-curation.md); fallback: [tool failure](references/tool-failure-and-fallback.md).
- Advisor ordering, persistent memory, token buffering, or conversation isolation: [advisors and memory](references/advisors-memory-and-conversation-state.md).
- RAG ingestion, chunking, embeddings, metadata filters, store selection, or advanced flow: [RAG pipeline](references/rag-pipeline-and-vector-store-decisions.md).
- choosing MCP versus an in-process bean, or MCP client/server or transport setup: [MCP boundaries](references/mcp-client-server-boundaries.md).
- Single-image input: [vision input](references/image-generation-and-vision-inputs.md); multiple images: [comparison](references/multiple-image-comparison.md); generated artifacts: [image generation](references/image-generation.md).
- Transcription or TTS configuration: [audio](references/audio-transcription-and-speech-output.md).
- Moderation placement or thresholds: [safety gates](references/moderation-and-safety-gates.md).
- Bounded agent workflows: [routing](references/routing-workflow.md), [chaining](references/chain-workflow.md), [planning](references/planning-and-stepwise-execution.md), or [iteration limits](references/loop-bounds-and-iteration-control.md).
- Repeatable model, retrieval, or tool evaluations: [evaluation harnesses](references/testing-and-evaluation-harnesses.md).
- Token accounting, latency, tracing, or incident diagnosis: [observability](references/observability-and-production-debugging.md).
- Local models and development services: [local infrastructure](references/development-services-and-local-infra.md); local vector stores: [store provisioning](references/local-vector-store-dev.md); containerized bootstrap: [container environment](references/containerized-dev-environment.md).

## Task scope

Choose the model, tool, memory, retrieval, or media surface required by the feature.
Do not build a plain-chat or non-RAG workflow as a prerequisite for a retrieval-only or tool-focused task.
Keep external calls and tool side effects within the task's authority and data-handling constraints.

## Dependencies and implementation

Spring AI 2.0.x supports Spring Boot 4.0.x and 4.1.x.
Import the Spring AI BOM and add only starters needed for the current feature.
For a new BOM, check Maven Central for the latest stable release compatible with the project's Boot line; keep existing pins unless an upgrade is authorized.
Keep credentials environment-backed, verify model support for the account and Spring AI version, and keep application code on Spring AI abstractions.

Use explicit, reviewable prompt variables and Spring AI mapping for structured output; treat provider-native JSON modes as an optimization.
Add only bounded tools; start with read-only or otherwise side-effect-safe capabilities and treat tool selection as an application contract.
Use advisors for request or response decoration and `ChatMemory` through an advisor for history; use repository-backed memory for production multi-session workloads and explicit conversation identifiers.
Use RAG for retrieved context with explicit empty-context behavior; keep `EmbeddingModel` portable and choose the `VectorStore` for deployment.

For provider setup, dependency declarations, prompt templates, structured output, tool calling, or memory and retrieval examples, read [setup, prompts, tools, and memory](references/setup-prompts-tools-and-memory.md).

## Usage handling and observability

Include token accounting in the application contract.

- Read `Usage` from the final `ChatResponse` when cost, token budgets, or provider drift matter.
- Record prompt, completion, and total token counts together with latency and tool or retrieval activity.
- Open [observability and production debugging](references/observability-and-production-debugging.md) when usage accounting, tracing, or production debugging becomes the blocker.

## Minimal validation

Use a local fixture for the prompt, tool call, or response format the feature consumes.
Prefer deterministic local checks.
Use live-provider evaluation only when the required evidence and authority justify it.

- Verify prompt assembly without a live provider where possible.
- Verify structured-output mapping for one valid and one invalid response shape.
- Verify tool methods behave like normal application APIs, including validation and authorization boundaries.
- Verify advisor and chat-memory scoping with an explicit conversation ID.
- Verify retrieval returns the expected documents and that empty-context behavior is explicit.
- Verify token usage, latency, and tool-call identity are observable in the final path.

Open [testing and evaluation harnesses](references/testing-and-evaluation-harnesses.md) when the task needs repeatable evaluation datasets, regression checks, or infrastructure-backed integration tests.

## Production guardrails

- Externalize provider credentials, endpoints, model names, moderation settings, and retrieval settings.
- Keep prompts, tool contracts, retrieval settings, and structured-output types versioned and reviewable.
- Put timeouts, retries, and provider switching at the provider edge.
- Keep tool-failure fallback at the application seam.
- Log latency, token usage, retrieval count, and tool usage without leaking secrets or personal data.
- Treat image, audio, and moderation model choices as explicit configuration, not ambient defaults.
