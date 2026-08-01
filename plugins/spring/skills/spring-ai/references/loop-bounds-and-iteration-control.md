# Loop bounds and iteration control

Open this reference when the ordinary path is not enough and the blocker is bounding an iterative workflow that may not converge by itself.

## Loop-bound blocker

Problem: a multi-step agent loop can continue indefinitely without converging.

Solution: keep agent state explicit and enforce an iteration bound in application code.

```java
enum WorkflowState {
    RUNNING,
    COMPLETE
}

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
        WorkflowState state = WorkflowState.RUNNING;
        for (int i = 0; i < maxIterations && state == WorkflowState.RUNNING; i++) {
            lastResponse = chatClient.prompt().user(question).tools(toolCallbacks).call().content();
            state = applicationWorkflowState();
        }
        return lastResponse;
    }
}
```

Let `ToolCallingAdvisor` manage tool-call iterations.
Use application-owned workflow state for the outer workflow bound instead of inspecting model text for a tool-call marker.

## Decision points

| Situation | Pattern |
| --- | --- |
| iteration may not converge by itself | explicit loop bound |

## Pitfalls

- Do not rely on the model to stop looping without an explicit application-level bound.
- Keep iteration count, stop reason, and final state observable in logs or metrics.
