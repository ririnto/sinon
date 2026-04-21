# Spring AI advisors, memory, and conversation state

Open this reference when the common-path advisor and chat-memory guidance in [SKILL.md](../SKILL.md) is not enough and the blocker is advisor ordering, persistent memory design, token buffering, or conversation isolation.

Keep the ordinary path in [SKILL.md](../SKILL.md). Use this file only when one of those specific blockers appears.

## When to open this

- Choosing between in-memory and repository-backed chat memory
- Configuring advisor ordering or advisor-scoped options
- Debugging why prior turns are or are not present in the prompt
- Deciding whether a `ChatMemory` implementation is safe for multi-user or multi-session use
- Truncating long histories without silently dropping required context

## Advisor chain basics

`ChatClient` processes messages through an ordered chain of advisors. Each advisor can read or modify the request and response around the `ChatModel` call.

Advisors wrap the model call. The outermost advisor runs first on the request path and last on the response path.

## Advisor ordering blocker

**Problem:** history, retrieval context, or token limits are applied in the wrong order, so the prompt seen by the model is not the prompt you expected.

**Solution:** register advisors in the order you want them to wrap the model call, then trace that order explicitly.

```java
@Bean
ChatClient chatClient(ChatClient.Builder builder, PromptChatMemoryAdvisor memoryAdvisor) {
    return builder
        .defaultAdvisors(memoryAdvisor)
        .build();
}
```

In this shape, `memoryAdvisor` runs before the model call.

Common ordering pattern:

```text
[audit advisor]
[memory advisor]
[model]
```

Put history assembly and truncation policy in the same memory strategy so the model sees a predictable context budget.

## Memory strategy blocker

**Problem:** the application needs conversation continuity, but the chosen memory store does not fit the deployment model.

**Solution:** choose memory persistence based on session scope, restart behavior, and instance topology.

| Strategy | Use when | Risk |
| --- | --- | --- |
| In-memory `MessageWindowChatMemory` | Demos, tests, single-instance transient conversations | Lost on restart, not shared across instances |
| Repository-backed `MessageWindowChatMemory` | Multi-user or multi-instance production flows | Requires starter, schema, and repository lifecycle planning |
| No memory | One-shot prompts with no history requirement | No continuity across turns |

### Default in-memory memory

```java
@Bean
ChatMemory chatMemory() {
    return MessageWindowChatMemory.builder().build();
}
```

### Repository-backed memory

```java
@Bean
ChatMemory jdbcChatMemory(JdbcChatMemoryRepository repository) {
    return MessageWindowChatMemory.builder()
        .chatMemoryRepository(repository)
        .maxMessages(20)
        .build();
}
```

Use repository-backed memory when the same conversation must survive restarts, move across instances, or remain available for later resumption.

## Conversation isolation blocker

**Problem:** one user sees another user's context, or resumed conversations do not load the expected history.

**Solution:** pass the conversation identifier explicitly through advisor parameters and keep the memory repository keyed by that identifier.

```java
String conversationId = "customer-42";

String answer = chatClient.prompt()
    .advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, conversationId))
    .user("Summarize our last discussion")
    .call()
    .content();
```

Keep the conversation ID explicit at the call site. Do not rely on mutable per-user memory objects stored in application code.

## Context budget blocker

**Problem:** memory injection makes prompts exceed the model context window.

**Solution:** bound the memory window explicitly and keep the conversation identifier stable.

```java
@Bean
PromptChatMemoryAdvisor memoryAdvisor(ChatMemory memory) {
    return PromptChatMemoryAdvisor.builder(memory)
        .conversationId("default")
        .build();
}

@Bean
ChatMemory boundedChatMemory(JdbcChatMemoryRepository repository) {
    return MessageWindowChatMemory.builder()
        .chatMemoryRepository(repository)
        .maxMessages(12)
        .build();
}
```

```text
[memory advisor]
[bounded chat memory]
[model]
```

Treat the message window as a tuned deployment parameter, not a magic constant.

## Decision points

| Situation | Choice |
| --- | --- |
| Single-session demo | `MessageWindowChatMemory` with default in-memory repository |
| Multi-user production | repository-backed `MessageWindowChatMemory` |
| Token budget is tight | smaller `MessageWindowChatMemory` window with explicit conversation IDs |
| Need restart-safe continuity | persistent repository plus explicit conversation IDs |
| Need to inspect prompt decoration order | trace and simplify advisor registration order |

## Pitfalls

- Do not use in-memory memory for production multi-session workloads.
- Do not mix transient and persistent memory strategies without a clear conversation-ID policy.
- Advisor ordering is easy to misread. Always verify the actual registration order when debugging missing context.
- Repository-backed memory needs its repository starter and schema strategy configured explicitly.
- Very long histories can still exceed model limits even with a token buffer. Trim the memory window and validate with representative conversations.
