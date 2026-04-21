# Spring Statemachine pseudo states

Open this reference when guarded external transitions are no longer enough and the workflow needs explicit choice, junction, fork, join, or history semantics.

Keep pseudo states rare and intentional. Most workflows do not need more than external transitions and one or two guarded branches.

If one external transition plus a guard keeps the topology readable, stay in `SKILL.md` and do not add pseudo states.

## Choice and junction

- Choice: one incoming transition that branches based on conditions.
- Junction: merge or branch multiple paths when readability matters more than repeating guards inline.

Use them when the machine itself, not an application service, owns the branching semantics.

```java
@Override
public void configure(StateMachineStateConfigurer<States, Events> states) throws Exception {
    states.withStates()
        .initial(States.NEW)
        .choice(States.PAYMENT_REVIEW)
        .state(States.PAID)
        .state(States.CANCELLED);
}

@Override
public void configure(StateMachineTransitionConfigurer<States, Events> transitions) throws Exception {
    transitions
        .withChoice()
            .source(States.PAYMENT_REVIEW)
            .first(States.PAID, paymentAllowed())
            .last(States.CANCELLED);
}
```

## Fork and join

- Fork: split into parallel regions.
- Join: wait for parallel regions to converge before proceeding.

Use fork and join only when concurrency is truly part of the workflow model rather than an implementation detail.

## History

Use history when a nested state machine must resume the last active child state after interruption.

History is a lifecycle modeling decision, not a shortcut for persistence.

## Decision points

| Situation | Use |
| --- | --- |
| One guarded branch remains readable as a normal transition | external transition with a guard |
| One incoming branch chooses among outcomes | choice |
| Multiple paths merge or branch with clearer topology | junction |
| Parallel sub-lifecycles start together | fork |
| Parallel sub-lifecycles must all finish before continuing | join |
| Nested states must resume the last active child | history |

## Gotchas

- Do not reach for pseudo states when a simple external transition with a guard is enough.
- Do not model implementation concurrency with fork and join unless the business lifecycle itself is parallel.
- Do not confuse history with restart persistence; they solve different problems.

## Verification rule

Verify that the pseudo state makes the lifecycle topology clearer than a guarded external transition, then add a test that proves the branch, parallel join, or resumed child state actually occurs.
