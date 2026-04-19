# Spring Statemachine when one machine lifecycle is not enough

Open this reference when the ordinary single-machine lifecycle in `SKILL.md` is not enough and the task needs many machine instances, persisted state, regions, or more advanced testing.

## Factory usage

Use `@EnableStateMachineFactory` when the application needs many machine instances with the same topology rather than one singleton machine.

```java
@Configuration
@EnableStateMachineFactory
class OrderStateMachineFactoryConfig {
}
```

Prefer one topology with many instances over many slightly different machine definitions.

## Persistence boundary

Persist machine state only when the lifecycle must survive restarts or be resumed later.

- Good fit: long-running orders, approvals, fulfillment, or manual review flows.
- Poor fit: short in-memory UI flows that already live inside one request sequence.
- Persistence keeps one machine restorable across time. It does not by itself create distributed coordination across nodes.

## Region usage

Use regions only when a workflow genuinely has parallel independent sub-lifecycles.

If the lifecycle is conceptually linear, regions usually add complexity without value.

## Testing persisted machines

Test the save and restore boundary separately from the transition rules.

```java
stateMachinePersister.persist(stateMachine, "order-1");
stateMachinePersister.restore(stateMachine, "order-1");
```

Verify both the restored state and any required extended-state variables.

Persisted-machine testing belongs here, not in the ordinary-path checklist, because save and restore behavior matters only after persistence is enabled.

## Related blockers

- Open [pseudo-states.md](pseudo-states.md) when the branching model depends on choice, junction, fork, join, or history semantics.
- Open [reactive-support.md](reactive-support.md) when the machine's actions, guards, or event flow must stay reactive.
