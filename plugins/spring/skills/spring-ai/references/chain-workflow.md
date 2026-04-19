# Chain workflow

Open this reference when the ordinary path in [SKILL.md](../SKILL.md) is not enough and the blocker is that one bounded model step must explicitly feed the next.

## Chain workflow blocker

**Problem:** the output of one model step must become the controlled input to the next step.

**Solution:** keep the chain explicit in application code instead of asking one prompt to invent the whole process.

```java
@Service
class ChainWorkflow {
    private final ChatClient chatClient;
    private final String[] systemPrompts;

    ChainWorkflow(ChatClient chatClient, String[] systemPrompts) {
        this.chatClient = chatClient;
        this.systemPrompts = systemPrompts;
    }

    String chain(String userInput) {
        String response = userInput;
        for (String prompt : systemPrompts) {
            response = chatClient.prompt().user(prompt + "\n" + response).call().content();
        }
        return response;
    }
}
```

## Decision points

| Situation | Pattern |
| --- | --- |
| one step must refine or transform the next step's input | chain workflow |

## Pitfalls

- Do not collapse several explicit transforms into one opaque prompt.
- Keep each step's input and output observable so failures can be localized.
