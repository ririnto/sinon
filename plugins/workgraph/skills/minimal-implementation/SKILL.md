---
name: minimal-implementation
description: Select a complete implementation route. Use when reuse, dependency choice, module boundaries, or solution shape is materially undecided.
---

# Implementation Route Selection

## Outcome

Select a complete implementation route when the solution shape is materially undecided.

## Selection Sequence

1. Trace the affected flow and its current responsibility boundaries.
2. Inspect established product patterns that solve the same problem.
3. Inspect the repository's current patterns and available project code.
4. Read relevant dependency documentation and type definitions.
5. Select the first complete route from the ordered list below.
   - Delete obsolete behavior or remove the need for new behavior.
   - Reuse an existing project function, component, pattern, or data flow.
   - Use the standard library or a native platform feature.
   - Use an installed dependency.
   - Adopt a maintained library when it lowers total complexity.
   - Write a focused local implementation.

## Boundary Decision

- Create a module only when the changed responsibility needs a separate owner.
  Keep the implementation local when a new boundary adds no ownership value.

## Evidence

- Use existing checks when they prove the changed behavior.
  Add a focused check only when current evidence has a real gap.

## Stop Condition

Stop when one route satisfies every acceptance criterion and preserves clear ownership.
