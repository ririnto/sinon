# Spring Shell interactive flows and terminal UI

Open this reference when the normal command-and-option path in `SKILL.md` is not enough and the blocker is guided multi-step input, bounded selection, destructive confirmation, or richer terminal UI behavior.

## Decision points

| Situation | Use |
| --- | --- |
| One command with explicit options is still easy to understand | stay in `SKILL.md` |
| A command must ask follow-up questions based on earlier answers | guided flow |
| A bounded choice set matters more than free-form typing | single-select or multi-select input |
| Operators must browse or edit structured state interactively | richer terminal UI |

## Guided flow blocker

Use flow components when a command must lead the operator through several dependent answers and free-form command options would be error-prone.

- String input: use for names, namespaces, and labels.
- Path input: use for file and directory targets that benefit from shell-style completion.
- Confirmation input: use before destructive actions such as delete, revoke, or reset.
- Single-select and multi-select input: use when the choice set is bounded and operator discoverability matters.

```java
@Command(group = "cluster")
class ClusterCommands {
    private final ClusterService clusterService;
    private final ComponentFlow.Builder componentFlowBuilder;

    ClusterCommands(ClusterService clusterService, ComponentFlow.Builder componentFlowBuilder) {
        this.clusterService = clusterService;
        this.componentFlowBuilder = componentFlowBuilder;
    }

    @Command(command = "cluster reset", description = "Reset a target cluster")
    String reset(@Option(longNames = "cluster", required = true) String cluster) {
        ComponentFlow flow = componentFlowBuilder.clone()
            .reset()
            .withStringInput("namespace")
            .name("Namespace")
            .and()
            .withConfirmationInput("confirmed")
            .name("Reset cluster %s?".formatted(cluster))
            .and()
            .build();
        ComponentFlowResult result = flow.run();
        String namespace = (String) result.getContext().get("namespace");
        boolean confirmed = (Boolean) result.getContext().get("confirmed");
        if (!confirmed) {
            return "cancelled";
        }
        clusterService.reset(cluster, namespace);
        return "reset cluster=%s namespace=%s".formatted(cluster, namespace);
    }
}
```

Keep the resulting flow output small and explicit so that automation wrappers can still reason about the final result.

## Terminal UI blocker

Reserve TUI views for cases where operators must watch or edit structured state interactively.

- Good fit: browsing jobs, reviewing queues, confirming staged changes, or stepping through a setup wizard.
- Poor fit: single-shot commands that already work well as normal shell output.

When adding TUI behavior, keep a non-TUI command path available for automation and remote execution.

## Gotchas

- Do not force flow components onto commands that already work as simple options.
- Do not make a TUI-only path the sole way to perform an operational action.
- Do not forget that terminal UI behavior increases test and accessibility cost.

## Validation rule

Verify every guided flow or TUI action still has one automation-safe path with explicit options or stable output that scripts can use without interactive prompts.
