# Loop bounds and iteration control

Open this reference when the ordinary path in [SKILL.md](../SKILL.md) is not enough and the blocker is bounding an iterative workflow that may not converge by itself.

## Loop-bound blocker

**Problem:** a multi-step agent loop can continue indefinitely without converging.

**Solution:** keep agent state explicit and enforce an iteration bound in application code.

```java
@Service
class BoundedAssistant {
    private final ChatClient chatClient;
    private final ToolCallback[] toolCallbacks;
    private final int maxIterations = 5;

    BoundedAssistant(ChatClient.Builder builder, ToolCallback[] toolCallbacks) {
        this.chatClient = builder.build();
        this.toolCallbacks = toolCallbacks;
    }

    String answer(String question) {
        String lastResponse = "";
        for (int i = 0; i < maxIterations; i++) {
            lastResponse = chatClient.prompt().user(question).tools(toolCallbacks).call().content();
            if (!lastResponse.contains("[TOOL_CALL]")) {
                break;
            }
        }
        return lastResponse;
    }
}
```

## Decision points

| Situation | Pattern |
| --- | --- |
| iteration may not converge by itself | explicit loop bound |

## Pitfalls

- Do not rely on the model to stop looping without an explicit application-level bound.
- Keep iteration count, stop reason, and final state observable in logs or metrics.
