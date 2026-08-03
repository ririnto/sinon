---
name: minimal-implementation
description: Select and implement the smallest complete code change. Use when solution shape, dependency choice, compatibility, migration, validation, or abstraction is materially undecided.
---

# Minimal Implementation

## Outcome

Deliver the current requirement end to end with the least new behavior and structure that remains correct and maintainable.

## Decision Ladder

Trace the code path the change touches before choosing a solution.

Stop at the first rung that fully satisfies the acceptance criteria:

1. Remove the need for new behavior or delete an obsolete path.
2. Reuse an existing project function, component, pattern, or data flow.
3. Use the standard library.
4. Use a native platform or framework capability.
5. Use an already-installed dependency after checking its documentation and types.
6. Write the smallest local implementation that completes the requirement.

## Constraints

- Treat current acceptance criteria as the only reason to add compatibility behavior, migrations, fallbacks, feature flags, configuration, extensibility, or indirection.
- Replace obsolete in-scope paths instead of preserving them behind wrappers or parallel implementations.
- Fix a bug at the shared root cause when the affected callers use the same faulty behavior.
- Keep concerns separate, but create a new module only when it gives the changed responsibility a real boundary.
- Begin with one working end-to-end slice and add each remaining capability on top of a state that already works.
- Validate at trust boundaries and real failure boundaries rather than defending internal states that the system guarantees cannot occur.
- Add a focused check only when existing checks do not prove the changed behavior.
- Record a deliberate simplification only when its known ceiling changes a future engineering decision.

## Stop Condition

Stop when the requested behavior works, obsolete in-scope code is removed, and current evidence proves the final state.
