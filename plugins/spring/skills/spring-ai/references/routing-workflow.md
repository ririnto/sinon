# Routing workflow

Open this reference when the ordinary tool path in [SKILL.md](../SKILL.md) is not enough and the blocker is routing one request across bounded specialist seams.

## Use this file for one blocker family

Use this file only when the application must classify a request and hand it to one of several explicit downstream seams.

## Routing workflow blocker

**Problem:** one user request must be dispatched to different specialist flows instead of one shared prompt path.

**Solution:** keep routing explicit and let the router choose among bounded downstream seams.

```java
@Service
class RoutingAgent {
    private final ChatClient router;
    private final ChatClient weatherClient;
    private final ChatClient inventoryClient;

    RoutingAgent(ChatClient.Builder builder, @Qualifier("weatherChatClient") ChatClient weatherClient, @Qualifier("inventoryChatClient") ChatClient inventoryClient) {
        this.router = builder.defaultSystem("Route the user's question to the appropriate specialist.").build();
        this.weatherClient = weatherClient;
        this.inventoryClient = inventoryClient;
    }

    String answer(String question) {
        return switch (router.prompt().user("Route: " + question).call().content().trim().toLowerCase()) {
            case "weather" -> weatherClient.prompt().user(question).call().content();
            case "inventory" -> inventoryClient.prompt().user(question).call().content();
            default -> "I could not determine which specialist can help.";
        };
    }
}
```

## Decision points

| Situation | Pattern |
| --- | --- |
| Different request classes need different specialist seams | routing workflow |

## Pitfalls

- Do not let a router choose among unbounded downstream tool sets.
- Keep the router output constrained to a known downstream seam list instead of free-form action generation.
- Open [chain-workflow.md](chain-workflow.md) when the blocker is an explicit multi-step chain.
- Open [planning-and-stepwise-execution.md](planning-and-stepwise-execution.md) when the blocker is bounded planning before execution.
- Open [loop-bounds-and-iteration-control.md](loop-bounds-and-iteration-control.md) when the blocker is convergence control for iterative workflows.
