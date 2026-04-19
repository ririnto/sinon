# Planning and stepwise execution

Open this reference when the ordinary path in [SKILL.md](../SKILL.md) is not enough and the blocker is that the task is too large for one safe pass.

## Planning blocker

**Problem:** the request is too large to execute safely in one pass.

**Solution:** generate a bounded plan first, then execute one step at a time through ordinary application seams.

```java
@Service
class PlanningAgent {
    private final ChatClient planner;
    private final ChatClient executor;

    PlanningAgent(ChatClient.Builder builder) {
        this.planner = builder.defaultSystem("Break down the user's request into numbered steps.").build();
        this.executor = builder.build();
    }

    String[] plan(String goal) {
        return parseSteps(planner.prompt().user("Plan: " + goal).call().content());
    }
}
```

## Decision points

| Situation | Pattern |
| --- | --- |
| the goal is too large for one safe pass | planning workflow |

## Pitfalls

- Do not run a planner when one direct tool or prompt path would do.
- Keep the plan bounded enough that each step can still be verified through ordinary seams.
