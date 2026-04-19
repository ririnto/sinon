# Spring Web Flow flow execution testing

Open this reference when state transitions themselves are the behavior under test and the ordinary happy-path assertions in `SKILL.md` are not enough.

Use flow execution tests when backtracking, invalid input, exception paths, or subflow exits are part of the contract.

## Flow execution test shape

```java
setCurrentState("enterDetails");
resumeFlow(requestContext());
assertCurrentStateEquals("confirm");
```

Test the smallest meaningful transition sequence first, then add edge cases for backtracking, invalid input, or subflow exits.

## Gotchas

- Do not start with a full end-to-end journey when one state transition is the behavior under test.
- Do not skip negative-path assertions for invalid input or unexpected events.
- Do not treat subflow entry and exit mapping as implicitly correct without a dedicated assertion.
