# Tool failure and fallback

Open this reference when the ordinary tool path in [SKILL.md](../SKILL.md) is not enough and the blocker is explicit fallback behavior after a tool failure.

## Tool-failure blocker

**Problem:** tool execution fails, and the workflow must degrade explicitly instead of retrying blindly.

**Solution:** catch tool-call failures at the application seam and choose a fallback deliberately.

```java
@Service
class ResilientOrchestrator {
    private final ChatClient chatClient;
    private final ToolCallback[] toolCallbacks;

    ResilientOrchestrator(ChatClient.Builder builder, ToolCallback[] toolCallbacks) {
        this.chatClient = builder.build();
        this.toolCallbacks = toolCallbacks;
    }

    String runWithFallback(String question) {
        try {
            return chatClient.prompt()
                .user(question)
                .tools(toolCallbacks)
                .call()
                .content();
        } catch (ToolCallException ex) {
            return chatClient.prompt()
                .user("The tool failed: " + ex.getMessage() + ". Provide a response without using tools.")
                .call()
                .content();
        }
    }
}
```

## Decision points

| Situation | Pattern |
| --- | --- |
| tool failures must not retry blindly | explicit fallback at the application seam |

## Pitfalls

- Do not let tool exceptions leak into silent retries without an explicit policy.
- Keep the degraded path explicit enough that operators can tell whether the answer used a tool or not.
