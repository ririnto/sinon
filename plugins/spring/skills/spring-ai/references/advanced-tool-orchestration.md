# Sequential tool orchestration

Open this reference when the ordinary tool-calling path in [SKILL.md](../SKILL.md) is not enough and the blocker is one explicit tool step that must feed the next.

## Use this file for one blocker family

Use this file only when the workflow needs explicit sequential choreography in application code.

## Sequential tool blocker

**Problem:** one tool call must produce an intermediate result before the next tool can run.

**Solution:** keep sequencing explicit in application code instead of assuming a single prompt should infer the whole workflow.

```java
@Service
class SequentialOrchestrator {
    private final ChatClient chatClient;
    private final ToolCallback[] toolCallbacks;

    SequentialOrchestrator(ChatClient.Builder builder, ToolCallback[] toolCallbacks) {
        this.chatClient = builder.build();
        this.toolCallbacks = toolCallbacks;
    }

    String run(String input) {
        String entity = chatClient.prompt()
            .user("Extract the product name from: " + input)
            .call()
            .content();
        return chatClient.prompt()
            .user("Find inventory for: " + entity)
            .tools(toolCallbacks)
            .call()
            .content();
    }
}
```

## Decision points

| Situation | Pattern |
| --- | --- |
| one tool depends on the previous result | explicit sequential orchestration |

## Pitfalls

- Do not assume a single prompt should own every orchestration decision.
- Do not rely on implicit model reasoning to infer the step order when the steps are already known in application code.
- Open [tool-set-curation.md](tool-set-curation.md) when the blocker is limiting which tools are exposed.
- Open [tool-failure-and-fallback.md](tool-failure-and-fallback.md) when the blocker is explicit fallback behavior after tool failure.
