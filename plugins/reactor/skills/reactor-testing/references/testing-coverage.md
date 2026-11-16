# Reactor Testing Selection

## Coverage map

| reactor-test surface | Common path | Seek more detail when... |
| --- | --- | --- |
| ordinary `StepVerifier` workflow | create, expect signals, verify | scenario options or complex choreography become the blocker |
| success, empty, and error assertions | `expectNext`, `expectComplete`, `expectError*` | custom predicates or richer assertion flows dominate the test |
| virtual time | `withVirtualTime(...)`, `expectNoEvent(...)`, `thenAwait(...)` | scheduler caveats or complex timing behavior become the blocker |
| request and cancellation | initial request, `thenRequest(...)`, `thenCancel()` | the test is mainly about request choreography |
| `TestPublisher` | manual emission for downstream testing | spec-violation or noncompliant publisher behavior becomes the blocker |
| `PublisherProbe` | verify subscription path without real data exchange | probe wrapping and advanced branch checks dominate the test |
| post-execution assertions | `verifyThenAssertThat()` for dropped/discarded checks | hook-heavy or context-specific assertions become the blocker |
| context test boundary | recognize context assertions as part of advanced verification | context expectations become the main job |
