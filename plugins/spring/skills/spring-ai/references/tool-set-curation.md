# Tool-set curation

Open this reference when the ordinary tool path in [SKILL.md](../SKILL.md) is not enough and the blocker is exposing only a curated tool set to the model.

## Multi-tool blocker

**Problem:** the same request may require several tools, but the application must still control which tool set is exposed.

**Solution:** register only the tool callbacks relevant to that workflow and keep the allowed tool surface narrow.

```java
@Service
class MultiToolAssistant {
    private final ChatClient chatClient;
    private final ToolCallback[] toolCallbacks;

    MultiToolAssistant(ChatClient.Builder builder, ToolCallback[] toolCallbacks) {
        this.chatClient = builder.build();
        this.toolCallbacks = toolCallbacks;
    }

    String answer(String question) {
        return chatClient.prompt()
            .user(question)
            .tools(toolCallbacks)
            .call()
            .content();
    }
}
```

Treat tool selection as an application contract. Do not expose unrelated tools to the model just because they are available in the same JVM.

## Decision points

| Situation | Pattern |
| --- | --- |
| one request needs a curated set of tools | multi-tool `ChatClient` path with a narrow tool set |

## Pitfalls

- Do not register tools that the current workflow does not need.
- Keep the allowed tool list explicit in application code or configuration.
